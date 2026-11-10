import contextlib
import io
import os
import subprocess
import unittest
from unittest.mock import patch
from datetime import datetime, timezone, timedelta

import deploy


class DeployTest(unittest.TestCase):
    def test_grants_failure_blocks_publication(self):
        with patch.dict(os.environ, self.preflight_environment()), \
                patch('deploy.subprocess.run', return_value=subprocess.CompletedProcess([], 1)):
            with self.assertRaises(deploy.Failure):
                deploy.grants()

    def test_grants_keep_password_out_of_process_arguments(self):
        with patch.dict(os.environ, self.preflight_environment()), \
                patch('deploy.subprocess.run', return_value=subprocess.CompletedProcess([], 0)) as run:
            deploy.grants()
        self.assertNotIn('not-a-real-secret', ' '.join(run.call_args.args[0]))
        self.assertEqual('verify-full', run.call_args.kwargs['env']['PGSSLMODE'])
        self.assertIn(b'BEGIN;', run.call_args.kwargs['input'])

    def preflight_environment(self):
        return {'DEPLOY_SHA': 'a' * 40, 'API_BASE_URL': 'https://example.org',
                'FLYWAY_URL': 'jdbc:postgresql://db.neon.tech/db?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory',
                'FLYWAY_USER': 'migration', 'FLYWAY_PASSWORD': 'not-a-real-secret',
                'RENDER_SERVICE_ID': 'srv-test', 'RENDER_API_KEY': 'private',
                'RENDER_DEPLOY_HOOK_URL': 'https://api.render.com/deploy/srv-test?key=private',
                'SMOKE_LOGIN': 'auditor', 'SMOKE_PASSWORD': 'private',
                'BACKUP_VERIFIED_SHA': 'a' * 40, 'BACKUP_RESTORE_REFERENCE': 'c' * 64,
                'BACKUP_VERIFIED_AT': datetime.now(timezone.utc).isoformat(),
                'BACKUP_WRITES_PAUSED': 'true'}

    def test_backup_attestation_requires_paused_writes(self):
        env = self.preflight_environment()
        env['BACKUP_WRITES_PAUSED'] = 'false'
        with patch.dict(os.environ, env), self.assertRaises(deploy.Failure):
            deploy.verify_backup('a' * 40)

    def test_stale_backup_is_rejected(self):
        env = self.preflight_environment()
        env['BACKUP_VERIFIED_AT'] = (datetime.now(timezone.utc) - timedelta(hours=7)).isoformat()
        with patch.dict(os.environ, env), self.assertRaises(deploy.Failure):
            deploy.verify_backup('a' * 40)

    def test_current_backup_attestation_passes(self):
        with patch.dict(os.environ, self.preflight_environment()):
            deploy.verify_backup('a' * 40)

    def test_arbitrary_reference_rejected(self):
        env = self.preflight_environment()
        env['BACKUP_RESTORE_REFERENCE'] = 'https://example.org/private?token=secret'
        with patch.dict(os.environ, env), self.assertRaises(deploy.Failure):
            deploy.verify_backup('a' * 40)

    def test_backup_for_other_sha_blocks(self):
        env = self.preflight_environment()
        env['BACKUP_VERIFIED_SHA'] = 'b' * 40
        runs = {'workflow_runs': [{'head_sha': 'a' * 40, 'head_branch': 'main', 'conclusion': 'success'}]}
        with patch.dict(os.environ, env), patch('deploy.github', return_value=runs):
            with self.assertRaises(deploy.Failure):
                deploy.preflight()

    def test_missing_green_ci_blocks(self):
        with patch.dict(os.environ, self.preflight_environment()), \
                patch('deploy.github', return_value={'workflow_runs': []}):
            with self.assertRaises(deploy.Failure):
                deploy.preflight()

    def test_pooler_or_weak_tls_blocks_before_database_access(self):
        for url in ('jdbc:postgresql://db-pooler.neon.tech/db?sslmode=verify-full',
                    'jdbc:postgresql://db.neon.tech/db?sslmode=require'):
            env = self.preflight_environment()
            env['FLYWAY_URL'] = url
            with patch.dict(os.environ, env), patch('deploy.github') as github:
                with self.assertRaises(deploy.Failure):
                    deploy.preflight()
                github.assert_not_called()

    def test_migration_failure_stops_at_first_command(self):
        with patch('deploy.subprocess.run', return_value=subprocess.CompletedProcess([], 1)) as run:
            with self.assertRaises(deploy.Failure):
                deploy.migrate()
        self.assertEqual(1, run.call_count)
        self.assertEqual(subprocess.DEVNULL, run.call_args.kwargs['stderr'])

    def test_strict_validation_after_migration(self):
        with patch('deploy.subprocess.run', return_value=subprocess.CompletedProcess([], 0)) as run:
            deploy.migrate()
        self.assertEqual(3, run.call_count)
        self.assertEqual(['-ignoreMigrationPatterns=', 'validate'], run.call_args.args[0][-2:])
        self.assertIn('-cleanDisabled=true', run.call_args.args[0])

    def test_public_url_rejects_credentials_and_query(self):
        for url in ('http://example.org', 'https://u:p@example.org', 'https://example.org?key=x'):
            with self.subTest(url=url), patch.dict(os.environ, {'API_BASE_URL': url}):
                with self.assertRaises(deploy.Failure):
                    deploy.base_url()

    def test_redirects_do_not_forward_tokens(self):
        self.assertIsNone(deploy.NoRedirect().redirect_request(None, None, 302, '', {}, 'https://other'))

    def test_cancelled_is_failure(self):
        with patch.dict(os.environ, {'DEPLOYMENT_ID': '1', 'DEPLOY_RESULT': 'cancelled',
                                     'DEPLOY_SHA': 'a' * 40}), patch('deploy.status') as status:
            deploy.finish()
        status.assert_called_once_with('failure')

    def test_only_complete_job_can_succeed(self):
        with patch.dict(os.environ, {'DEPLOYMENT_ID': '1', 'DEPLOY_RESULT': 'success',
                                     'DEPLOY_SHA': 'a' * 40}), patch('deploy.status') as status:
            deploy.finish()
        status.assert_called_once_with('success')

    def test_render_old_sha_cannot_pass(self):
        env = {'RENDER_SERVICE_ID': 'srv-test', 'RENDER_API_KEY': 'private',
               'RENDER_DEPLOY_HOOK_URL': 'https://api.render.com/deploy/srv-test?key=private',
               'DEPLOY_SHA': 'a' * 40}
        responses = [(200, {'deploy': {'id': 'dep-test'}}),
                     (200, {'id': 'dep-test', 'status': 'live', 'commit': {'id': 'b' * 40}})]
        with patch.dict(os.environ, env), patch('deploy.request', side_effect=responses):
            with self.assertRaises(deploy.Failure):
                deploy.render()

    def test_render_queue_tracks_new_deploy(self):
        env = {'RENDER_SERVICE_ID': 'srv-test', 'RENDER_API_KEY': 'private',
               'RENDER_DEPLOY_HOOK_URL': 'https://api.render.com/deploy/srv-test?key=private',
               'DEPLOY_SHA': 'a' * 40}
        responses = [(202, None), (200, [{'deploy': {
            'id': 'dep-new', 'status': 'live', 'commit': {'id': 'a' * 40}}}])]
        with patch.dict(os.environ, env), patch('deploy.request', side_effect=responses) as request:
            with contextlib.redirect_stdout(io.StringIO()):
                deploy.render()
        self.assertIn('createdAfter=', request.call_args.args[0])
        self.assertIn('ref=' + 'a' * 40, request.call_args_list[0].args[0])

    def test_anonymous_inventory_fails_smoke(self):
        responses = [(200, {'service': 'friovax-api', 'status': 'UP'}),
                     (200, {'status': 'UP', 'checks': [
                         {'name': 'Database connections health check', 'status': 'UP'}]}),
                     (200, [])]
        with patch.dict(os.environ, {'API_BASE_URL': 'https://example.org'}), \
                patch('deploy.request', side_effect=responses):
            with self.assertRaises(deploy.Failure):
                deploy.smoke()


if __name__ == '__main__':
    unittest.main()

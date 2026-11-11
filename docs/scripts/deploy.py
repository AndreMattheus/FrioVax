"""Publicação controlada. Nunca imprime corpos de erro, URLs secretas ou tokens."""

import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone

FLYWAY_IMAGE = ('redgate/flyway:12.0.0@sha256:'
                '4f10cf260df7a1970934eb273c6d581aff5f8c70df8449c31efe4137027b8047')


class Failure(Exception):
    pass


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def required(name):
    value = os.environ.get(name, '')
    if not value:
        raise Failure(f'Configuração ausente: {name}')
    return value


def request(url, method='GET', body=None, token=None):
    headers = {'Accept': 'application/json', 'User-Agent': 'FrioVax-deploy'}
    if token:
        headers['Authorization'] = f'Bearer {token}'
    data = None if body is None else json.dumps(body).encode()
    if data is not None:
        headers['Content-Type'] = 'application/json'
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.build_opener(NoRedirect).open(req, timeout=30) as response:
            raw = response.read()
            return response.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as error:
        return error.code, None
    except (OSError, ValueError):
        raise Failure('Falha de conexão ou resposta inválida; detalhes suprimidos.') from None


def github(path, method='GET', body=None):
    status, result = request(
        f'https://api.github.com/repos/{required("GITHUB_REPOSITORY")}/{path}',
        method, body, required('GH_TOKEN'))
    if status not in (200, 201):
        raise Failure(f'GitHub recusou a operação (HTTP {status}).')
    return result


def sha():
    value = required('DEPLOY_SHA')
    if not re.fullmatch('[0-9a-f]{40}', value):
        raise Failure('SHA completo inválido.')
    return value


def base_url():
    value = required('API_BASE_URL').rstrip('/')
    parsed = urllib.parse.urlsplit(value)
    if (parsed.scheme != 'https' or not parsed.hostname or parsed.username or
            parsed.password or parsed.query or parsed.fragment or parsed.path):
        raise Failure('API_BASE_URL deve ser uma origem HTTPS sem credenciais.')
    return value


def status(state):
    body = {
        'state': state, 'environment': 'producao',
        'log_url': f'https://github.com/{required("GITHUB_REPOSITORY")}/actions/runs/{required("GITHUB_RUN_ID")}',
        'description': 'Saúde e smoke aprovados' if state == 'success' else 'Publicação controlada pelo CI',
        'auto_inactive': state == 'success',
    }
    try:
        body['environment_url'] = base_url()
    except (Failure, ValueError):
        if state != 'failure':
            raise
    github(f'deployments/{required("DEPLOYMENT_ID")}/statuses', 'POST', body)


def begin():
    result = github('deployments', 'POST', {
        'ref': sha(), 'environment': 'producao', 'auto_merge': False,
        'required_contexts': [], 'production_environment': True,
        'description': 'API Java / Render / Neon',
    })
    os.environ['DEPLOYMENT_ID'] = str(result['id'])
    with open(required('GITHUB_ENV'), 'a', encoding='utf-8') as env:
        env.write(f'DEPLOYMENT_ID={result["id"]}\n')
    status('in_progress')


def preflight():
    base_url()
    target = sha()
    for name in ('FLYWAY_URL', 'FLYWAY_USER', 'FLYWAY_PASSWORD', 'RENDER_API_KEY',
                 'RENDER_DEPLOY_HOOK_URL', 'RENDER_SERVICE_ID', 'SMOKE_LOGIN', 'SMOKE_PASSWORD'):
        required(name)
    jdbc = required('FLYWAY_URL')
    parsed = urllib.parse.urlsplit(jdbc.removeprefix('jdbc:'))
    params = urllib.parse.parse_qs(parsed.query)
    if (not jdbc.startswith('jdbc:postgresql://') or not parsed.hostname or
            '-pooler' in parsed.hostname or parsed.username or parsed.password or
            params.get('sslmode') != ['verify-full'] or
            params.get('sslfactory') != ['org.postgresql.ssl.DefaultJavaSSLFactory']):
        raise Failure('FLYWAY_URL exige JDBC direto e TLS verify-full, sem credenciais embutidas.')
    hook = urllib.parse.urlsplit(required('RENDER_DEPLOY_HOOK_URL'))
    service = required('RENDER_SERVICE_ID')
    if (not re.fullmatch(r'srv-[a-z0-9]+', service) or hook.scheme != 'https' or
            hook.netloc != 'api.render.com' or hook.path != f'/deploy/{service}'):
        raise Failure('Hook e serviço Render incompatíveis.')
    runs = github(f'actions/workflows/ci.yml/runs?head_sha={target}&event=push&per_page=100')
    if not any(r['head_sha'] == target and r['head_branch'] == 'main' and
               r['conclusion'] == 'success' for r in runs['workflow_runs']):
        raise Failure('O mesmo SHA precisa de CI completo verde em main.')
    verify_backup(target)
    print('Pre-requisitos conferidos; ensaio manual declarado pelo operador.')


def verify_backup(target):
    if required('BACKUP_VERIFIED_SHA') != target:
        raise Failure('Backup precisa de confirmacao para este SHA.')
    if not re.fullmatch(r'[0-9a-fA-F]{64}', required('BACKUP_RESTORE_REFERENCE')):
        raise Failure('Informe somente o SHA256 do backup restaurado.')
    try:
        verified = datetime.fromisoformat(required('BACKUP_VERIFIED_AT').replace('Z', '+00:00'))
        if verified.tzinfo is None:
            raise ValueError('timezone ausente')
        age = (datetime.now(timezone.utc) - verified).total_seconds()
    except ValueError:
        raise Failure('Horario do ensaio deve estar em ISO 8601 com fuso.') from None
    if age < -300 or age > 21600:
        raise Failure('Ensaio deve ser recente (ate 6 horas), sem horario futuro.')
    if required('BACKUP_WRITES_PAUSED').lower() != 'true':
        raise Failure('Suspender escritas desde o backup ate concluir a publicacao.')


def migrate():
    location = str(Path('api/src/main/resources/db/migration').resolve())
    common = ['docker', 'run', '--rm', '-e', 'FLYWAY_URL', '-e', 'FLYWAY_USER',
              '-e', 'FLYWAY_PASSWORD', '-v', f'{location}:/flyway/sql:ro', FLYWAY_IMAGE,
              '-locations=filesystem:/flyway/sql', '-cleanDisabled=true', '-connectRetries=5']
    for args in (['-ignoreMigrationPatterns=*:pending', 'validate'], ['migrate'],
                 ['-ignoreMigrationPatterns=', 'validate']):
        result = subprocess.run(common + args, stdout=subprocess.DEVNULL,
                                stderr=subprocess.DEVNULL, timeout=600, check=False)
        if result.returncode:
            raise Failure('Migração/validação falhou. Hook não será chamado; investigar em canal privado.')
    print('Flyway validate / migrate / validate concluído.')


def render():
    service = required('RENDER_SERVICE_ID')
    started = datetime.now(timezone.utc).isoformat()
    hook = urllib.parse.urlsplit(required('RENDER_DEPLOY_HOOK_URL'))
    query = urllib.parse.parse_qsl(hook.query)
    query = [(k, v) for k, v in query if k != 'ref'] + [('ref', sha())]
    url = urllib.parse.urlunsplit(hook._replace(query=urllib.parse.urlencode(query)))
    code, result = request(url, 'POST')
    if code not in (200, 202):
        raise Failure(f'Hook recusado (HTTP {code}).')
    deploy_id = (result or {}).get('deploy', {}).get('id') or (result or {}).get('id')
    deadline = time.monotonic() + 1200
    while time.monotonic() < deadline:
        url = f'https://api.render.com/v1/services/{service}/deploys'
        if deploy_id:
            url += f'/{deploy_id}'
        else:
            url += '?' + urllib.parse.urlencode({'createdAfter': started, 'limit': 100})
        token = required('RENDER_API_KEY')
        try:
            code, result = request(url, token=token)
        except Failure:
            time.sleep(min(10, max(0, deadline - time.monotonic())))
            continue
        if code == 200:
            candidates = [result] if deploy_id else [r['deploy'] for r in result]
            matches = [d for d in candidates if d.get('commit', {}).get('id') == sha()]
            if len(matches) > 1:
                raise Failure('Múltiplos deploys do mesmo SHA; conferir concorrência manual.')
            if matches:
                deployment = matches[0]
                deploy_id = deployment['id']
                if deployment['status'] == 'live':
                    print(f'Render live: SHA {sha()}, deploy {deploy_id}')
                    return
                if deployment['status'] in ('build_failed', 'update_failed', 'pre_deploy_failed',
                                             'canceled', 'deactivated'):
                    raise Failure('Render encerrou o deploy sem sucesso.')
            elif deploy_id:
                raise Failure('Render retornou SHA diferente do solicitado.')
        elif code not in (429, 500, 502, 503, 504):
            raise Failure(f'Consulta Render falhou (HTTP {code}).')
        time.sleep(min(10, max(0, deadline - time.monotonic())))
    raise Failure('Timeout aguardando Render live no SHA solicitado.')


def grants():
    parsed = urllib.parse.urlsplit(required('FLYWAY_URL').removeprefix('jdbc:'))
    params = urllib.parse.parse_qs(parsed.query)
    if (parsed.scheme != 'postgresql' or not parsed.hostname or
            '-pooler' in parsed.hostname or params.get('sslmode') != ['verify-full']):
        raise Failure('Grants exigem conexao direta com TLS validado.')
    pg_env = os.environ.copy()
    pg_env.update({
        'PGHOST': parsed.hostname, 'PGPORT': str(parsed.port or 5432),
        'PGDATABASE': urllib.parse.unquote(parsed.path.lstrip('/')),
        'PGUSER': required('FLYWAY_USER'), 'PGPASSWORD': required('FLYWAY_PASSWORD'),
        'PGSSLMODE': 'verify-full', 'PGSSLROOTCERT': '/etc/ssl/certs/ca-certificates.crt',
        'PGCONNECT_TIMEOUT': '30',
    })
    args = ['docker', 'run', '--rm', '-i']
    for name in ('PGHOST', 'PGPORT', 'PGDATABASE', 'PGUSER', 'PGPASSWORD',
                 'PGSSLMODE', 'PGSSLROOTCERT', 'PGCONNECT_TIMEOUT'):
        args += ['-e', name]
    args += ['postgres:18.6-alpine@sha256:123b17998ccaf47e53b502e8c6a1e79afd8f579c6f3eec83c563da026f957395',
             'psql', '-X', '--no-password', '--set=ON_ERROR_STOP=1', '--quiet']
    result = subprocess.run(args, input=Path('docs/scripts/runtime-grants.sql').read_bytes(),
                            env=pg_env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                            timeout=120, check=False)
    if result.returncode:
        raise Failure('Permissoes de runtime invalidas ou nao aplicadas; deploy interrompido.')
    print('Permissoes do runtime aplicadas; estrutura e historico Flyway protegidos.')


def smoke():
    base = base_url()
    deadline = time.monotonic() + 300
    while True:
        try:
            code, process = request(base + '/api/status')
            ready_code, ready = request(base + '/q/health/ready')
            if (code == ready_code == 200 and process.get('service') == 'friovax-api' and
                    process.get('status') == ready.get('status') == 'UP' and
                    any(c.get('name') == 'Database connections health check' and
                        c.get('status') == 'UP' for c in ready.get('checks', []))):
                break
        except Failure:
            pass
        if time.monotonic() >= deadline:
            raise Failure('Timeout de saúde/prontidão, incluindo banco.')
        time.sleep(10)
    for method, body in [('GET', None), ('POST', {})]:
        code, _ = request(base + '/api/camaras', method, body)
        if code != 401:
            raise Failure('Inventário permite acesso anônimo ou retorna resposta inesperada.')
    code, session = request(base + '/api/auth/login', 'POST', {
        'login': required('SMOKE_LOGIN'), 'senha': required('SMOKE_PASSWORD')})
    if code != 200 or not session or not session.get('accessToken'):
        raise Failure('Login do smoke falhou.')
    try:
        code, inventory = request(base + '/api/camaras', token=session['accessToken'])
        if code != 200 or not isinstance(inventory, (dict, list)):
            raise Failure('Consulta autenticada ao inventário falhou.')
    finally:
        if session.get('refreshToken'):
            request(base + '/api/auth/logout', 'POST', {'refreshToken': session['refreshToken']})
    print('Saúde, banco, bloqueio anônimo e consulta autenticada aprovados.')


def finish():
    if os.environ.get('DEPLOYMENT_ID'):
        state = 'success' if os.environ.get('DEPLOY_RESULT') == 'success' else 'failure'
        status(state)
        print(f'Deployment {required("DEPLOYMENT_ID")}: {state}; SHA {sha()}')


if __name__ == '__main__':
    try:
        {'begin': begin, 'preflight': preflight, 'migrate': migrate,
         'grants': grants, 'render': render, 'smoke': smoke, 'finish': finish}[sys.argv[1]]()
    except Failure as error:
        print(f'ERRO: {error}', file=sys.stderr)
        sys.exit(1)
    except Exception:
        print('ERRO: operação interrompida; detalhes sensíveis suprimidos.', file=sys.stderr)
        sys.exit(1)

"""Testes do contrato de coleta, sem API ou dados fictícios nas evidências reais."""

from contextlib import redirect_stdout
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch


def carregar(nome):
    spec = importlib.util.spec_from_file_location(nome, Path(__file__).with_name(nome + ".py"))
    modulo = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(modulo)
    return modulo


coleta = carregar("coletar-medicao-sprint-2")
validacao = carregar("validar-medicao-sprint-2")


class TestMedicao(unittest.TestCase):
    def test_timestamps_exigem_fuso_e_ordem(self):
        for inicio, fim in [("2026-11-03", ""), ("2026-11-03T14:00:00", ""),
                            ("2026-11-03T14:00:00-03:00", "2026-11-03T13:59:00-03:00")]:
            with self.subTest(inicio=inicio), self.assertRaises(ValueError):
                validacao.periodo(inicio, fim)
        validacao.periodo("2026-11-03T14:00:00-03:00", "2026-11-03T17:50:00Z")
        validacao.periodo("2026-11-03T14:00:00-03:00", "")

    def test_fontes_rejeitam_credenciais_e_urls_assinadas(self):
        for fonte in ["", "https://token@github.com/x/y", "https://github.com/x?token=abc",
                      "https://provedor.example/log?signature=abc", "../../etc/passwd"]:
            with self.subTest(fonte=fonte), self.assertRaises(ValueError):
                validacao.fonte(fonte)
        validacao.fonte("https://github.com/AndreMattheus/FrioVax/issues/66")

    def test_fontes_locais_resolvem_ponteiro(self):
        with tempfile.TemporaryDirectory() as pasta, patch.object(validacao, "ROOT", Path(pasta).resolve()):
            Path(pasta, "fonte.json").write_text('[{"inicio":"observado"}]')
            validacao.fonte("fonte.json#/0/inicio")
            with self.assertRaises(KeyError):
                validacao.fonte("fonte.json#/0/inexistente")

    def test_paginacao_preserva_paginas_e_descarta_campos_livres(self):
        resposta = subprocess.CompletedProcess([], 0, json.dumps([
            [{"id": 1, "body": "SEGREDO"}], [{"id": 2, "body": "SEGREDO"}]]), "")
        with tempfile.TemporaryDirectory() as pasta, patch.object(coleta.subprocess, "run", return_value=resposta) as cli:
            coletor = coleta.Coletor(Path(pasta))
            dados = coletor.rest("issues", "fonte.json", lambda obj: coleta.selecionar(obj, "id"))
            self.assertEqual([{"id": 1}, {"id": 2}], dados)
            self.assertEqual(2, coletor.consultas[0]["paginas"])
            self.assertNotIn("SEGREDO", Path(pasta, "fonte.json").read_text())
            self.assertIn("--paginate", cli.call_args.args[0])
            self.assertIn("--slurp", cli.call_args.args[0])

    def test_issue_endpoint_nao_duplica_pull_request(self):
        resposta = subprocess.CompletedProcess([], 0, '[[{"id":1},{"id":2,"pull_request":{}}]]', "")
        with tempfile.TemporaryDirectory() as pasta, patch.object(coleta.subprocess, "run", return_value=resposta):
            coletor = coleta.Coletor(Path(pasta))
            dados = coletor.rest("issues", "issues.json", lambda obj: None if "pull_request" in obj else obj)
            self.assertEqual([{"id": 1}], dados)
            self.assertEqual(1, coletor.consultas[0]["registros"])

    def test_falha_api_nao_publica_arquivo_completo(self):
        resposta = subprocess.CompletedProcess([], 1, "", "erro privado")
        with tempfile.TemporaryDirectory() as pasta, patch.object(coleta.subprocess, "run", return_value=resposta):
            coletor = coleta.Coletor(Path(pasta))
            with self.assertRaises(RuntimeError) as erro:
                coletor.rest("issues", "issues.json", lambda obj: obj)
            self.assertEqual([], list(Path(pasta).iterdir()))
            self.assertEqual([], coletor.consultas)
            self.assertNotIn("erro privado", str(erro.exception))

    def test_graphql_truncado_e_rejeitado(self):
        pagina = [{"data": {"nodes": [], "pageInfo": {"hasNextPage": True, "endCursor": "cursor"}}}]
        resposta = subprocess.CompletedProcess([], 0, json.dumps(pagina), "")
        with tempfile.TemporaryDirectory() as pasta, patch.object(coleta.subprocess, "run", return_value=resposta):
            with self.assertRaises(ValueError):
                coleta.Coletor(Path(pasta)).consultar(["graphql"], "x.json", lambda obj: obj, caminho=["data"])

    def test_hash_detecta_exportacao_alterada(self):
        with tempfile.TemporaryDirectory() as pasta:
            root = Path(pasta).resolve()
            with patch.object(validacao, "ROOT", root), patch.object(validacao, "PASTA", root):
                for nome, cabecalho in validacao.CABECALHOS.items():
                    (root / f"{nome}.csv").write_text(cabecalho + "\n")
                corte = root / "exportacoes/corte"
                corte.mkdir(parents=True)
                (corte / "dados.json").write_text("[]\n")
                manifesto = {"resultado": "completo", "inicio": "2026-11-03T17:00:00Z",
                             "fim": "2026-11-03T17:01:00Z", "consultas": [{
                                 "arquivo": "dados.json", "sha256": "hash_divergente", "registros": 0,
                                 "paginas": 1, "inicio": "2026-11-03T17:00:00Z", "fim": "2026-11-03T17:01:00Z"}]}
                (corte / "manifesto.json").write_text(json.dumps(manifesto))
                with self.assertRaisesRegex(ValueError, "Hash divergente"), redirect_stdout(io.StringIO()):
                    validacao.validar()

    def test_padrao_de_credencial_impede_publicar_evidencia(self):
        with tempfile.TemporaryDirectory() as pasta:
            root = Path(pasta).resolve()
            with patch.object(validacao, "ROOT", root), patch.object(validacao, "PASTA", root):
                for nome, cabecalho in validacao.CABECALHOS.items():
                    (root / f"{nome}.csv").write_text(cabecalho + "\n")
                # Token deliberadamente sintético, somente no diretório temporário do teste.
                (root / "exportacao.json").write_text(json.dumps({"valor": "github_pat_" + "x" * 30}))
                with self.assertRaisesRegex(ValueError, "Possível credencial"):
                    validacao.validar()


if __name__ == "__main__":
    unittest.main()

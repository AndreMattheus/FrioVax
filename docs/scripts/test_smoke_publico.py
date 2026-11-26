"""Contrato do smoke público: sem API real e sem credenciais em evidências."""
from contextlib import redirect_stdout
from email.message import Message
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import unittest
import tempfile
from unittest.mock import Mock
import urllib.error

spec = importlib.util.spec_from_file_location("smoke", Path(__file__).with_name("smoke-publico.py"))
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


class Resposta(io.BytesIO):
    def __init__(self, body, code=200):
        super().__init__(json.dumps(body).encode())
        self.code = code
        self.headers = Message()
        self.headers["Content-Type"] = "application/json"


class ClienteFalso:
    def __init__(self, fantasma=False, tempo=.1, capacidade=120):
        self.base = "https://friovax.onrender.com"
        self.etapas = []
        self.codigo = None
        self.fantasma = fantasma
        self.tempo = tempo
        self.capacidade = capacidade

    def chamar(self, etapa, caminho, esperado, metodo="GET", corpo=None, token=None, **kwargs):
        self.etapas.append(etapa)
        o = {}
        if etapa in ("login", "renovar_apos_redeploy"):
            o = {"accessToken": "JWT-SEGREDO", "refreshToken": "REFRESH-SEGREDO"}
        elif etapa == "identidade":
            o = {"perfil": "ADMINISTRADOR"}
        elif etapa == "criar_camara":
            self.codigo = corpo["codigo"]
            o = {"id": 99, "codigo": self.codigo}
        elif etapa.endswith("_camara"):
            o = {"id": 99, "codigo": self.codigo, "capacidade": self.capacidade, "ativo": True}
        elif etapa.endswith(("_leituras", "_ocorrencias")):
            n = 1 if self.fantasma and etapa == "depois_falha_leituras" else 0
            o = {"items": [{}] if n else [], "totalElements": n}
        elif etapa == "leitura_sem_go":
            o = {"type": "/problemas/analise-indisponivel"}
        elif etapa == "consultar_fixture_inativa":
            o = {"ativo": False}
        return o, b"", self.tempo


class SmokePublicoTest(unittest.TestCase):
    def test_origem_rejeita_credenciais_query_e_http(self):
        for url in ("http://example.com", "https://u:p@example.com", "https://example.com?token=secreto", "https://example.com/log"):
            with self.subTest(url=url), self.assertRaises(m.Falha):
                m.origem(url)
        self.assertEqual("https://friovax.onrender.com", m.origem("https://friovax.onrender.com/"))
        self.assertEqual("https://friovax.onrender.com", m.origem("https://FRIOVAX.onrender.com:443/"))

    def test_put_200_sem_capacidade_persistida_reprova_antes_da_leitura(self):
        for capacidade in (100, None):
            with self.subTest(capacidade=capacidade):
                c = ClienteFalso(capacidade=capacidade)
                r = {"publicacao_antes": {"sha": "a" * 40, "deployment": 1}}
                gh = Mock()
                with self.assertRaisesRegex(m.Falha, "Capacidade 120"):
                    m.autenticado(c, gh, r, "teste", "SENHA-SEGREDA", True, 2)
                self.assertIn("atualizar_camara", c.etapas)
                self.assertNotIn("leitura_sem_go", c.etapas)
                gh.redeploy.assert_not_called()
                self.assertEqual(capacidade, r["fixture"]["antes"]["camara"]["capacidade"])

    def test_url_divergente_bloqueia_antes_de_enviar_credenciais(self):
        r = {"publicacao_antes": {"sha": "a" * 40, "deployment": 1}}
        c = ClienteFalso()
        c.base = "https://outro.example"
        gh = m.GitHub(r)
        gh.api = Mock(return_value={"value": "https://friovax.onrender.com"})
        gh.executar = Mock()
        with self.assertRaises(m.Falha):
            m.autenticado(c, gh, r, "teste", "SENHA-SEGREDA", True, 2)
        self.assertEqual([], c.etapas)
        gh.executar.assert_not_called()

    def test_destino_exige_url_configurada_e_url_do_deployment(self):
        gh = m.GitHub({})
        gh.api = Mock(return_value={"value": "https://friovax.onrender.com/"})
        publicacao = {"ambiente": "producao", "base_url": "https://friovax.onrender.com"}
        gh.validar_destino("https://FRIOVAX.onrender.com:443", publicacao)
        for ambiente, url in [("homologacao", publicacao["base_url"]),
                              ("producao", "https://outro.example"), ("producao", None)]:
            with self.subTest(ambiente=ambiente, url=url), self.assertRaises(m.Falha):
                gh.validar_destino(publicacao["base_url"], {"ambiente": ambiente, "base_url": url})
        gh.api.return_value = {"value": ""}
        with self.assertRaises(m.Falha):
            gh.validar_destino(publicacao["base_url"])

    def test_redeploy_revalida_destino_antes_do_dispatch_e_apos_publicacao(self):
        gh = m.GitHub({})
        gh.api = Mock(return_value={"value": "https://outro.example"})
        gh.executar = Mock()
        with self.assertRaises(m.Falha):
            gh.redeploy("a" * 40, "https://friovax.onrender.com")
        gh.executar.assert_not_called()
        cfg = {"value": "https://friovax.onrender.com"}
        gh.api.side_effect = [cfg, {"sha": "a" * 40}, [{"id": 1}],
                             [{"id": 2, "head_sha": "a" * 40, "head_branch": "main",
                               "status": "completed", "conclusion": "success"}], cfg]
        gh.publicacao = Mock(return_value={"ambiente": "producao", "base_url": "https://outro.example"})
        with self.assertRaises(m.Falha):
            gh.redeploy("a" * 40, "https://friovax.onrender.com")
        gh.executar.assert_called_once_with(["workflow", "run", "deploy.yml", "--repo", m.REPO, "--ref", "main"])

    def test_redirecionamento_com_bearer_nao_e_seguido(self):
        self.assertIsNone(m.SemRedirect().redirect_request(None, None, 302, "", {}, "https://outro.example"))

    def test_resposta_de_sessao_nao_vai_para_relatorio(self):
        r = {"chamadas": []}
        c = m.Cliente("https://example.com", r)
        c.abrir = Mock(return_value=Resposta({"accessToken": "JWT-SEGREDO", "refreshToken": "REFRESH-SEGREDO"}))
        saida = io.StringIO()
        with redirect_stdout(saida):
            o, _, _ = c.chamar("login", "/api/auth/login", 200, "POST", {"senha": "SENHA-SEGREDA"})
        self.assertEqual("JWT-SEGREDO", o["accessToken"])
        publicado = json.dumps(r) + saida.getvalue()
        for segredo in ("JWT-SEGREDO", "REFRESH-SEGREDO", "SENHA-SEGREDA"):
            self.assertNotIn(segredo, publicado)

    def test_erro_http_nao_publica_corpo_sensivel(self):
        r = {"chamadas": []}
        c = m.Cliente("https://example.com", r)
        cab = Message()
        cab["Content-Type"] = "application/problem+json"
        c.abrir = Mock(side_effect=urllib.error.HTTPError("https://example.com", 403, "", cab,
                       io.BytesIO(b'{"detail":"SENHA-SEGREDA"}')))
        saida = io.StringIO()
        with redirect_stdout(saida), self.assertRaises(m.Falha) as e:
            c.chamar("negado", "/api/camaras", 200)
        self.assertNotIn("SENHA-SEGREDA", json.dumps(r) + saida.getvalue() + str(e.exception))
        self.assertFalse(r["chamadas"][0]["aprovado"])

    def test_degradacao_com_gravacao_fantasma_reprova(self):
        r = {"publicacao_antes": {"sha": "a" * 40, "deployment": 1}}
        c = ClienteFalso(fantasma=True)
        with self.assertRaises(m.Falha):
            m.autenticado(c, None, r, "teste", "SENHA-SEGREDA", False, 2)
        self.assertNotIn("persistencia", r)
        self.assertEqual(99, r["fixture_pendente"]["id"])

    def test_503_fora_do_prazo_reprova(self):
        r = {"publicacao_antes": {"sha": "a" * 40, "deployment": 1}}
        c = ClienteFalso(tempo=2.01)
        with self.assertRaises(m.Falha):
            m.autenticado(c, None, r, "teste", "SENHA-SEGREDA", False, 2)
        self.assertIn("depois_falha_ocorrencias", c.etapas)
        self.assertEqual(r["fixture"]["antes"], r["fixture"]["depois_falha"])
        self.assertFalse(r["fixture"]["prazo_publico_atendido"])
        self.assertNotIn("publicacao_depois", r)

    def test_orcamento_publico_explicito_preserva_tempo_real(self):
        r = {"publicacao_antes": {"sha": "a" * 40, "deployment": 1}}
        m.autenticado(ClienteFalso(tempo=2.746), None, r, "teste", "SENHA-SEGREDA", False, 10)
        self.assertEqual(2.746, r["fixture"]["tempo_503_s"])
        self.assertEqual(10, r["fixture"]["limite_503_s"])
        self.assertTrue(r["fixture"]["prazo_publico_atendido"])

    def test_limpeza_exige_proveniencia_e_identidade_da_fixture(self):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d)
            b = json.dumps({"base_url": "https://example.com", "fixture_pendente": {"id": 2}}).encode()
            (p / "resultado.json").write_bytes(b)
            (p / "resultado.sha256").write_text(hashlib.sha256(b).hexdigest())
            c = Mock(base="https://example.com")
            def chamar(etapa, *args, **kwargs):
                if etapa.endswith(("_leituras", "_ocorrencias")):
                    return {"items": [], "totalElements": 0}, b"", 0
                if etapa == "limpeza_anterior_camara":
                    return {"codigo": "E89-TESTE", "nome": "Outra camara"}, b"", 0
                return {"ativo": False}, b"", 0
            c.chamar.side_effect = chamar
            with self.assertRaises(m.Falha):
                m.limpar_fixture_anterior(c, "JWT-SEGREDO", p, {})
            self.assertFalse(any(call.args[0] == "inativar_fixture_anterior" for call in c.chamar.call_args_list))
            c.chamar.reset_mock()
            def propria(etapa, *args, **kwargs):
                if etapa == "limpeza_anterior_camara":
                    return {"codigo": "E89-TESTE", "nome": "Fixture ficticia smoke 89"}, b"", 0
                return chamar(etapa, *args, **kwargs)
            c.chamar.side_effect = propria
            r = {}
            m.limpar_fixture_anterior(c, "JWT-SEGREDO", p, r)
            self.assertEqual(2, r["limpeza_anterior"]["id"])
            self.assertTrue(r["limpeza_anterior"]["inativada"])
            (p / "resultado.sha256").write_text("hash-divergente")
            with self.assertRaises(m.Falha):
                m.limpar_fixture_anterior(c, "JWT-SEGREDO", p, {})

    def test_primeira_execucao_autenticada_permanece_incompleta(self):
        p = m.ROOT / "docs/evidencias/sprint-2/smoke-publico/20261126T132235626835Z"
        b = (p / "resultado.json").read_bytes()
        self.assertEqual(hashlib.sha256(b).hexdigest(), (p / "resultado.sha256").read_text().strip())
        r = json.loads(b)
        self.assertEqual("incompleto", r["resultado"])
        self.assertEqual(2, r["fixture_pendente"]["id"])
        leitura = next(x for x in r["chamadas"] if x["etapa"] == "leitura_sem_go")
        self.assertEqual(503, leitura["http"])
        self.assertGreater(leitura["segundos"], 2)
        self.assertNotIn("publicacao_depois", r)

    def test_redeploy_confere_refresh_persistido_estado_e_limpeza(self):
        r = {"publicacao_antes": {"sha": "a" * 40, "deployment": 1}}
        c = ClienteFalso()
        gh = Mock()
        gh.redeploy.return_value = {"sha": "a" * 40, "deployment": 2}
        m.autenticado(c, gh, r, "teste", "SENHA-SEGREDA", True, 2)
        gh.validar_destino.assert_called_once_with(c.base, r["publicacao_antes"])
        gh.redeploy.assert_called_once_with("a" * 40, c.base)
        self.assertEqual(r["fixture"]["antes"], r["fixture"]["depois_falha"])
        self.assertEqual(r["fixture"]["antes"], r["fixture"]["depois_redeploy"])
        self.assertTrue(r["fixture"]["inativada"])
        for etapa in ("renovar_apos_redeploy", "refresh_consumido", "refresh_revogado", "jwt_apos_logout"):
            self.assertIn(etapa, c.etapas)
        self.assertNotIn("encerrar_sessao", c.etapas)
        self.assertNotIn("SEGREDO", json.dumps(r))
        self.assertNotIn("SENHA-SEGREDA", json.dumps(r))

    def test_sem_redeploy_nao_declara_persistencia_aprovada(self):
        r = {"publicacao_antes": {"sha": "a" * 40, "deployment": 1}}
        m.autenticado(ClienteFalso(), None, r, "teste", "SENHA-SEGREDA", False, 2)
        self.assertNotIn("publicacao_depois", r)
        self.assertNotIn("depois_redeploy", r["fixture"])
        self.assertTrue(r["persistencia"].startswith("não executada"))

    def test_compactacao_preserva_sequencia_e_nao_modifica_medicoes(self):
        comandos = [["gh", "api", "runs"], ["gh", "api", "jobs"], ["gh", "api", "runs"]]
        r = {"schema": 1, "comandos_github": comandos, "fixture": {"tempo_503_s": 1.097}}
        m.compactar_comandos(r)
        self.assertEqual(comandos, [r["comandos_github"][i] for i in r["sequencia_comandos_github"]])
        self.assertEqual(2, len(r["comandos_github"]))
        self.assertEqual(1.097, r["fixture"]["tempo_503_s"])
        b = json.dumps(r)
        m.compactar_comandos(r)
        self.assertEqual(b, json.dumps(r))

    def test_evidencia_real_aprovada_confere_degradacao_persistencia_e_sessao(self):
        pasta = m.ROOT / "docs/evidencias/sprint-2/smoke-publico/20261126T133357359660Z"
        bruto = (pasta / "resultado.json").read_bytes()
        self.assertEqual(hashlib.sha256(bruto).hexdigest(), (pasta / "resultado.sha256").read_text().strip())
        r = json.loads(bruto)
        self.assertEqual(13, len(r["comandos_github"]))
        self.assertEqual(29, len(r["sequencia_comandos_github"]))
        self.assertEqual("aprovado", r["resultado"])
        self.assertTrue(all(c["aprovado"] for c in r["chamadas"]))
        self.assertEqual(r["publicacao_antes"]["sha"], r["publicacao_depois"]["sha"])
        self.assertNotEqual(r["publicacao_antes"]["deployment"], r["publicacao_depois"]["deployment"])
        self.assertEqual(8086685428, r["publicacao_depois"]["deployment"])
        f = r["fixture"]
        self.assertEqual(3, f["id"])
        self.assertEqual(120, f["antes"]["camara"]["capacidade"])
        self.assertEqual(f["antes"], f["depois_falha"])
        self.assertEqual(f["antes"], f["depois_redeploy"])
        self.assertEqual(0, f["depois_falha"]["leituras"])
        self.assertEqual(0, f["depois_falha"]["ocorrencias"])
        self.assertTrue(f["inativada"])
        self.assertTrue(r["limpeza_anterior"]["inativada"])
        self.assertLess(f["tempo_503_s"], f["limite_503_s"])
        etapas = {c["etapa"]: c for c in r["chamadas"]}
        for nome, http in [("renovar_apos_redeploy", 200), ("refresh_consumido", 401),
                           ("refresh_revogado", 401), ("jwt_apos_logout", 200)]:
            self.assertEqual(http, etapas[nome]["http"])
        def conferir_chaves(obj):
            if isinstance(obj, dict):
                self.assertFalse({"accessToken", "refreshToken", "senha", "password", "Authorization"} & obj.keys())
                for valor in obj.values():
                    conferir_chaves(valor)
            elif isinstance(obj, list):
                for valor in obj:
                    conferir_chaves(valor)
        conferir_chaves(r)

    def test_documentacao_apos_redeploy_corresponde_ao_evento_aprovado(self):
        pasta = m.ROOT / "docs/evidencias/sprint-2/smoke-publico"
        antes = json.loads((pasta / "20261126T133357359660Z/resultado.json").read_bytes())
        p = pasta / "20261126T134018149599Z"
        bruto = (p / "resultado.json").read_bytes()
        self.assertEqual(hashlib.sha256(bruto).hexdigest(), (p / "resultado.sha256").read_text().strip())
        depois = json.loads(bruto)
        self.assertEqual("parcial_publico_aprovado", depois["resultado"])
        self.assertNotIn("fixture", depois)
        self.assertNotIn("persistencia", depois)
        self.assertEqual(antes["publicacao_depois"]["deployment"], depois["publicacao_antes"]["deployment"])
        self.assertEqual(antes["publicacao_depois"]["sha"], depois["publicacao_antes"]["sha"])
        self.assertEqual(antes["openapi"]["sha256"], depois["openapi"]["sha256"])
        self.assertTrue(all(c["aprovado"] for c in depois["chamadas"]))


if __name__ == "__main__":
    unittest.main()

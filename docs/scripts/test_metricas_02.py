"""Regressões da apuração: eventos distintos, ausência de observação e cortes."""
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("metricas", Path(__file__).with_name("calcular-metricas-02.py"))
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)
SNAPSHOT = m.ROOT / "docs/evidencias/sprint-2/exportacoes/20261123T220209564272Z"
FONTES = m.ROOT / "docs/evidencias/sprint-2/metricas-02/fontes-20261123"


class MetricasTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.r = m.apurar(SNAPSHOT, "2026-11-23T21:50:00Z", FONTES)

    def test_contagens_por_semana_e_redeploy_comprovado(self):
        self.assertEqual([0, 0, 0, 6, 8, 2], [s["implantacoes"] for s in self.r["semanas"]])
        self.assertEqual([0, 0, 3, 5, 6, 2], [s["throughput_tecnico"] for s in self.r["semanas"]])
        self.assertEqual(40, len(self.r["deployments_auditados"]))
        self.assertEqual(16, sum(d["contado"] for d in self.r["deployments_auditados"]))
        redeploys = [p for p in self.r["publicacoes"] if p["tipo"] == "redeploy"]
        self.assertEqual([7773456323], [p["deployment"] for p in redeploys])
        self.assertEqual(15, len({p["sha"] for p in self.r["publicacoes"]}))

    def test_prova_falha_fora_e_primeira_publicacao_por_pr(self):
        mapa = {x["pr"]: x["deployment"] for x in self.r["mudancas"]}
        self.assertEqual(24, len(mapa))
        self.assertNotIn(122, mapa)
        for n in range(100, 110):
            self.assertEqual(7772377365, mapa[n])
        self.assertEqual(7790932145, mapa[110])
        self.assertEqual(8008807028, mapa[124])
        self.assertEqual([], self.r["fluxo_lacunas"])
        self.assertEqual(3, len(self.r["publicacoes"][0]["commits_novos_sem_pr_conhecido"]))
        self.assertTrue(all(not d["commits_novos_sem_pr_conhecido"] for d in self.r["publicacoes"][1:]))

    def test_corte_historico_exclui_eventos_futuros(self):
        r = m.apurar(SNAPSHOT, "2026-11-21T03:00:00Z", FONTES)
        self.assertEqual(11, len(r["publicacoes"]))
        self.assertEqual(19, len(r["mudancas"]))
        self.assertEqual(11, len(r["fluxo"]))
        self.assertNotIn(119, [x["pr"] for x in r["mudancas"]])
        with self.assertRaises(ValueError):
            m.apurar(SNAPSHOT, "2026-11-27T03:00:00Z", FONTES)

    def test_inactive_nao_apaga_sucesso_e_falha_posterior_prevalece(self):
        ss = [{"id": 1, "state": "success", "created_at": "2026-11-13T17:00:00Z"},
              {"id": 2, "state": "inactive", "created_at": "2026-11-14T17:00:00Z"}]
        self.assertEqual("success", m.operacional(ss, m.dt("2026-11-15T00:00:00Z"))["state"])
        ss.append({"id": 3, "state": "failure", "created_at": "2026-11-14T18:00:00Z"})
        self.assertEqual("failure", m.operacional(ss, m.dt("2026-11-15T00:00:00Z"))["state"])

    def test_success_apos_smoke_conta_antes_do_termino_do_job(self):
        job = next(j for j in m.ler(SNAPSHOT / "run-43476705265-jobs.json") if j["id"] == 132062290267)
        self.assertEqual("2026-11-13T17:53:53Z", job["completed_at"])
        smoke = next(s for s in job["steps"] if s["name"] == "Saúde e smoke autenticado")
        self.assertEqual("2026-11-13T17:53:50Z", smoke["completed_at"])
        # Fim exclusivo: no instante do success (17:53:51) ele ainda fica fora.
        # No segundo seguinte já conta, embora o job termine só às 17:53:53.
        for corte, esperadas in [("2026-11-13T17:53:50Z", 0), ("2026-11-13T17:53:51Z", 0),
                                 ("2026-11-13T17:53:52Z", 1), ("2026-11-13T17:53:54Z", 1)]:
            with self.subTest(corte=corte):
                r = m.apurar(SNAPSHOT, corte, FONTES)
                self.assertEqual(esperadas, len(r["publicacoes"]))
                if esperadas:
                    self.assertEqual(7772377365, r["publicacoes"][0]["deployment"])
                    self.assertEqual("2026-11-13T17:53:51Z", r["publicacoes"][0]["publicado"])

    def test_zero_incidente_exige_monitoramento_e_causa_nao_duplica(self):
        corte = m.dt("2026-11-14T00:00:00Z")
        pub = [{"deployment": 1, "publicado": "2026-11-13T17:00:00Z"}]
        with tempfile.TemporaryDirectory() as d:
            p = Path(d)
            def gravar(efetivo, incidentes):
                cab = "id,detectado_em,recuperado_em,deployments_causais,implantacao_recuperacao,autor,fonte_deteccao,fonte_recuperacao,observacao\n"
                (p / "incidentes.csv").write_text(cab + incidentes, encoding="utf-8")
                cab = "inicio,fim,ambiente,autor,fonte,cobertura\n"
                linha = "2026-11-13T16:00:00Z,2026-11-14T00:00:00Z,producao,equipe,https://github.com/exemplo,efetiva\n"
                (p / "monitoramento.csv").write_text(cab + (linha if efetivo else ""), encoding="utf-8")
                hashes = {f.name: hashlib.sha256(f.read_bytes()).hexdigest() for f in p.glob("*.csv")}
                (p / "manifesto.json").write_text(json.dumps({"sha256": hashes}), encoding="utf-8")
            gravar(False, "")
            self.assertIsNone(m.incidentes(p, pub, corte)["valor"])
            gravar(True, "")
            r = m.incidentes(p, pub, corte)
            self.assertEqual(0, r["valor"])
            self.assertEqual([], r["recuperacoes"])
            self.assertIsNone(m.incidentes(p, [], corte)["valor"])
            linha = "a,2026-11-13T18:00:00Z,,1,,equipe,https://github.com/exemplo,,\n"
            gravar(True, linha + linha.replace("a,", "b,", 1))
            self.assertEqual(1, m.incidentes(p, pub, corte)["numerador"])

    def test_percentis_nearest_rank_e_ausencia_nao_e_zero(self):
        self.assertIsNone(m.distribuicao([])["media"])
        self.assertEqual(9, m.distribuicao(list(range(1, 11)))["p85"])
        self.assertEqual(10, m.distribuicao(list(range(1, 11)))["p95"])

    def test_artefatos_publicados_reproduzem_todos_os_prs_e_semanas(self):
        pasta = m.ROOT / "docs/evidencias/sprint-2/metricas-02"
        for dia, corte in [("20261123", "2026-11-23T21:50:00Z"), ("20261120", "2026-11-21T03:00:00Z")]:
            r = self.r if dia == "20261123" else m.apurar(SNAPSHOT, corte, FONTES)
            self.assertEqual(r, m.ler(pasta / dia / "resultado.json"))
            self.assertEqual(m.tabelas(r), (pasta / dia / "tabelas.md").read_text(encoding="utf-8"))
        ambiente = pasta / "ambiente-20261123"
        m.integridade(ambiente)
        self.assertTrue(any(a["name"] == "producao" for a in m.ler(ambiente / "ambientes.json")))


if __name__ == "__main__":
    unittest.main()

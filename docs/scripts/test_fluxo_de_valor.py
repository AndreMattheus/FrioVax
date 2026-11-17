"""Testes das contas do fluxo de valor com dados sintéticos, sem consultar a API."""

import csv
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock


def carregar(nome):
    spec = importlib.util.spec_from_file_location(nome, Path(__file__).with_name(nome + ".py"))
    modulo = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(modulo)
    return modulo


fluxo = carregar("medir-fluxo-de-valor")
validacao = carregar("validar-medicao-sprint-2")
H = 3600


def gravar(pasta, nome, dados):
    (pasta / nome).write_text(json.dumps(dados), encoding="utf-8")


def gravar_csv(pasta, nome, linhas):
    with (pasta / f"{nome}.csv").open("w", encoding="utf-8", newline="") as arquivo:
        escritor = csv.writer(arquivo, lineterminator="\n")
        escritor.writerow(validacao.CABECALHOS[nome].split(","))
        escritor.writerows(linhas)


def pull(numero, criado, merged="2026-10-02T00:00:00Z", autor="ana", base="main"):
    return {"number": numero, "created_at": criado, "merged_at": merged,
            "merge_commit_sha": "b" * 40, "user": {"login": autor}, "base": {"ref": base}}


def review(login, quando, estado="APPROVED", tipo="User"):
    return {"user": {"login": login, "type": tipo}, "state": estado, "submitted_at": quando}


class TestIntervalos(unittest.TestCase):
    def test_timestamp_sem_fuso_e_intervalo_invertido_sao_rejeitados(self):
        with self.assertRaisesRegex(ValueError, "sem fuso"):
            fluxo.instante("2026-10-01T00:00:00")
        with self.assertRaisesRegex(ValueError, "anterior"):
            fluxo.uniao([(4, 2)])

    def test_uniao_funde_sobreposicoes_sem_somar_duas_vezes(self):
        blocos = fluxo.uniao([(0, 4), (2, 6), (8, 9)])
        self.assertEqual([(0, 6), (8, 9)], blocos)

    def test_subtrair_preserva_apenas_o_que_sobra(self):
        self.assertEqual([(0, 2), (5, 6), (8, 10)], fluxo.subtrair([(0, 6), (8, 10)], [(2, 5)]))


class TestEsperaRevisao(unittest.TestCase):
    def test_ignora_bot_autorreview_e_conta_saida_de_rascunho(self):
        with tempfile.TemporaryDirectory() as nome:
            corte = Path(nome)
            gravar(corte, "pulls.json", [pull(1, "2026-10-01T00:00:00Z"), pull(2, "2026-10-01T00:00:00Z"),
                                         pull(3, "2026-10-01T00:00:00Z", merged=None)])
            gravar(corte, "pr-1-events.json", [{"event": "ready_for_review", "created_at": "2026-10-01T10:00:00Z"}])
            gravar(corte, "pr-1-reviews.json", [review("ana", "2026-10-01T11:00:00Z"),
                                                review("bot", "2026-10-01T11:00:00Z", tipo="Bot"),
                                                review("bia", "2026-10-01T20:00:00Z", "COMMENTED")])
            gravar(corte, "pr-2-events.json", [])
            gravar(corte, "pr-2-reviews.json", [review("bia", "2026-10-01T05:00:00Z", "PENDING")])
            medidos, abertos = fluxo.espera_revisao(corte, 1, 3)
        self.assertEqual([(1, 10.0)], [(m[0], m[3]) for m in medidos])
        self.assertEqual([2], abertos)

    def test_pr_integrado_em_outra_branch_nao_entra_na_linha_de_base(self):
        with tempfile.TemporaryDirectory() as nome:
            corte = Path(nome)
            gravar(corte, "pulls.json", [pull(1, "2026-10-01T00:00:00Z", base="release")])
            self.assertEqual(([], []), fluxo.espera_revisao(corte, 1, 1))

    def test_distribuicao_vazia_tem_diagnostico(self):
        with tempfile.TemporaryDirectory() as nome:
            corte = Path(nome)
            gravar(corte, "pulls.json", [])
            args = mock.Mock(corte=nome, primeiro=1, ultimo=1)
            with self.assertRaisesRegex(ValueError, "Nenhum PR"):
                fluxo.imprimir_revisao(args)

    def test_review_anterior_a_saida_de_rascunho_e_inconsistente(self):
        with tempfile.TemporaryDirectory() as nome:
            corte = Path(nome)
            gravar(corte, "pulls.json", [pull(1, "2026-10-01T00:00:00Z")])
            gravar(corte, "pr-1-events.json", [{"event": "ready_for_review", "created_at": "2026-10-01T10:00:00Z"}])
            gravar(corte, "pr-1-reviews.json", [review("bia", "2026-10-01T05:00:00Z")])
            with self.assertRaisesRegex(ValueError, "fora do período"):
                fluxo.espera_revisao(corte, 1, 1)


class TestItem(unittest.TestCase):
    def alterar(self, pasta, tabela, identificador, **campos):
        linhas = fluxo.ler_csv(tabela, pasta)
        linha = next(r for r in linhas if r.get("id", r.get("evento_id")) == identificador)
        linha.update(campos)
        gravar_csv(pasta, tabela, [list(r.values()) for r in linhas])

    def preparar(self, pasta, fim_espera="2026-10-02T12:00:00Z"):
        g = "https://github.com/o/r"
        gravar_csv(pasta, "eventos", [
            ["c", 9, "chegada", "2026-10-01T00:00:00Z", "bot", f"{g}/issues/9", "observado"],
            ["p", 9, "pedido_revisao", "2026-10-02T10:00:00Z", "ana", f"{g}/pull/5", "observado"],
            ["v", 9, "primeira_revisao", "2026-10-02T12:00:00Z", "bia", f"{g}/pull/5", "observado"],
            ["m", 9, "merge", "2026-10-02T12:00:00Z", "bia", f"{g}/pull/5", "observado"],
        ])
        gravar_csv(pasta, "implantacoes", [
            ["pub", 1, "a" * 40, "producao", "publicacao", "success", "2026-10-03T23:00:00Z",
             "2026-10-05T00:00:00Z", "2026-10-05T00:00:00Z", f"{g}/actions/runs/1", f"{g}/actions/runs/1", "4;5", ""],
        ])
        gravar_csv(pasta, "intervalos", [
            ["b", 9, "backlog", "espera", "2026-10-01T00:00:00Z", "2026-10-02T00:00:00Z", "ana", g, "observado", ""],
            ["t1", 9, "implementacao", "trabalho", "2026-10-02T06:00:00Z", "2026-10-02T10:00:00Z", "ana", g, "relatado", ""],
            ["a1", 9, "ci-pr", "automacao", "2026-10-02T09:00:00Z", "2026-10-02T11:00:00Z", "gha", g, "observado", ""],
            ["r", 9, "revisao", "espera", "2026-10-02T10:00:00Z", fim_espera, "ana", g, "observado", ""],
            ["i", 9, "implantacao", "espera", "2026-10-02T12:00:00Z", "2026-10-05T00:00:00Z", "ana", g, "observado", ""],
            ["a2", 9, "implantacao", "automacao", "2026-10-04T23:00:00Z", "2026-10-05T01:00:00Z", "gha", g, "observado", ""],
        ])
        gravar_csv(pasta, "amostra", [[9, "X", "ana", "2026-10-01", "", "", g]])

    def test_eficiencia_usa_uniao_e_precedencia_do_processamento(self):
        with tempfile.TemporaryDirectory() as nome:
            pasta = Path(nome)
            self.preparar(pasta)
            item = fluxo.medir_item(9, pasta)
        self.assertEqual(96 * H, item["lead"])
        self.assertEqual(4 * H, item["trabalho"])
        # Automação recortada na publicação; a parte concomitante ao trabalho não soma de novo.
        self.assertEqual(3 * H, item["automacao"])
        self.assertEqual(6 * H, item["processamento"])
        self.assertEqual({"backlog": 24 * H, "implantacao": 59 * H, "revisao": 1 * H}, item["espera_por_etapa"])
        self.assertEqual(6 * H, item["lacuna"])
        self.assertAlmostEqual(6.25, item["eficiencia"])
        self.assertEqual(["backlog", "desenvolvimento", "revisao", "publicacao"], [f["fase"] for f in item["fases"]])
        self.assertEqual(sum(f["fim"].timestamp() - f["inicio"].timestamp() for f in item["fases"]), item["lead"])

    def test_intervalo_em_curso_impede_apuracao(self):
        with tempfile.TemporaryDirectory() as nome:
            pasta = Path(nome)
            self.preparar(pasta, fim_espera="")
            with self.assertRaisesRegex(ValueError, "em curso"):
                fluxo.medir_item(9, pasta)

    def test_publicacao_anterior_ao_merge_e_rejeitada(self):
        with tempfile.TemporaryDirectory() as nome:
            pasta = Path(nome)
            self.preparar(pasta)
            self.alterar(pasta, "implantacoes", "pub", publicado_em="2026-10-01T10:00:00Z")
            with self.assertRaisesRegex(ValueError, "fora de ordem"):
                fluxo.medir_item(9, pasta)

    def test_esperas_de_etapas_distintas_nao_duplicam_o_total(self):
        with tempfile.TemporaryDirectory() as nome:
            pasta = Path(nome)
            self.preparar(pasta)
            self.alterar(pasta, "intervalos", "r", fim="2026-10-02T13:00:00Z")
            with self.assertRaisesRegex(ValueError, "se sobrepõem"):
                fluxo.medir_item(9, pasta)

    def test_bloqueio_tem_precedencia_e_coluna_propria_nas_fases(self):
        with tempfile.TemporaryDirectory() as nome:
            pasta = Path(nome)
            self.preparar(pasta)
            linhas = fluxo.ler_csv("intervalos", pasta)
            bloqueio = dict(linhas[3], id="bloq", tipo="bloqueio",
                            inicio="2026-10-02T10:00:00Z", fim="2026-10-02T12:00:00Z")
            gravar_csv(pasta, "intervalos", [list(r.values()) for r in linhas + [bloqueio]])
            item = fluxo.medir_item(9, pasta)
        self.assertEqual(H, item["bloqueio"])
        self.assertEqual(0, item["fases"][2]["espera"])
        self.assertEqual(H, item["fases"][2]["bloqueio"])
        self.assertEqual(item["lead"], sum(item[c] for c in ["processamento", "bloqueio", "espera", "lacuna"]))

    def preparar_fontes(self, pasta):
        corte = pasta / "corte"
        corte.mkdir()
        gravar(corte, "pulls.json", [pull(5, "2026-10-02T00:00:00Z", merged="2026-10-02T12:00:00Z")])
        gravar(corte, "issue-9-timeline.json", [
            {"__typename": "AddedToProjectV2Event", "createdAt": "2026-10-01T00:00:00Z"},
            {"__typename": "ProjectV2ItemStatusChangedEvent", "status": "Em Progresso", "createdAt": "2026-10-02T00:00:00Z"},
        ])
        gravar(corte, "pr-5-events.json", [{"event": "review_requested", "created_at": "2026-10-02T10:00:00Z"}])
        gravar(corte, "pr-5-reviews.json", [review("bia", "2026-10-02T12:00:00Z")])
        gravar(corte, "deployments.json", [{"id": 1, "environment": "producao", "production_environment": True,
                                          "sha": "a" * 40, "created_at": "2026-10-03T23:00:00Z"}])
        gravar(corte, "deployment-1-statuses.json", [{"state": "success", "created_at": "2026-10-05T00:00:00Z"}])
        for ident, numero in [("a1", 11), ("a2", 12)]:
            self.alterar(pasta, "intervalos", ident, fonte=f"https://github.com/o/r/actions/runs/{numero}")
            r = next(r for r in fluxo.ler_csv("intervalos", pasta) if r["id"] == ident)
            gravar(corte, f"run-{numero}-jobs.json", [{"started_at": r["inicio"], "completed_at": r["fim"]}])
        (pasta / "validacao").mkdir()
        gravar(pasta / "validacao", "fluxo-91-jobs.json", {"consultas": [{"job": {
            "html_url": "https://github.com/o/r/actions/runs/1", "head_sha": "a" * 40,
            "conclusion": "success", "completed_at": "2026-10-05T01:00:00Z", "steps": [{
                "name": "Saúde e smoke autenticado", "conclusion": "success", "completed_at": "2026-10-05T00:00:00Z",
            }],
        }}]})
        return corte

    def test_fontes_exigem_ancestralidade_mesmo_com_pr_listado_no_csv(self):
        with tempfile.TemporaryDirectory() as nome:
            pasta = Path(nome)
            self.preparar(pasta)
            corte = self.preparar_fontes(pasta)
            with mock.patch.object(fluxo, "contem_commit", return_value=True) as contem:
                fluxo.validar_fontes(9, pasta, corte)
                contem.assert_called_once_with("b" * 40, "a" * 40)
            with mock.patch.object(fluxo, "contem_commit", return_value=False):
                with self.assertRaisesRegex(ValueError, "nenhum deployment"):
                    fluxo.validar_fontes(9, pasta, corte)

    def test_fontes_detectam_segundos_arredondados(self):
        with tempfile.TemporaryDirectory() as nome:
            pasta = Path(nome)
            self.preparar(pasta)
            corte = self.preparar_fontes(pasta)
            self.alterar(pasta, "eventos", "m", instante="2026-10-02T12:00:31Z")
            with self.assertRaisesRegex(ValueError, "evento merge diverge"):
                fluxo.validar_fontes(9, pasta, corte)

    def test_fontes_rejeitam_intervalo_que_inclui_pausa_entre_jobs(self):
        with tempfile.TemporaryDirectory() as nome:
            pasta = Path(nome)
            self.preparar(pasta)
            corte = self.preparar_fontes(pasta)
            gravar(corte, "run-11-jobs.json", [
                {"started_at": "2026-10-02T09:00:00Z", "completed_at": "2026-10-02T09:30:00Z"},
                {"started_at": "2026-10-02T10:00:00Z", "completed_at": "2026-10-02T11:00:00Z"},
            ])
            with mock.patch.object(fluxo, "contem_commit", return_value=True):
                with self.assertRaisesRegex(ValueError, "automação diverge"):
                    fluxo.validar_fontes(9, pasta, corte)

    def test_fontes_rejeitam_smoke_falho(self):
        with tempfile.TemporaryDirectory() as nome:
            pasta = Path(nome)
            self.preparar(pasta)
            corte = self.preparar_fontes(pasta)
            arq = pasta / "validacao/fluxo-91-jobs.json"
            dados = fluxo.ler_json(arq)
            dados["consultas"][0]["job"]["steps"][0]["conclusion"] = "failure"
            gravar(arq.parent, arq.name, dados)
            with mock.patch.object(fluxo, "contem_commit", return_value=True):
                with self.assertRaisesRegex(ValueError, "sem smoke"):
                    fluxo.validar_fontes(9, pasta, corte)

    def test_fontes_rejeitam_pular_a_primeira_publicacao(self):
        with tempfile.TemporaryDirectory() as nome:
            pasta = Path(nome)
            self.preparar(pasta)
            corte = self.preparar_fontes(pasta)
            dados = fluxo.ler_json(corte / "deployments.json")
            dados.append(dict(dados[0], id=2))
            gravar(corte, "deployments.json", dados)
            gravar(corte, "deployment-2-statuses.json", [{"state": "success", "created_at": "2026-10-04T23:30:00Z"}])
            with mock.patch.object(fluxo, "contem_commit", return_value=True):
                with self.assertRaisesRegex(ValueError, "primeiro deployment"):
                    fluxo.validar_fontes(9, pasta, corte)


if __name__ == "__main__":
    unittest.main()

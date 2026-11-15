"""Testes da espera até a primeira revisão com dados sintéticos, sem consultar a API."""

import importlib.util
import json
from pathlib import Path
import tempfile
import unittest


def carregar(nome):
    spec = importlib.util.spec_from_file_location(nome, Path(__file__).with_name(nome + ".py"))
    modulo = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(modulo)
    return modulo


fluxo = carregar("medir-fluxo-de-valor")


def gravar(pasta, nome, dados):
    (pasta / nome).write_text(json.dumps(dados), encoding="utf-8")


def pull(numero, criado, merged="2026-10-02T00:00:00Z", autor="ana"):
    return {"number": numero, "created_at": criado, "merged_at": merged, "user": {"login": autor}}


def review(login, quando, estado="APPROVED", tipo="User"):
    return {"user": {"login": login, "type": tipo}, "state": estado, "submitted_at": quando}


class TestEsperaRevisao(unittest.TestCase):
    def test_ignora_bot_autorreview_e_conta_saida_de_rascunho(self):
        with tempfile.TemporaryDirectory() as nome:
            corte = Path(nome)
            gravar(corte, "pulls.json", [pull(1, "2026-10-01T00:00:00Z"), pull(2, "2026-10-01T00:00:00Z"),
                                         pull(3, "2026-10-01T00:00:00Z", merged=None)])
            gravar(corte, "pr-1-events.json", [{"event": "ready_for_review", "created_at": "2026-10-01T10:00:00Z"}])
            gravar(corte, "pr-1-reviews.json", [review("ana", "2026-10-01T11:00:00Z"),
                                                review("bot", "2026-10-01T11:00:00Z", tipo="Bot"),
                                                review("bia", "2026-10-02T12:00:00Z", "COMMENTED")])
            gravar(corte, "pr-2-events.json", [])
            gravar(corte, "pr-2-reviews.json", [review("bia", "2026-10-01T05:00:00Z", "PENDING")])
            medidos, abertos = fluxo.espera_revisao(corte, 1, 3)
        self.assertEqual([(1, 26.0)], [(m[0], m[3]) for m in medidos])
        self.assertEqual([2], abertos)


if __name__ == "__main__":
    unittest.main()

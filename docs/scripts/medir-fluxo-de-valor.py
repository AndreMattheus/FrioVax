#!/usr/bin/env python3
"""Mede a espera até a primeira revisão por PR de um corte preservado (stdlib)."""

import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import statistics


REVIEWS_VALIDAS = {"APPROVED", "CHANGES_REQUESTED", "COMMENTED"}


def instante(valor):
    return datetime.fromisoformat(valor.replace("Z", "+00:00")).astimezone(timezone.utc)


def horas(segundos):
    return segundos / 3600


def ler_json(arquivo):
    return json.loads(arquivo.read_text(encoding="utf-8"))


def espera_revisao(corte, primeiro, ultimo):
    """Abertura do PR (ou primeira saída de rascunho) até a primeira review de outra pessoa."""
    pulls = {pr["number"]: pr for pr in ler_json(corte / "pulls.json")}
    medidos, abertos = [], []
    for numero in range(primeiro, ultimo + 1):
        pr = pulls.get(numero)
        if not pr or not pr["merged_at"]:
            continue
        autor = pr["user"]["login"]
        prontos = [e["created_at"] for e in ler_json(corte / f"pr-{numero}-events.json")
                   if e["event"] == "ready_for_review"]
        inicio = instante(min(prontos)) if prontos else instante(pr["created_at"])
        reviews = [instante(r["submitted_at"]) for r in ler_json(corte / f"pr-{numero}-reviews.json")
                   if r["state"] in REVIEWS_VALIDAS and r["submitted_at"]
                   and r["user"]["type"] != "Bot" and r["user"]["login"] != autor]
        if reviews:
            medidos.append((numero, inicio, min(reviews), horas((min(reviews) - inicio).total_seconds())))
        else:
            abertos.append(numero)
    return medidos, abertos


def imprimir_revisao(args):
    medidos, abertos = espera_revisao(Path(args.corte).resolve(), args.primeiro, args.ultimo)
    print("| PR | Início (UTC) | Primeira review (UTC) | Espera (h) |")
    print("|---|---|---|---:|")
    for numero, inicio, review, espera in medidos:
        print(f"| #{numero} | {inicio:%Y-%m-%dT%H:%M:%SZ} | {review:%Y-%m-%dT%H:%M:%SZ} | {espera:.2f} |")
    esperas = [m[3] for m in medidos]
    acima = sum(espera > 24 for espera in esperas)
    maior = max(medidos, key=lambda m: m[3])
    print(f"\nn={len(esperas)}; acima de 24 h: {acima} ({100 * acima / len(esperas):.1f}%); "
          f"mediana {statistics.median(esperas):.2f} h; máximo {maior[3]:.2f} h (#{maior[0]})")
    if abertos:
        print(f"Integrados sem review de outra pessoa (fora da distribuição): {abertos}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="medida", required=True)
    revisao = sub.add_parser("revisao", help="espera até a primeira review por PR de um corte")
    revisao.add_argument("corte")
    revisao.add_argument("--primeiro", type=int, default=36)
    revisao.add_argument("--ultimo", type=int, default=64)
    revisao.set_defaults(executar=imprimir_revisao)
    args = parser.parse_args()
    args.executar(args)

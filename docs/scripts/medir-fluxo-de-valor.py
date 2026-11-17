#!/usr/bin/env python3
"""Mede a espera até a primeira revisão e a eficiência de fluxo da amostra (stdlib)."""

import argparse
import csv
from datetime import datetime, timezone
from functools import lru_cache
import json
import math
from pathlib import Path
import re
import statistics
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[2]
PASTA = ROOT / "docs/evidencias/sprint-2"
CORTE = PASTA / "exportacoes/20261117T023019358359Z"
REVIEWS_VALIDAS = {"APPROVED", "CHANGES_REQUESTED", "COMMENTED"}


def instante(valor):
    data = datetime.fromisoformat(valor.replace("Z", "+00:00"))
    if data.utcoffset() is None:
        raise ValueError(f"Timestamp sem fuso: {valor}")
    return data.astimezone(timezone.utc)


def exigir(condicao, mensagem):
    if not condicao:
        raise ValueError(mensagem)


def horas(segundos):
    return segundos / 3600


def uniao(intervalos):
    blocos = []
    for inicio, fim in sorted(intervalos):
        exigir(fim >= inicio, "Intervalo com fim anterior ao início")
        if blocos and inicio <= blocos[-1][1]:
            blocos[-1][1] = max(blocos[-1][1], fim)
        else:
            blocos.append([inicio, fim])
    return [tuple(bloco) for bloco in blocos]


def duracao(blocos):
    return sum((fim - inicio).total_seconds() for inicio, fim in blocos)


def recortar(blocos, inicio, fim):
    return [(max(a, inicio), min(b, fim)) for a, b in blocos if a < fim and b > inicio]


def subtrair(blocos, removidos):
    restantes = []
    for inicio, fim in blocos:
        cursor = inicio
        for a, b in removidos:
            if b <= cursor or a >= fim:
                continue
            if a > cursor:
                restantes.append((cursor, a))
            cursor = max(cursor, b)
        if cursor < fim:
            restantes.append((cursor, fim))
    return restantes


def ler_json(arquivo):
    return json.loads(arquivo.read_text(encoding="utf-8"))


def espera_revisao(corte, primeiro, ultimo):
    """Abertura do PR (ou primeira saída de rascunho) até a primeira review de outra pessoa."""
    pulls = {pr["number"]: pr for pr in ler_json(corte / "pulls.json")}
    medidos, abertos = [], []
    for numero in range(primeiro, ultimo + 1):
        pr = pulls.get(numero)
        if not pr or not pr["merged_at"] or pr["base"]["ref"] != "main":
            continue
        autor = pr["user"]["login"]
        prontos = [e["created_at"] for e in ler_json(corte / f"pr-{numero}-events.json")
                   if e["event"] == "ready_for_review"]
        inicio = instante(min(prontos)) if prontos else instante(pr["created_at"])
        reviews = [instante(r["submitted_at"]) for r in ler_json(corte / f"pr-{numero}-reviews.json")
                   if r["state"] in REVIEWS_VALIDAS and r["submitted_at"]
                   and r["user"]["type"] != "Bot" and r["user"]["login"] != autor]
        if reviews:
            exigir(inicio <= min(reviews) <= instante(pr["merged_at"]),
                   f"PR #{numero}: primeira review fora do período até o merge")
            medidos.append((numero, inicio, min(reviews), horas((min(reviews) - inicio).total_seconds())))
        else:
            abertos.append(numero)
    return medidos, abertos


def ler_csv(nome, pasta):
    with (pasta / f"{nome}.csv").open(encoding="utf-8", newline="") as arquivo:
        return list(csv.DictReader(arquivo))


@lru_cache(maxsize=None)
def contem_commit(merge, sha):
    resultado = subprocess.run(["git", "merge-base", "--is-ancestor", merge, sha],
                               cwd=ROOT, capture_output=True)
    exigir(resultado.returncode in {0, 1},
           f"Histórico Git insuficiente para conferir {merge} em {sha}; usar clone com histórico completo")
    return resultado.returncode == 0


def validar_fontes(issue, pasta, corte):
    """Confere os CSVs contra eventos preservados e a ancestralidade real dos SHAs."""
    registros = [r for r in ler_csv("eventos", pasta) if r["issue"] == str(issue)]
    eventos = {r["tipo"]: r for r in registros}
    exigir(len(eventos) == len(registros), f"#{issue}: tipo de evento duplicado")
    numero = int(re.search(r"/pull/(\d+)", eventos["merge"]["fonte"]).group(1))
    pr = next(p for p in ler_json(corte / "pulls.json") if p["number"] == numero)
    exigir(pr["merged_at"] and pr["base"]["ref"] == "main", f"PR #{numero}: não integrado em main")
    timeline = ler_json(corte / f"issue-{issue}-timeline.json")
    pedidos = [e for e in ler_json(corte / f"pr-{numero}-events.json") if e["event"] == "review_requested"]
    reviews = sorted((r for r in ler_json(corte / f"pr-{numero}-reviews.json")
                      if r["state"] in REVIEWS_VALIDAS and r["submitted_at"]
                      and r["user"]["type"] != "Bot" and r["user"]["login"] != pr["user"]["login"]),
                     key=lambda r: instante(r["submitted_at"]))
    esperados = {
        "chegada": min(e["createdAt"] for e in timeline if e["__typename"] == "AddedToProjectV2Event"),
        "pedido_revisao": min(e["created_at"] for e in pedidos),
        "primeira_revisao": reviews[0]["submitted_at"], "merge": pr["merged_at"],
    }
    for tipo, data in esperados.items():
        exigir(instante(eventos[tipo]["instante"]) == instante(data),
               f"#{issue}: evento {tipo} diverge do corte preservado")

    entregas = []
    for deploy in ler_json(corte / "deployments.json"):
        if deploy["environment"] != "producao" or not deploy["production_environment"]:
            continue
        statuses = ler_json(corte / f"deployment-{deploy['id']}-statuses.json")
        sucessos = [s for s in statuses if s["state"] == "success"]
        if sucessos and contem_commit(pr["merge_commit_sha"], deploy["sha"]):
            entregas.append((min(instante(s["created_at"]) for s in sucessos), deploy))
    exigir(bool(entregas), f"#{issue}: nenhum deployment de produção bem-sucedido contém o merge")
    publicado, deploy = min(entregas, key=lambda par: par[0])
    publicacoes = [r for r in ler_csv("implantacoes", pasta)
                   if r["resultado"] == "success" and str(numero) in r["prs"].split(";")]
    exigir(bool(publicacoes), f"#{issue}: publicação ausente no CSV")
    publicacao = min(publicacoes, key=lambda r: instante(r["publicado_em"]))
    exigir(publicacao["github_deployment_id"] == str(deploy["id"])
           and publicacao["sha"] == deploy["sha"] and publicacao["ambiente"] == "producao"
           and instante(publicacao["inicio"]) == instante(deploy["created_at"])
           and instante(publicacao["publicado_em"]) == publicado,
           f"#{issue}: publicação não corresponde ao primeiro deployment que contém o merge")
    consultas = ler_json(pasta / "validacao/fluxo-91-jobs.json")["consultas"]
    job = next(c["job"] for c in consultas if c["job"]["html_url"] == publicacao["smoke_fonte"])
    smoke = next(s for s in job["steps"] if s["name"] == "Saúde e smoke autenticado")
    exigir(job["conclusion"] == smoke["conclusion"] == "success" and job["head_sha"] == deploy["sha"]
           and instante(smoke["completed_at"]) == instante(publicacao["smoke_em"])
           and instante(smoke["completed_at"]) <= publicado <= instante(job["completed_at"]),
           f"#{issue}: publicação sem smoke bem-sucedido no SHA entregue")

    intervalos = [r for r in ler_csv("intervalos", pasta) if r["issue"] == str(issue)]
    pares_revisao = set()
    for pedido in pedidos:
        inicio = instante(pedido["created_at"])
        posteriores = [instante(r["submitted_at"]) for r in reviews if instante(r["submitted_at"]) >= inicio]
        if posteriores:
            pares_revisao.add((inicio, min(posteriores)))
    observados = {(instante(r["inicio"]), instante(r["fim"])) for r in intervalos
                  if r["tipo"] == "espera" and r["etapa"] == "revisao" and r["fim"]}
    exigir(observados == pares_revisao, f"#{issue}: esperas de revisão divergem dos pedidos/reviews")
    for r in intervalos:
        if not r["fim"]:
            continue  # medir_item rejeita registros ainda em curso.
        inicio, fim = instante(r["inicio"]), instante(r["fim"])
        if r["tipo"] == "automacao":
            run = re.search(r"/actions/runs/(\d+)", r["fonte"]).group(1)
            jobs = ler_json(corte / f"run-{run}-jobs.json")
            blocos = uniao((instante(j["started_at"]), instante(j["completed_at"])) for j in jobs)
            exigir(blocos == [(inicio, fim)], f"{r['id']}: automação diverge dos jobs preservados")
        elif r["tipo"] == "espera" and r["etapa"] == "implantacao":
            exigir((inicio, fim) == (instante(pr["merged_at"]), publicado),
                   f"{r['id']}: espera de publicação diverge do merge/deployment")
        elif r["tipo"] == "espera" and r["etapa"] == "merge":
            exigir(reviews[-1]["state"] == "APPROVED"
                   and (inicio, fim) == (instante(reviews[-1]["submitted_at"]), instante(pr["merged_at"])),
                   f"{r['id']}: espera de merge diverge da aprovação/merge")
        elif r["tipo"] == "espera" and r["etapa"] == "backlog":
            entradas = {instante(e["createdAt"]) for e in timeline
                        if e.get("status") in {"Backlog", "Sprint Backlog"}}
            entradas.add(instante(esperados["chegada"]))
            saidas = {instante(e["createdAt"]) for e in timeline if e.get("status") == "Em Progresso"}
            exigir(inicio in entradas and fim in saidas, f"{r['id']}: fila diverge da timeline do quadro")


def medir_item(issue, pasta):
    eventos = [e for e in ler_csv("eventos", pasta) if e["issue"] == str(issue)]
    def evento(tipo):
        candidatos = [e for e in eventos if e["tipo"] == tipo]
        exigir(len(candidatos) == 1, f"#{issue}: evento {tipo} ausente ou duplicado")
        return candidatos[0]
    chegada = instante(evento("chegada")["instante"])
    merge = evento("merge")
    pr = re.search(r"/pull/(\d+)", merge["fonte"]).group(1)
    publicacoes = [r for r in ler_csv("implantacoes", pasta)
                   if r["resultado"] == "success" and pr in r["prs"].split(";")]
    if not publicacoes:
        raise ValueError(f"#{issue}: nenhuma publicação bem-sucedida registrada contém o PR #{pr}")
    publicacao = min(publicacoes, key=lambda r: instante(r["publicado_em"]))
    fim = instante(publicacao["publicado_em"])

    intervalos = [r for r in ler_csv("intervalos", pasta) if r["issue"] == str(issue)]
    em_curso = [r["id"] for r in intervalos if not r["fim"]]
    if em_curso:
        raise ValueError(f"#{issue}: intervalos em curso não entram na apuração: {', '.join(em_curso)}")

    def blocos(filtro):
        return recortar(uniao((instante(r["inicio"]), instante(r["fim"]))
                              for r in intervalos if filtro(r)), chegada, fim)

    trabalho = blocos(lambda r: r["tipo"] == "trabalho")
    estimado = blocos(lambda r: r["tipo"] == "trabalho" and r["precisao"] == "estimado")
    automacao = blocos(lambda r: r["tipo"] == "automacao")
    processamento = uniao(trabalho + automacao)
    # Precedência evita contar o mesmo instante duas vezes: processamento > bloqueio > espera.
    bloqueio = subtrair(blocos(lambda r: r["tipo"] == "bloqueio"), processamento)
    ocupado = uniao(processamento + bloqueio)
    espera = subtrair(blocos(lambda r: r["tipo"] == "espera"), ocupado)
    etapas = {}
    for etapa in sorted({r["etapa"] for r in intervalos if r["tipo"] == "espera"}):
        etapas[etapa] = duracao(subtrair(
            blocos(lambda r, etapa=etapa: r["tipo"] == "espera" and r["etapa"] == etapa), ocupado))
    lead = (fim - chegada).total_seconds()
    exigir(lead > 0 and chegada <= instante(merge["instante"]) <= fim,
           f"#{issue}: chegada, merge e publicação fora de ordem")
    exigir(math.isclose(sum(etapas.values()), duracao(espera), rel_tol=0, abs_tol=1e-6),
           f"#{issue}: esperas de etapas distintas se sobrepõem")
    backlog = blocos(lambda r: r["tipo"] == "espera" and r["etapa"] == "backlog")
    exigir(bool(backlog), f"#{issue}: fila do quadro sem registro")
    marcos = [("backlog", chegada),
              ("desenvolvimento", max(b for _, b in backlog)),
              ("revisao", instante(evento("pedido_revisao")["instante"])),
              ("publicacao", instante(merge["instante"])), (None, fim)]
    exigir(all(a <= b for (_, a), (_, b) in zip(marcos, marcos[1:])),
           f"#{issue}: marcos de fase fora de ordem")
    fases = []
    for (nome, inicio), (_, termino) in zip(marcos, marcos[1:]):
        def parte(itens):
            return duracao(recortar(itens, inicio, termino))
        fases.append({"fase": nome, "inicio": inicio, "fim": termino,
                      "trabalho": parte(trabalho), "automacao": parte(subtrair(automacao, trabalho)),
                      "espera": parte(espera), "bloqueio": parte(bloqueio),
                      "lacuna": (termino - inicio).total_seconds() - parte(ocupado) - parte(espera)})
    return {
        "issue": issue, "pr": int(pr), "chegada": chegada, "publicacao": fim,
        "implantacao": publicacao["github_deployment_id"], "lead": lead,
        "trabalho": duracao(trabalho), "trabalho_estimado": duracao(estimado),
        "automacao": duracao(automacao),
        "automacao_exclusiva": duracao(subtrair(automacao, trabalho)),
        "processamento": duracao(processamento), "bloqueio": duracao(bloqueio),
        "espera": duracao(espera), "espera_por_etapa": etapas,
        "lacuna": lead - duracao(processamento) - duracao(bloqueio) - duracao(espera),
        "eficiencia": 100 * duracao(processamento) / lead, "fases": fases,
    }


def imprimir_revisao(args):
    medidos, abertos = espera_revisao(Path(args.corte).resolve(), args.primeiro, args.ultimo)
    print("| PR | Início (UTC) | Primeira review (UTC) | Espera (h) |")
    print("|---|---|---|---:|")
    for numero, inicio, review, espera in medidos:
        print(f"| #{numero} | {inicio:%Y-%m-%dT%H:%M:%SZ} | {review:%Y-%m-%dT%H:%M:%SZ} | {espera:.2f} |")
    esperas = [m[3] for m in medidos]
    exigir(bool(esperas), "Nenhum PR integrado em main com review válida no intervalo selecionado")
    acima = sum(espera > 24 for espera in esperas)
    maior = max(medidos, key=lambda m: m[3])
    print(f"\nn={len(esperas)}; acima de 24 h: {acima} ({100 * acima / len(esperas):.1f}%); "
          f"mediana {statistics.median(esperas):.2f} h; máximo {maior[3]:.2f} h (#{maior[0]})")
    if abertos:
        print(f"Integrados sem review de outra pessoa (fora da distribuição): {abertos}")


def imprimir_itens(args):
    pasta = Path(args.pasta).resolve()
    amostra = [int(r["issue"]) for r in ler_csv("amostra", pasta) if not r["saida"]]
    exigir(len(amostra) == 3 and len(set(amostra)) == 3, "A amostra deve conter três tarefas distintas")
    for issue in amostra:
        validar_fontes(issue, pasta, Path(args.corte).resolve())
    resultados = [medir_item(issue, pasta) for issue in amostra]
    print("| Item | PR | Lead (h) | Trabalho (h) | dos quais estimado (h) | Automação (h) "
          "| Automação fora do trabalho (h) | Processamento (h) | Bloqueio (h) | Espera (h) "
          "| Lacuna (h) | Eficiência |")
    print("|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|")
    for r in resultados:
        print(f"| #{r['issue']} | #{r['pr']} | {horas(r['lead']):.2f} | {horas(r['trabalho']):.2f} "
              f"| {horas(r['trabalho_estimado']):.2f} | {horas(r['automacao']):.2f} "
              f"| {horas(r['automacao_exclusiva']):.2f} "
              f"| {horas(r['processamento']):.2f} | {horas(r['bloqueio']):.2f} "
              f"| {horas(r['espera']):.2f} | {horas(r['lacuna']):.2f} | {r['eficiencia']:.2f}% |")
    print("\nEspera por etapa (h), já sem sobreposição com processamento/bloqueio:")
    for r in resultados:
        etapas = "; ".join(f"{etapa} {horas(s):.2f}" for etapa, s in r["espera_por_etapa"].items())
        print(f"- #{r['issue']}: chegada {r['chegada']:%Y-%m-%dT%H:%M:%SZ}, publicação "
              f"{r['publicacao']:%Y-%m-%dT%H:%M:%SZ} (deployment {r['implantacao']}); {etapas}")
    for r in resultados:
        print(f"\n#{r['issue']} por fase (UTC; h):")
        print("| Fase | Início | Fim | Trabalho | Automação fora do trabalho | Bloqueio | Espera | Lacuna |")
        print("|---|---|---|---:|---:|---:|---:|---:|")
        for f in r["fases"]:
            print(f"| {f['fase']} | {f['inicio']:%Y-%m-%dT%H:%M:%SZ} | {f['fim']:%Y-%m-%dT%H:%M:%SZ} "
                  f"| {horas(f['trabalho']):.2f} | {horas(f['automacao']):.2f} "
                  f"| {horas(f['bloqueio']):.2f} | {horas(f['espera']):.2f} | {horas(f['lacuna']):.2f} |")
    media = statistics.mean(r["eficiencia"] for r in resultados)
    print(f"\nMédia aritmética das eficiências por item: {media:.2f}% (n={len(resultados)})")


def imprimir_backlog(args):
    texto = Path(args.documento).read_text(encoding="utf-8")
    dados = [(float(a), float(b)) for a, b in re.findall(
        r"^\| \[#\d+\].*\| ([\d.]+) \| ([\d.]+) \|$", texto, re.MULTILINE)]
    exigir(len(dados) == 24, "Esperados os 24 cartões técnicos da apuração da Sprint 1")
    lead = statistics.mean(a for a, _ in dados)
    cycle = statistics.mean(b for _, b in dados)
    print(f"n={len(dados)}; lead médio {lead:.2f} d; cycle médio {cycle:.2f} d; "
          f"diferença {lead - cycle:.2f} d ({100 * (lead - cycle) / lead:.1f}% do lead)")
    print("Reprodução aproximada pela tabela publicada, já arredondada; início por PR em 17 dos 24 cartões.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="medida", required=True)
    revisao = sub.add_parser("revisao", help="espera até a primeira review por PR de um corte")
    revisao.add_argument("corte")
    revisao.add_argument("--primeiro", type=int, default=36)
    revisao.add_argument("--ultimo", type=int, default=64)
    revisao.set_defaults(executar=imprimir_revisao)
    itens = sub.add_parser("itens", help="eficiência de fluxo das tarefas ativas da amostra")
    itens.add_argument("--pasta", default=str(PASTA))
    itens.add_argument("--corte", default=str(CORTE))
    itens.set_defaults(executar=imprimir_itens)
    backlog = sub.add_parser("backlog", help="reproduzir os valores publicados da fila da Sprint 1")
    backlog.add_argument("--documento", default=str(ROOT / "docs/metricas-sprint-1.md"))
    backlog.set_defaults(executar=imprimir_backlog)
    args = parser.parse_args()
    try:
        args.executar(args)
    except (ValueError, KeyError, StopIteration, OSError) as erro:
        print(f"Apuração falhou: {erro}", file=sys.stderr)
        sys.exit(1)

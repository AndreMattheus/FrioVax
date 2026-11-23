#!/usr/bin/env python3
"""Apuração offline da #90. Entradas imutáveis; stdlib, sem chamadas à rede."""

import argparse
import csv
from datetime import datetime, timedelta, timezone
import hashlib
import json
import math
from pathlib import Path
import statistics

ROOT = Path(__file__).resolve().parents[2]
INICIO = "2026-10-21T03:00:00Z"
FIM = "2026-12-01T03:00:00Z"
FUSO = timezone(timedelta(hours=-3))  # America/Fortaleza, sem horário de verão
PROJECT = "PVT_kwHOCrfrY84Bifhr"
# População fixada antes do cálculo: implementação, testes, contratos executáveis,
# containers, CI e publicação. ADRs, processo, documentação e cerimônias separados.
TECNICAS = set(range(68, 87)) | {88, 89, 99}


def dt(s):
    t = datetime.fromisoformat(s.replace("Z", "+00:00"))
    if t.utcoffset() is None:
        raise ValueError(f"Instante sem fuso: {s}")
    return t


def ler(p):
    return json.loads(p.read_text(encoding="utf-8"))


def distribuicao(valores):
    valores = sorted(valores)
    if not valores:
        return {"n": 0, "media": None, "mediana": None, "p85": None, "p95": None, "min": None, "max": None}
    return {"n": len(valores), "media": statistics.mean(valores), "mediana": statistics.median(valores),
            "p85": valores[math.ceil(.85 * len(valores)) - 1],
            "p95": valores[math.ceil(.95 * len(valores)) - 1], "min": min(valores), "max": max(valores)}


def semana(s):
    d = dt(s).astimezone(FUSO).date()
    return (d - timedelta(days=d.weekday())).isoformat()


def operacional(statuses, corte):
    # inactive significa substituído por outra publicação, não desfaz success.
    ss = [s for s in statuses if dt(s["created_at"]) < corte and s["state"] != "inactive"]
    return max(ss, key=lambda s: (dt(s["created_at"]), s["id"])) if ss else None


def integridade(pasta):
    m = ler(pasta / "manifesto.json")
    if m["resultado"] != "completo":
        raise ValueError("Snapshot incompleto")
    for q in m["consultas"]:
        b = (pasta / q["arquivo"]).read_bytes()
        if hashlib.sha256(b).hexdigest() != q["sha256"] or len(json.loads(b)) != q["registros"]:
            raise ValueError(f"Hash/contagem divergente: {q['arquivo']}")
    return m


def incidentes(fontes, publicacoes, corte):
    manifesto = ler(fontes / "manifesto.json")
    for nome, sha in manifesto["sha256"].items():
        if hashlib.sha256((fontes / nome).read_bytes()).hexdigest() != sha:
            raise ValueError(f"Fonte local alterada: {nome}")
    def csvs(nome):
        with (fontes / nome).open(encoding="utf-8", newline="") as f:
            return list(csv.DictReader(f))
    cobertura = sorted((dt(r["inicio"]), min(dt(r["fim"]), corte)) for r in csvs("monitoramento.csv")
                       if r["cobertura"] == "efetiva" and r["ambiente"] == "producao" and r["fim"]
                       and r["autor"] and r["fonte"] and dt(r["inicio"]) < corte)
    avaliadas = []
    for p in publicacoes:
        cursor = dt(p["publicado"])
        for a, b in cobertura:
            if a <= cursor < b:
                cursor = b
        if cursor >= corte:
            avaliadas.append(p["deployment"])
    ii = [i for i in csvs("incidentes.csv") if dt(i["detectado_em"]) < corte]
    causais = set()
    recuperacoes = []
    sem_causa = []
    for i in ii:
        if not i["deployments_causais"]:
            sem_causa.append(i["id"])
        causais.update(int(d) for d in i["deployments_causais"].split(";") if d)
        if i["recuperado_em"] and dt(i["recuperado_em"]) < corte:
            h = (dt(i["recuperado_em"]) - dt(i["detectado_em"])).total_seconds() / 3600
            if h < 0 or not i["fonte_deteccao"] or not i["fonte_recuperacao"]:
                raise ValueError("Incidente sem fonte ou com recuperação anterior à detecção")
            recuperacoes.append({"id": i["id"], "horas": h})
    n = len(avaliadas)
    numerador = len(causais & set(avaliadas))
    return {"valor": 100 * numerador / n if n and not sem_causa else None,
            "N_publicacoes": len(publicacoes), "N_avaliadas": n, "numerador": numerador if n else None,
            "deployments_avaliados": avaliadas, "incidentes_registrados": len(ii),
            "incidentes_sem_causa": sem_causa, "recuperacoes": recuperacoes,
            "motivo": "cobertura efetiva contínua da publicação ao corte exigida; ausência de fonte não prova zero"}


def apurar(pasta, corte, fontes):
    m = integridade(pasta)
    a, b = dt(INICIO), dt(corte)
    if not a < b <= min(dt(FIM), dt(m["inicio"])):
        raise ValueError("Corte deve ser posterior ao início e não ultrapassar o início da extração nem a sprint")
    prs = ler(pasta / "pulls.json")
    integrados = [p for p in prs if p["merged_at"] and not p["draft"] and
                  p["base"]["ref"] == "main" and dt(p["merged_at"]) < b]
    populacao = [p for p in integrados if a <= dt(p["merged_at"])]
    jobs = [j for f in sorted(pasta.glob("run-*-jobs.json")) for j in ler(f)]
    registros, grupos = [], {}
    for d in ler(pasta / "deployments.json"):
        if d["environment"] != "producao" or dt(d["created_at"]) >= b:
            continue
        s = operacional(ler(pasta / f"deployment-{d['id']}-statuses.json"), b)
        candidatos = [j for j in jobs if j["head_sha"] == d["sha"] and j["started_at"] and
                      j["completed_at"] and
                      dt(j["started_at"]) - timedelta(seconds=30) <= dt(d["created_at"]) <= dt(j["completed_at"]) and
                      any(t["name"] == "Registrar deployment" for t in j.get("steps", []))]
        if len(candidatos) > 1:
            raise ValueError(f"Deployment {d['id']}: associação ambígua a jobs")
        j = candidatos[0] if candidatos else None
        r = {"id": d["id"], "sha": d["sha"], "criado": d["created_at"],
             "estado": s["state"] if s else "sem_status", "status_id": s["id"] if s else None,
             "instante": s["created_at"] if s else None,
             "job": j["id"] if j else None, "contado": False}
        registros.append(r)
        if j:
            grupos.setdefault(j["id"], {"job": j, "registros": []})["registros"].append(r)
        else:
            r["motivo"] = "sem job/smoke vinculável; não comprova publicação"
    publicacoes, vistos, anteriores = [], set(), set()
    commits_pr = {p["merge_commit_sha"] for p in integrados}
    for p in integrados:
        arquivo = pasta / f"pr-{p['number']}-commits.json"
        if arquivo.exists():
            commits_pr.update(c["sha"] for c in ler(arquivo))
    for g in sorted(grupos.values(), key=lambda g: g["job"]["started_at"]):
        j, rs = g["job"], g["registros"]
        smoke = [t for t in j["steps"] if t["name"] == "Saúde e smoke autenticado" and
                 t["conclusion"] == "success" and t["completed_at"] and dt(t["completed_at"]) < b]
        render = [t for t in j["steps"] if t["name"] == "Publicar SHA e esperar Render" and
                  t["conclusion"] == "success"]
        sucessos = [r for r in rs if r["estado"] == "success" and r["instante"] and
                    a <= dt(r["instante"]) < b]
        for r in rs:
            r["motivo"] = "falha, execução aberta ou sem smoke/Render aprovado no corte"
        if not (sucessos and smoke and render and j["conclusion"] == "success"):
            continue
        # begin cria o registro explícito após o automático do environment.
        r = max(sucessos, key=lambda r: (r["criado"], r["id"]))
        repetido = r["sha"] in vistos
        vistos.add(r["sha"])
        if dt(smoke[0]["completed_at"]) > dt(r["instante"]):
            raise ValueError("Status success anterior ao smoke")
        for item in rs:
            item["motivo"] = "duplicata do mesmo job/publicação"
        r.update(contado=True, motivo="publicação confirmada")
        grafo = ler(pasta / f"ancestry-{r['sha']}.json")
        shas = {c["sha"] for c in grafo}
        if r["sha"] not in shas or any(p not in shas for c in grafo for p in c["parents"]):
            raise ValueError("Ancestralidade incompleta")
        incluidos = [p["number"] for p in integrados if p["merge_commit_sha"] in shas]
        # Nenhum PR integrado depois da publicação pode ser incluído por ordenação.
        if any(dt(p["merged_at"]) > dt(r["instante"]) for p in integrados if p["number"] in incluidos):
            raise ValueError("PR incorporado antes do merge; conferir relógios")
        publicacoes.append({"deployment": r["id"], "sha": r["sha"], "publicado": r["instante"],
                            "tipo": "redeploy" if repetido else "publicacao",
                            "justificativa": "job distinto executou publicação no Render e novo smoke" if repetido else "primeira publicação deste SHA",
                            "smoke": smoke[0]["completed_at"], "job": j["id"], "fonte": j["html_url"],
                            "prs_contidos": incluidos, "commits_contidos": sorted(shas),
                            "commits_novos": sorted(shas - anteriores),
                            "commits_novos_sem_pr_conhecido": sorted(shas - anteriores - commits_pr),
                            "semana": semana(r["instante"])})
        anteriores = shas
    mudancas, nao_publicados = [], []
    for p in populacao:
        ds = [d for d in publicacoes if p["number"] in d["prs_contidos"]]
        if not ds:
            nao_publicados.append(p["number"])
            continue
        d = min(ds, key=lambda d: d["publicado"])
        commits = ler(pasta / f"pr-{p['number']}-commits.json")
        if not commits:
            raise ValueError(f"PR {p['number']}: sem commits originais")
        primeiro = min(commits, key=lambda c: dt(c["authored_at"]))
        h = (dt(d["publicado"]) - dt(primeiro["authored_at"])).total_seconds() / 3600
        if h < 0:
            raise ValueError("Commit posterior à publicação")
        mudancas.append({"pr": p["number"], "primeiro_sha": primeiro["sha"],
                         "primeiro_commit": primeiro["authored_at"], "merge": p["merged_at"],
                         "deployment": d["deployment"], "publicado": d["publicado"], "horas": h,
                         "pos_merge_h": (dt(d["publicado"]) - dt(p["merged_at"])).total_seconds() / 3600})
    # Mesmo método da Sprint 1. Fechamento é aproximação, diferenças para o merge
    # ficam visíveis e não são corrigidas silenciosamente.
    ligacoes = {}
    for p in populacao:
        for i in ler(pasta / f"pr-{p['number']}-closing-issues.json"):
            if i["repository"]["nameWithOwner"] == m["repositorio"]:
                ligacoes.setdefault(i["number"], []).append(p)
    fluxo, lacunas, excluidos = [], [], []
    issues = ler(pasta / "issues.json")
    for i in issues:
        n = i["number"]
        if not i["title"].startswith("[S2-") or n not in TECNICAS:
            excluidos.append(n)
            continue
        if not i["closed_at"] or not a <= dt(i["closed_at"]) < b:
            continue
        timeline = ler(pasta / f"issue-{n}-timeline.json")
        eventos = [e for e in timeline if e.get("project", {}).get("id") == PROJECT and dt(e["createdAt"]) < b]
        chegadas = [e["createdAt"] for e in eventos if e["__typename"] == "AddedToProjectV2Event"]
        inicios = [e["createdAt"] for e in eventos if e["__typename"] == "ProjectV2ItemStatusChangedEvent" and
                   e["status"].casefold() == "em progresso"]
        pp = [p for p in ligacoes.get(n, []) if dt(p["merged_at"]) <= dt(i["closed_at"])]
        if not pp or not chegadas:
            lacunas.append({"issue": n, "motivo": "sem chegada ou PR integrado vinculado antes do fechamento"})
            continue
        inicio = min(inicios) if inicios else min(p["created_at"] for p in pp)
        chegada, fim = min(chegadas), i["closed_at"]
        if not dt(chegada) <= dt(inicio) <= dt(fim):
            lacunas.append({"issue": n, "motivo": "ordem temporal inconsistente"})
            continue
        fluxo.append({"issue": n, "prs": sorted(p["number"] for p in pp), "chegada": chegada,
                      "inicio": inicio, "fonte_inicio": "quadro" if inicios else "PR (aproximação)",
                      "entrega": fim, "lead_d": (dt(fim) - dt(chegada)).total_seconds() / 86400,
                      "cycle_d": (dt(fim) - dt(inicio)).total_seconds() / 86400,
                      "fechamento_menos_ultimo_merge_h": (dt(fim) - max(dt(p["merged_at"]) for p in pp)).total_seconds() / 3600,
                      "semana": semana(fim)})
    # Review é auxiliar para interpretação; pedido explícito, fallback declarado.
    reviews, reviews_abertas, retrabalho, revisados = [], [], [], []
    for p in populacao:
        ee = ler(pasta / f"pr-{p['number']}-events.json")
        pronto = max([p["created_at"]] + [e["created_at"] for e in ee if e["event"] == "ready_for_review" and dt(e["created_at"]) < b])
        pedidos = [e["created_at"] for e in ee if e["event"] == "review_requested" and dt(pronto) <= dt(e["created_at"]) < b]
        inicio = min(pedidos) if pedidos else pronto
        rr = [r for r in ler(pasta / f"pr-{p['number']}-reviews.json") if r["state"] in
              {"APPROVED", "CHANGES_REQUESTED", "COMMENTED"} and r["user"]["type"] != "Bot" and
              r["user"]["login"] != p["user"]["login"] and r["submitted_at"] and
              dt(inicio) <= dt(r["submitted_at"]) <= dt(p["merged_at"])]
        todas = [r for r in ler(pasta / f"pr-{p['number']}-reviews.json") if r["submitted_at"] and
                 r["user"]["type"] != "Bot" and r["user"]["login"] != p["user"]["login"] and
                 dt(r["submitted_at"]) <= dt(p["merged_at"])]
        if todas:
            revisados.append(p["number"])
            if any(r["state"] == "CHANGES_REQUESTED" for r in todas):
                retrabalho.append(p["number"])
        if rr:
            fim = min(r["submitted_at"] for r in rr)
            reviews.append({"pr": p["number"], "inicio": inicio, "fonte_inicio": "pedido" if pedidos else "abertura/pronto (aproximação)",
                            "fim": fim, "horas": (dt(fim) - dt(inicio)).total_seconds() / 3600})
        else:
            reviews_abertas.append({"pr": p["number"], "idade_h": (b - dt(inicio)).total_seconds() / 3600})
    semanas = []
    d = dt(semana(INICIO) + "T00:00:00-03:00")
    while d < b:
        s = d.date().isoformat()
        semanas.append({"segunda": s, "inicio": max(d, a).isoformat(), "fim_exclusivo": min(d + timedelta(days=7), b).isoformat(),
                        "parcial": d < a or d + timedelta(days=7) > b,
                        "implantacoes": sum(x["semana"] == s for x in publicacoes),
                        "throughput_tecnico": sum(x["semana"] == s for x in fluxo),
                        "tarefas_fechadas_geral": sum(i["title"].startswith("[S2-") and bool(i["closed_at"]) and
                            a <= dt(i["closed_at"]) < b and semana(i["closed_at"]) == s for i in issues)})
        d += timedelta(days=7)
    return {"schema": 1, "snapshot": pasta.relative_to(ROOT).as_posix(), "corte_exclusivo": corte,
            "janela_inicio": INICIO, "manifesto_sha256": hashlib.sha256((pasta / "manifesto.json").read_bytes()).hexdigest(),
            "publicacoes": publicacoes, "deployments_auditados": sorted(registros, key=lambda r: r["id"]),
            "mudancas": sorted(mudancas, key=lambda r: r["pr"]), "prs_sem_publicacao": nao_publicados,
            "tempo_mudanca_h": distribuicao([x["horas"] for x in mudancas]),
            "fluxo": sorted(fluxo, key=lambda r: r["issue"]), "fluxo_lacunas": lacunas,
            "fluxo_excluidos": sorted(excluidos), "lead_cartao_d": distribuicao([x["lead_d"] for x in fluxo]),
            "cycle_cartao_d": distribuicao([x["cycle_d"] for x in fluxo]),
            "reviews": sorted(reviews, key=lambda r: r["pr"]), "reviews_abertas": reviews_abertas,
            "espera_review_h": distribuicao([x["horas"] for x in reviews]), "semanas": semanas,
            "retrabalho_revisao": {"prs": retrabalho, "populacao": revisados, "numerador": len(retrabalho),
                                   "denominador": len(revisados), "percentual": 100 * len(retrabalho) / len(revisados) if revisados else None},
            "fontes_locais": fontes.relative_to(ROOT).as_posix(),
            "taxa_falha": incidentes(fontes, publicacoes, b)}


def tabelas(r):
    linhas = ["<!-- Gerado por calcular-metricas-02.py; não editar números manualmente. -->",
              "| Semana (segunda, Fortaleza) | Parcial | Publicações | Throughput técnico | Tarefas S2 fechadas (geral) |",
              "|---|---|---:|---:|---:|"]
    linhas += [f"| {s['segunda']} | {'sim' if s['parcial'] else 'não'} | {s['implantacoes']} | {s['throughput_tecnico']} | {s['tarefas_fechadas_geral']} |" for s in r["semanas"]]
    linhas += ["", "| Distribuição | n | Média | Mediana | p85 | p95 | Mínimo | Máximo |", "|---|---:|---:|---:|---:|---:|---:|---:|"]
    for nome, chave in [("Tempo de mudança (h)", "tempo_mudanca_h"), ("Lead de cartão (d)", "lead_cartao_d"),
                         ("Cycle de cartão (d)", "cycle_cartao_d"), ("Espera até review (h)", "espera_review_h")]:
        s = r[chave]
        linhas.append(f"| {nome} | {s['n']} | " + " | ".join("—" if s[k] is None else f"{s[k]:.2f}" for k in ["media", "mediana", "p85", "p95", "min", "max"]) + " |")
    linhas += ["", "| Deployment (tipo) | SHA | Publicado (UTC) | Job com smoke | PRs da sprint na primeira publicação |", "|---|---|---|---|---|"]
    for d in r["publicacoes"]:
        pp = ", ".join(f"#{x['pr']}" for x in r["mudancas"] if x["deployment"] == d["deployment"]) or "nenhum novo PR"
        linhas.append(f"| {d['deployment']} ({d['tipo']}) | `{d['sha'][:12]}` | {d['publicado']} | [{d['job']}]({d['fonte']}) | {pp} |")
    linhas += ["", "| PR | Primeiro commit (UTC) | Deployment inicial | Tempo de mudança (h) | Merge → publicação (h) |", "|---|---|---|---:|---:|"]
    linhas += [f"| #{x['pr']} | {x['primeiro_commit']} | {x['deployment']} | {x['horas']:.2f} | {x['pos_merge_h']:.2f} |" for x in r["mudancas"]]
    linhas += ["", "| Cartão | PRs | Chegada (UTC) | Início (UTC) | Fonte início | Fechamento (UTC) | Lead (d) | Cycle (d) | Fechamento − merge (h) |", "|---|---|---|---|---|---|---:|---:|---:|"]
    linhas += [f"| #{x['issue']} | {','.join(map(str,x['prs']))} | {x['chegada']} | {x['inicio']} | {x['fonte_inicio']} | {x['entrega']} | {x['lead_d']:.2f} | {x['cycle_d']:.2f} | {x['fechamento_menos_ultimo_merge_h']:.2f} |" for x in r["fluxo"]]
    return "\n".join(linhas) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("snapshot", type=Path)
    parser.add_argument("--corte", required=True, help="fim exclusivo ISO 8601 com fuso")
    parser.add_argument("--saida", type=Path, required=True)
    parser.add_argument("--fontes", type=Path, required=True)
    args = parser.parse_args()
    r = apurar(args.snapshot.resolve(), args.corte, args.fontes.resolve())
    args.saida.mkdir(parents=True, exist_ok=True)
    (args.saida / "resultado.json").write_bytes((json.dumps(r, ensure_ascii=False, indent=2) + "\n").encode("utf-8"))
    (args.saida / "tabelas.md").write_bytes(tabelas(r).encode("utf-8"))
    print(f"Publicações={len(r['publicacoes'])}; mudanças={len(r['mudancas'])}; cartões={len(r['fluxo'])}; lacunas={len(r['fluxo_lacunas'])}")


if __name__ == "__main__":
    main()

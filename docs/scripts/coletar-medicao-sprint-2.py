#!/usr/bin/env python3
"""Coleta somente leitura, paginada, de metadados para a Sprint 2 (stdlib + gh)."""

import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile


ROOT = Path(__file__).resolve().parents[2]
EVIDENCIAS = ROOT / "docs/evidencias/sprint-2"
REPO = "AndreMattheus/FrioVax"
INICIO = "2026-10-21T03:00:00Z"
FIM = "2026-12-01T03:00:00Z"

TIMELINE = """query($number:Int!, $endCursor:String) {
  repository(owner:"AndreMattheus", name:"FrioVax") {
    issue(number:$number) {
      timelineItems(first:100, after:$endCursor, itemTypes:[ADDED_TO_PROJECT_V2_EVENT,
        PROJECT_V2_ITEM_STATUS_CHANGED_EVENT,CLOSED_EVENT,REOPENED_EVENT]) {
        nodes {
          __typename
          ... on AddedToProjectV2Event {id createdAt actor{login} project{id} wasAutomated}
          ... on ProjectV2ItemStatusChangedEvent {
            id createdAt actor{login} project{id} previousStatus status wasAutomated
          }
          ... on ClosedEvent {id createdAt actor{login}}
          ... on ReopenedEvent {id createdAt actor{login}}
        }
        pageInfo{hasNextPage endCursor}
      }
    }
  }
}"""

PROJECT = """query($endCursor:String) {
  user(login:"AndreMattheus") {projectV2(number:1) {
    id
    items(first:100, after:$endCursor) {
      nodes {
        id createdAt updatedAt
        content {
          ... on Issue {number url repository{nameWithOwner}}
          ... on PullRequest {number url repository{nameWithOwner}}
        }
        fieldValues(first:100) {
          nodes {
            ... on ProjectV2ItemFieldDateValue {date field{... on ProjectV2Field{name}}}
            ... on ProjectV2ItemFieldSingleSelectValue {
              name field{... on ProjectV2SingleSelectField{name}}
            }
          }
          pageInfo{hasNextPage}
        }
      }
      pageInfo{hasNextPage endCursor}
    }
  }}
}"""


def agora():
    return datetime.now(timezone.utc).isoformat(timespec="microseconds").replace("+00:00", "Z")


def selecionar(obj, *campos):
    return {campo: obj.get(campo) for campo in campos}


def usuario(obj):
    return selecionar(obj or {}, "login", "type")


def pull(obj):
    dado = selecionar(obj, "id", "number", "html_url", "state", "draft", "created_at",
                      "updated_at", "closed_at", "merged_at", "merge_commit_sha")
    dado["user"] = usuario(obj.get("user"))
    for campo in ("head", "base"):
        dado[campo] = selecionar(obj[campo], "ref", "sha")
    return dado


def issue(obj):
    dado = selecionar(obj, "id", "number", "html_url", "state", "created_at", "updated_at", "closed_at")
    dado["user"] = usuario(obj.get("user"))
    dado["assignees"] = [usuario(u) for u in obj.get("assignees", [])]
    dado["labels"] = [selecionar(label, "id", "name") for label in obj.get("labels", [])]
    return dado


def commit(obj):
    return {"sha": obj["sha"], "html_url": obj["html_url"],
            "author": usuario(obj.get("author")),
            "committer": usuario(obj.get("committer")),
            "authored_at": obj["commit"]["author"]["date"],
            "committed_at": obj["commit"]["committer"]["date"]}


def review(obj):
    dado = selecionar(obj, "id", "html_url", "state", "submitted_at", "commit_id", "author_association")
    dado["user"] = usuario(obj.get("user"))
    return dado


def evento(obj):
    dado = selecionar(obj, "id", "event", "created_at", "commit_id", "commit_url")
    dado["actor"] = usuario(obj.get("actor"))
    for nome in ("requested_reviewer", "review_requester", "assignee"):
        if nome in obj:
            dado[nome] = usuario(obj[nome])
    if obj.get("label"):
        dado["label"] = selecionar(obj["label"], "name")
    return dado


def execucao(obj):
    dado = selecionar(obj, "id", "workflow_id", "event", "status", "conclusion", "head_sha",
                      "head_branch", "created_at", "updated_at", "run_started_at", "run_attempt", "html_url")
    dado["actor"] = usuario(obj.get("actor"))
    return dado


def job(obj):
    return selecionar(obj, "id", "run_id", "run_attempt", "head_sha", "status", "conclusion",
                       "created_at", "started_at", "completed_at", "html_url")


def deployment(obj):
    dado = selecionar(obj, "id", "sha", "environment", "created_at", "updated_at", "statuses_url",
                      "production_environment", "transient_environment")
    dado["creator"] = usuario(obj.get("creator"))
    return dado


def status(obj):
    # environment_url, log_url, description e payload podem conter URLs privadas/assinadas.
    dado = selecionar(obj, "id", "state", "environment", "created_at", "updated_at", "url")
    dado["creator"] = usuario(obj.get("creator"))
    return dado


class Coletor:
    def __init__(self, destino):
        self.destino = destino
        self.consultas = []

    def consultar(self, argumentos, arquivo, projetar, chave=None, caminho=None):
        comando = ["gh", "api", "--paginate", "--slurp", *argumentos]
        inicio = agora()
        resultado = subprocess.run(comando, capture_output=True, text=True)
        if resultado.returncode:
            raise RuntimeError(f"gh falhou ({resultado.returncode}) na consulta de {arquivo}; corte incompleto")
        paginas = json.loads(resultado.stdout)
        if not isinstance(paginas, list):
            raise ValueError("Resposta sem envelope paginado")
        dados = []
        for pagina in paginas:
            if caminho:
                if pagina.get("errors"):
                    raise ValueError(f"GraphQL retornou erro em {arquivo}")
                conexao = pagina
                for parte in caminho:
                    conexao = conexao[parte]
                if conexao is None:
                    raise ValueError(f"Sem acesso à conexão de {arquivo}")
                dados.extend(conexao["nodes"])
                if conexao["pageInfo"]["hasNextPage"] and not conexao["pageInfo"].get("endCursor"):
                    raise ValueError("Cursor de paginação ausente")
            else:
                dados.extend(pagina[chave] if chave else pagina)
        if caminho and paginas and conexao["pageInfo"]["hasNextPage"]:
            raise ValueError("Paginação GraphQL incompleta")
        dados = [projetado for dado in dados if dado is not None
                 if (projetado := projetar(dado)) is not None]
        if arquivo == "project.json":
            for dado in dados:
                if dado["fieldValues"]["pageInfo"]["hasNextPage"]:
                    raise ValueError("Campos do Project truncados; ampliar a coleta antes de usar")
        fim = agora()
        conteudo = json.dumps(dados, ensure_ascii=False, indent=2) + "\n"
        (self.destino / arquivo).write_text(conteudo, encoding="utf-8")
        self.consultas.append({"comando": comando, "inicio": inicio, "fim": fim,
                               "paginas": len(paginas), "registros": len(dados),
                               "arquivo": arquivo,
                               "sha256": hashlib.sha256(conteudo.encode()).hexdigest()})
        return dados

    def rest(self, endpoint, arquivo, projetar, chave=None):
        separador = "&" if "?" in endpoint else "?"
        return self.consultar([f"repos/{REPO}/{endpoint}{separador}per_page=100"],
                              arquivo, projetar, chave=chave)


def coletar():
    inicio = agora()
    pai = EVIDENCIAS / "exportacoes"
    pai.mkdir(parents=True, exist_ok=True)
    destino = Path(tempfile.mkdtemp(prefix=".incompleto-", dir=pai))
    coletor = Coletor(destino)
    # Falha em qualquer fonte mantém a pasta .incompleto-, sem manifesto de sucesso.
    try:
        prs = coletor.rest("pulls?state=all&base=main", "pulls.json", pull)
        issues = coletor.rest("issues?state=all&milestone=3", "issues.json",
                              lambda obj: None if "pull_request" in obj else issue(obj))
        incidentes = coletor.rest("issues?state=all&labels=incidente", "incidentes.json",
                                  lambda obj: None if "pull_request" in obj else issue(obj))
        runs = coletor.rest("actions/runs", "runs.json", execucao, chave="workflow_runs")
        deploys = coletor.rest("deployments?environment=producao", "deployments.json", deployment)
        coletor.consultar(["graphql", "-f", f"query={PROJECT}"], "project.json", lambda obj: obj,
                          caminho=["data", "user", "projectV2", "items"])
        relevantes = [p for p in prs if 36 <= p["number"] <= 64 or any(
            INICIO <= p.get(campo, "") < FIM for campo in ("created_at", "merged_at", "updated_at")
            if p.get(campo))]
        tarefas = []
        for pr in relevantes:
            n = pr["number"]
            tarefas.extend([(f"pulls/{n}/commits", f"pr-{n}-commits.json", commit, None),
                            (f"pulls/{n}/reviews", f"pr-{n}-reviews.json", review, None),
                            (f"issues/{n}/events", f"pr-{n}-events.json", evento, None)])
        for run in runs:
            if INICIO <= run["created_at"] < FIM:
                tarefas.append((f"actions/runs/{run['id']}/attempts/{run['run_attempt']}/jobs",
                                f"run-{run['id']}-jobs.json", job, "jobs"))
        for deploy in deploys:
            tarefas.append((f"deployments/{deploy['id']}/statuses",
                            f"deployment-{deploy['id']}-statuses.json", status, None))
        with ThreadPoolExecutor(max_workers=4) as executor:
            list(executor.map(lambda tarefa: coletor.rest(*tarefa), tarefas))
        for item in issues:
            n = item["number"]
            coletor.consultar(["graphql", "-f", f"query={TIMELINE}", "-F", f"number={n}"],
                              f"issue-{n}-timeline.json", lambda obj: obj,
                              caminho=["data", "repository", "issue", "timelineItems"])
        fim = agora()
        manifesto = {"schema": 1, "repositorio": REPO, "inicio": inicio, "fim": fim,
                     "janela_inicio": INICIO, "janela_fim_exclusivo": FIM,
                     "fuso": "America/Fortaleza", "resultado": "completo",
                     "projecao": "metadados originais selecionados; sem corpos, emails, patches, payloads ou logs",
                     "observacao": "CSV de incidentes/monitoramento é fonte complementar; lista vazia não comprova zero falhas",
                     "consultas": sorted(coletor.consultas, key=lambda c: c["arquivo"])}
        (destino / "manifesto.json").write_text(json.dumps(manifesto, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        nome = datetime.fromisoformat(fim.replace("Z", "+00:00")).strftime("%Y%m%dT%H%M%S%fZ")
        destino = destino.rename(pai / nome)
        print(destino.relative_to(ROOT))
        print(f"Corte completo: {len(coletor.consultas)} consultas paginadas; "
              f"{len(deploys)} deployments, {len(incidentes)} issues com label incidente (sem inferir cobertura)")
    except Exception:
        print(f"Corte INCOMPLETO preservado em {destino.relative_to(ROOT)}")
        raise


def verificar_paginacao(corte):
    corte = Path(corte).resolve()
    if not corte.is_relative_to(EVIDENCIAS.resolve()):
        raise ValueError("O corte deve estar em docs/evidencias/sprint-2/")
    comando = ["gh", "api", "--paginate", "--slurp",
               f"repos/{REPO}/pulls?state=all&base=main&per_page=1"]
    inicio = agora()
    resultado = subprocess.run(comando, capture_output=True, text=True, check=True)
    paginas = json.loads(resultado.stdout)
    ids = sorted(p["id"] for pagina in paginas for p in pagina)
    originais = sorted(p["id"] for p in json.loads((corte / "pulls.json").read_text()))
    registro = {"inicio": inicio, "fim": agora(), "comando": comando,
                "corte": str(corte.relative_to(ROOT)), "paginas": len(paginas),
                "ids_originais": originais, "ids_reconsulta": ids, "coincidem": ids == originais}
    pasta = EVIDENCIAS / "validacao"
    pasta.mkdir(exist_ok=True)
    arquivo = pasta / f"paginacao-{corte.name}.json"
    if arquivo.exists():
        raise ValueError("A conferência já existe; usar outro corte para preservar a evidência")
    arquivo.write_text(json.dumps(registro, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if ids != originais:
        raise ValueError("IDs divergentes: investigar alterações concorrentes ou paginação")
    print(f"Paginação conferida: {len(paginas)} páginas, {len(ids)} PRs, mesmos IDs do corte")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--verificar-paginacao", metavar="CORTE")
    args = parser.parse_args()
    if args.verificar_paginacao:
        verificar_paginacao(args.verificar_paginacao)
    else:
        coletar()

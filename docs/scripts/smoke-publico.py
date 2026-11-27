#!/usr/bin/env python3
"""Smoke #89: dados fictícios próprios; credenciais apenas em memória; evidência por allowlist."""
import argparse
from datetime import datetime, timezone, timedelta
import getpass
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
import warnings

ROOT = Path(__file__).resolve().parents[2]
REPO = "AndreMattheus/FrioVax"


class Falha(Exception):
    """Só mensagens constantes/controladas podem chegar ao relatório."""


def agora():
    return datetime.now(timezone.utc).isoformat(timespec="microseconds").replace("+00:00", "Z")


def dt(s):
    return datetime.fromisoformat(s.replace("Z", "+00:00"))


def exigir(condicao, mensagem):
    if not condicao:
        raise Falha(mensagem)


def origem(url):
    p = urllib.parse.urlsplit(url)
    exigir(p.scheme == "https" and bool(p.hostname) and not p.username and not p.password
           and p.path in ("", "/") and not p.query and not p.fragment,
           "Use apenas a origem HTTPS pública, sem credenciais, caminho ou query.")
    try:
        porta = p.port
    except ValueError:
        raise Falha("Porta HTTPS inválida.") from None
    host = p.hostname.lower()
    if ":" in host:
        host = f"[{host}]"
    return f"https://{host}" + (f":{porta}" if porta is not None and porta != 443 else "")


class SemRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class Cliente:
    def __init__(self, base, relatorio):
        self.base = origem(base)
        self.relatorio = relatorio
        self.abrir = urllib.request.build_opener(SemRedirect()).open

    def chamar(self, etapa, caminho, esperado, metodo="GET", corpo=None, token=None, timeout=30):
        headers = {"Accept": "application/json", "User-Agent": "FrioVax-smoke-89",
                   "X-Correlation-Id": "s89-" + uuid.uuid4().hex}
        if token:
            headers["Authorization"] = "Bearer " + token
        dados = None if corpo is None else json.dumps(corpo).encode("utf-8")
        if dados is not None:
            headers["Content-Type"] = "application/json"
        req = urllib.request.Request(self.base + caminho, data=dados, method=metodo, headers=headers)
        inicio, t0 = agora(), time.perf_counter()
        try:
            try:
                resposta = self.abrir(req, timeout=timeout)
            except urllib.error.HTTPError as e:
                resposta = e
            with resposta as r:
                status, bruto = r.code, r.read()
                tipo = r.headers.get("Content-Type", "").split(";")[0]
        except (OSError, ValueError):
            status, bruto, tipo = 0, b"", ""
        duracao = time.perf_counter() - t0
        # Nunca registrar headers, corpos de autenticação, respostas ou exceções brutas.
        self.relatorio["chamadas"].append({"etapa": etapa, "metodo": metodo,
            "caminho": caminho, "inicio": inicio, "fim": agora(), "segundos": duracao,
            "http": status, "http_esperado": esperado, "content_type": tipo,
            "aprovado": status == esperado})
        print(f"{etapa}: HTTP {status}, {duracao:.3f} s", flush=True)
        exigir(status == esperado, "HTTP inesperado; conferir a etapa registrada, sem publicar o corpo da resposta.")
        try:
            obj = json.loads(bruto) if bruto and tipo.endswith("json") else None
        except (ValueError, UnicodeError):
            raise Falha("JSON inválido na resposta.") from None
        return obj, bruto, duracao


class GitHub:
    def __init__(self, relatorio):
        self.relatorio = relatorio

    def executar(self, args):
        self.relatorio["comandos_github"].append(["gh", *args])
        try:
            p = subprocess.run(["gh", *args], capture_output=True, text=True, encoding="utf-8", timeout=90)
        except (OSError, subprocess.TimeoutExpired):
            raise Falha("GitHub CLI indisponível ou sem resposta.") from None
        exigir(p.returncode == 0, "GitHub CLI falhou; conferir autenticação/permissão no terminal sem publicar credenciais.")
        return p.stdout

    def api(self, endpoint, chave=None):
        paginas = json.loads(self.executar(["api", "--paginate", "--slurp", f"repos/{REPO}/{endpoint}"]))
        if chave is None:
            return paginas[0]
        return [x for p in paginas for x in (p[chave] if chave else p)]

    def publicacao(self, run_id):
        run = self.api(f"actions/runs/{run_id}")
        exigir(run["conclusion"] == "success" and run["head_branch"] == "main", "Execução de publicação sem sucesso em main.")
        jobs = self.api(f"actions/runs/{run_id}/jobs?per_page=100", "jobs")
        jobs = [j for j in jobs if j["name"] == "publicar" and j["conclusion"] == "success"]
        exigir(len(jobs) == 1, "Não foi localizado um único job de publicação aprovado.")
        j = jobs[0]
        nomes = {s["name"] for s in j["steps"] if s["conclusion"] == "success"}
        exigir({"Migrar banco (sem saída sensível)", "Publicar SHA e esperar Render", "Saúde e smoke autenticado"} <= nomes,
               "Migração, Render ou smoke não comprovados pelo job.")
        sha = run["head_sha"]
        ds = self.api(f"deployments?environment=producao&sha={sha}&per_page=100", "")
        ds = [d for d in ds if dt(j["started_at"]) - timedelta(seconds=30) <= dt(d["created_at"]) <= dt(j["completed_at"])]
        aprovados = []
        for d in ds:
            ss = self.api(f"deployments/{d['id']}/statuses?per_page=100", "")
            ss = [s for s in ss if s["state"] != "inactive"]
            if ss and ss[0]["state"] == "success":
                aprovados.append((d, ss[0]))
        exigir(bool(aprovados), "Deployment de producao com success não localizado.")
        d, s = max(aprovados, key=lambda par: par[0]["created_at"])
        return {"sha": sha, "run": run["id"], "run_url": run["html_url"], "job": j["id"],
                "job_url": j["html_url"], "deployment": d["id"], "success_em": s["created_at"],
                "ambiente": d["environment"], "base_url": origem(s.get("environment_url") or ""),
                "steps": [{k: v.get(k) for k in ("name", "conclusion", "started_at", "completed_at")} for v in j["steps"]]}

    def atual(self):
        runs = self.api("actions/workflows/deploy.yml/runs?branch=main&status=success&per_page=100", "workflow_runs")
        exigir(bool(runs), "Nenhuma publicação aprovada localizada.")
        return self.publicacao(runs[0]["id"])

    def validar_destino(self, base, publicacao=None):
        variavel = self.api("environments/producao/variables/API_BASE_URL")
        destino = origem(variavel.get("value") or "")
        exigir(origem(base) == destino, "URL testada difere de API_BASE_URL do ambiente producao; redeploy interrompido.")
        if publicacao is not None:
            exigir(publicacao.get("ambiente") == "producao" and
                   origem(publicacao.get("base_url") or "") == destino,
                   "URL do deployment não corresponde ao ambiente producao testado.")
        self.relatorio["ambiente_redeploy"] = {"nome": "producao", "base_url": destino}

    def redeploy(self, sha, base):
        self.validar_destino(base)
        main = self.api("commits/main")
        exigir(main["sha"] == sha, "main mudou desde o início: repetir o smoke na nova versão antes do redeploy.")
        antigos = {r["id"] for r in self.api("actions/workflows/deploy.yml/runs?event=workflow_dispatch&per_page=100", "workflow_runs")}
        self.executar(["workflow", "run", "deploy.yml", "--repo", REPO, "--ref", "main"])
        limite = time.monotonic() + 2400
        while time.monotonic() < limite:
            runs = self.api("actions/workflows/deploy.yml/runs?event=workflow_dispatch&per_page=100", "workflow_runs")
            novos = [r for r in runs if r["id"] not in antigos and r["head_sha"] == sha and r["head_branch"] == "main"]
            exigir(len(novos) <= 1, "Mais de um dispatch concorrente: conferir o run antes de associar evidência.")
            if novos and novos[0]["status"] == "completed":
                exigir(novos[0]["conclusion"] == "success", "Redeploy falhou; conferir o run sem publicar logs sensíveis.")
                publicacao = self.publicacao(novos[0]["id"])
                self.validar_destino(base, publicacao)
                return publicacao
            print("Aguardando redeploy da mesma versão...", flush=True)
            time.sleep(15)
        raise Falha("Redeploy não terminou em 40 minutos.")


def compactar_comandos(r):
    """Deduplica consultas repetidas, conservando a sequência integral por índices."""
    if "sequencia_comandos_github" in r:
        return
    comandos = r.get("comandos_github", [])
    distintos, indices, sequencia = [], {}, []
    for comando in comandos:
        chave = tuple(comando)
        if chave not in indices:
            indices[chave] = len(distintos)
            distintos.append(comando)
        sequencia.append(indices[chave])
    if len(distintos) < len(comandos):
        r.update(schema=2, comandos_github=distintos, sequencia_comandos_github=sequencia)


def publico(c, r):
    limite = time.monotonic() + 300
    while True:
        try:
            o, _, _ = c.chamar("partida", "/api/status", 200, timeout=15)
            exigir(o and o.get("status") == "UP", "Status da API não está UP.")
            break
        except Falha:
            if time.monotonic() >= limite:
                raise
            time.sleep(5)
    for path in ("/q/health/live", "/q/health/ready"):
        o, _, _ = c.chamar("saude", path, 200)
        exigir(o and o.get("status") == "UP", "Sonda de saúde não está UP.")
    spec, bruto, _ = c.chamar("openapi", "/q/openapi?format=json", 200)
    necessarios = {"/api/camaras", "/api/auth/login", "/api/auth/identidade", "/api/leituras",
                   "/api/camaras/{id}/leituras", "/api/ocorrencias"}
    exigir(isinstance(spec, dict) and necessarios <= set(spec.get("paths", {})), "OpenAPI não contém as rotas funcionais esperadas.")
    r["openapi"] = {"sha256": hashlib.sha256(bruto).hexdigest(), "rotas_conferidas": sorted(necessarios)}
    _, html, _ = c.chamar("swagger", "/q/swagger-ui/", 200)
    exigir(b"swagger" in html.lower(), "Swagger UI não reconhecida.")
    c.chamar("inventario_anonimo", "/api/camaras", 401)
    c.chamar("historico_anonimo", "/api/ocorrencias", 401)


def sessao(o):
    exigir(isinstance(o, dict) and bool(o.get("accessToken")) and bool(o.get("refreshToken")), "Sessão não retornou o par de tokens esperado.")
    return o


def estado(c, token, id_, etapa):
    cam, _, _ = c.chamar(etapa + "_camara", f"/api/camaras/{id_}", 200, token=token)
    leituras, _, _ = c.chamar(etapa + "_leituras", f"/api/camaras/{id_}/leituras?page=0&size=100", 200, token=token)
    ocorrencias, _, _ = c.chamar(etapa + "_ocorrencias", f"/api/ocorrencias?camaraId={id_}&page=0&size=100", 200, token=token)
    for p in (leituras, ocorrencias):
        exigir(isinstance(p, dict) and p.get("totalElements") == 0 and p.get("items") == [], "Fixture nova tem histórico inesperado; interrompido sem ocultar dados.")
    # Só a fixture criada pelo smoke é comparada. Não exportar inventário de terceiros.
    campos = ("id", "codigo", "nome", "unidade", "capacidade", "temperaturaMinima", "temperaturaMaxima",
              "estado", "estadoTermico", "ocupacao", "ativo", "criadoEm", "atualizadoEm")
    return {"camara": {k: cam.get(k) for k in campos}, "leituras": 0, "ocorrencias": 0}


def limpar_fixture_anterior(c, token, pasta, r):
    """Inativa apenas a fixture identificada por evidência íntegra da mesma origem."""
    pasta = Path(pasta)
    bruto = (pasta / "resultado.json").read_bytes()
    exigir(hashlib.sha256(bruto).hexdigest() == (pasta / "resultado.sha256").read_text().strip(),
           "Hash da evidência anterior não confere; limpeza interrompida.")
    anterior = json.loads(bruto)
    exigir(anterior.get("base_url") == c.base, "Fixture anterior pertence a outra origem.")
    id_ = anterior.get("fixture_pendente", {}).get("id")
    exigir(isinstance(id_, int) and id_ > 0, "Evidência anterior não identifica fixture pendente.")
    e = estado(c, token, id_, "limpeza_anterior")
    cam = e["camara"]
    exigir(str(cam.get("codigo", "")).startswith("E89-") and cam.get("nome") == "Fixture ficticia smoke 89",
           "Registro anterior não corresponde à fixture do smoke; limpeza interrompida.")
    c.chamar("inativar_fixture_anterior", f"/api/camaras/{id_}", 204, "DELETE", token=token)
    cam, _, _ = c.chamar("confirmar_fixture_anterior_inativa", f"/api/camaras/{id_}", 200, token=token)
    exigir(cam.get("ativo") is False, "Inativação da fixture anterior não confirmada.")
    r["limpeza_anterior"] = {"id": id_, "inativada": True, "evidencia_sha256": hashlib.sha256(bruto).hexdigest()}


def autenticado(c, gh, r, login, senha, redeploy, limite_503, limpar_de=None):
    s = None
    fixture = None
    encerrada = False
    try:
        if redeploy:
            gh.validar_destino(c.base, r["publicacao_antes"])
        o, _, _ = c.chamar("login", "/api/auth/login", 200, "POST", {"login": login, "senha": senha})
        s = sessao(o)
        ident, _, _ = c.chamar("identidade", "/api/auth/identidade", 200, token=s["accessToken"])
        exigir(ident.get("perfil") == "ADMINISTRADOR", "O smoke de CRUD exige conta ADMINISTRADOR de teste.")
        if limpar_de:
            limpar_fixture_anterior(c, s["accessToken"], limpar_de, r)
        c.chamar("inventario", "/api/camaras?page=0&size=1", 200, token=s["accessToken"])
        codigo = "E89-" + uuid.uuid4().hex[:12].upper()
        dados = {"codigo": codigo, "nome": "Fixture ficticia smoke 89", "unidade": "Laboratorio academico",
                 "capacidade": 100, "temperaturaMinima": 2, "temperaturaMaxima": 8, "estado": "OPERACIONAL"}
        cam, _, _ = c.chamar("criar_camara", "/api/camaras", 201, "POST", dados, s["accessToken"])
        exigir(cam.get("codigo") == codigo and isinstance(cam.get("id"), int) and cam["id"] > 0, "Fixture criada não corresponde à requisição.")
        fixture = cam["id"]
        atualizacao = {k: v for k, v in dados.items() if k != "codigo"}
        atualizacao["capacidade"] = 120
        c.chamar("atualizar_camara", f"/api/camaras/{fixture}", 200, "PUT", atualizacao, s["accessToken"])
        antes = estado(c, s["accessToken"], fixture, "antes_falha")
        r["fixture"] = {"id": fixture, "codigo": codigo, "antes": antes, "limite_503_s": limite_503}
        exigir(antes["camara"]["capacidade"] == atualizacao["capacidade"], "Capacidade 120 não foi persistida na consulta após a atualização.")
        leitura = {"idExterno": codigo + "-L1", "camaraId": fixture, "temperatura": 9,
                   "medidaEm": agora(), "origem": "smoke-publico-89"}
        o, _, tempo = c.chamar("leitura_sem_go", "/api/leituras", 503, "POST", leitura, s["accessToken"])
        r["fixture"].update(leitura_recusada=leitura, tempo_503_s=tempo, prazo_publico_atendido=tempo <= limite_503)
        exigir(isinstance(o, dict) and o.get("type") == "/problemas/analise-indisponivel", "503 não corresponde à falha controlada de análise.")
        depois = estado(c, s["accessToken"], fixture, "depois_falha")
        r["fixture"]["depois_falha"] = depois
        exigir(antes == depois, "Estado/histórico da fixture mudou após leitura recusada.")
        r["sem_go"] = "503 analise-indisponivel; sem leitura/ocorrencia aceita e sem alteração de estado"
        exigir(tempo <= limite_503, "Resposta 503 excedeu o limite de tempo configurado; estado posterior foi conferido.")
        if redeploy:
            r["publicacao_depois"] = gh.redeploy(r["publicacao_antes"]["sha"], c.base)
            exigir(r["publicacao_depois"]["deployment"] != r["publicacao_antes"]["deployment"], "Redeploy não gerou novo evento.")
            # Refresh persistido no banco sobrevive ao processo; JWT de 15 min pode expirar durante a espera.
            antigo = s["refreshToken"]
            o, _, _ = c.chamar("renovar_apos_redeploy", "/api/auth/renovar", 200, "POST", {"refreshToken": antigo})
            s = sessao(o)
            c.chamar("refresh_consumido", "/api/auth/renovar", 401, "POST", {"refreshToken": antigo})
            c.chamar("identidade_apos_redeploy", "/api/auth/identidade", 200, token=s["accessToken"])
            persistido = estado(c, s["accessToken"], fixture, "depois_redeploy")
            exigir(antes == persistido, "Dados da fixture não foram preservados no redeploy.")
            r["fixture"]["depois_redeploy"] = persistido
            r["persistencia"] = "dados e refresh preservados; rotação de refresh e JWT válidos após redeploy"
        else:
            r["persistencia"] = "não executada: usar --redeploy para verificar reinício real"
        c.chamar("inativar_fixture", f"/api/camaras/{fixture}", 204, "DELETE", token=s["accessToken"])
        cam, _, _ = c.chamar("consultar_fixture_inativa", f"/api/camaras/{fixture}", 200, token=s["accessToken"])
        exigir(cam.get("ativo") is False, "Remoção lógica da fixture não confirmada.")
        r["fixture"]["inativada"] = True
        refresh = s["refreshToken"]
        c.chamar("logout", "/api/auth/logout", 204, "POST", {"refreshToken": refresh})
        encerrada = True
        c.chamar("refresh_revogado", "/api/auth/renovar", 401, "POST", {"refreshToken": refresh})
        c.chamar("jwt_apos_logout", "/api/auth/identidade", 200, token=s["accessToken"])
        r["sessao"] = "logout revoga refresh; access JWT continua válido até expirar, conforme desenho"
    finally:
        if s and not encerrada:
            try:
                c.chamar("encerrar_sessao", "/api/auth/logout", 204, "POST", {"refreshToken": s["refreshToken"]})
            except Falha:
                r["encerramento_sessao"] = "não confirmado; verificar no ambiente privado"
        if fixture and not r.get("fixture", {}).get("inativada"):
            r["fixture_pendente"] = {"id": fixture, "acao": "conferir e inativar somente a fixture criada nesta execução"}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--base-url", default="https://friovax.onrender.com")
    p.add_argument("--somente-publico", action="store_true", help="não pede credenciais nem altera dados")
    p.add_argument("--redeploy", action="store_true", help="dispara deploy.yml em main usando seu gh autenticado")
    p.add_argument("--limite-503", type=float, help="orçamento público de ida e volta em segundos; obrigatório no modo autenticado")
    p.add_argument("--limpar-fixture-de", type=Path, help="pasta de evidência anterior íntegra cuja fixture pendente será conferida e inativada")
    p.add_argument("--saida", type=Path)
    args = p.parse_args()
    exigir(not (args.somente_publico and args.redeploy), "--redeploy exige execução autenticada.")
    exigir(not (args.somente_publico and args.limpar_fixture_de), "Limpeza de fixture exige execução autenticada.")
    exigir(args.somente_publico or args.limite_503 is not None and 0 < args.limite_503 <= 30,
           "Informe --limite-503 com orçamento público maior que zero e até 30 segundos.")
    base = origem(args.base_url)
    destino = args.saida or ROOT / "docs/evidencias/sprint-2/smoke-publico" / datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
    destino.mkdir(parents=True, exist_ok=False)
    r = {"schema": 1, "inicio": agora(), "base_url": base, "modo": "publico" if args.somente_publico else "autenticado",
         "chamadas": [], "comandos_github": [], "resultado": "incompleto"}
    codigo = 1
    try:
        gh = GitHub(r)
        r["publicacao_antes"] = gh.atual()
        main_ref = gh.api("commits/main")
        exigir(main_ref["sha"] == r["publicacao_antes"]["sha"], "Última publicação não corresponde à main atual; aguardar CI/deploy.")
        c = Cliente(base, r)
        publico(c, r)
        if not args.somente_publico:
            exigir(sys.stdin.isatty(), "Execute no seu terminal para digitar credenciais sem gravá-las.")
            login = input("Login ADMINISTRADOR de teste da API pública: ").strip()
            with warnings.catch_warnings():
                # Não aceitar o fallback de getpass que ecoaria a senha em terminais incompatíveis.
                warnings.simplefilter("error", getpass.GetPassWarning)
                try:
                    senha = getpass.getpass("Senha (oculta, não será gravada): ")
                except getpass.GetPassWarning:
                    raise Falha("Terminal sem suporte à senha oculta; use PowerShell/Terminal interativo.") from None
            exigir(bool(login and senha), "Login/senha não informados.")
            autenticado(c, gh, r, login, senha, args.redeploy, args.limite_503, args.limpar_fixture_de)
            r["resultado"] = "aprovado" if args.redeploy else "parcial_sem_redeploy"
        else:
            r["resultado"] = "parcial_publico_aprovado"
        codigo = 0
    except (Falha, KeyboardInterrupt) as e:
        r["falha"] = str(e) if isinstance(e, Falha) else "Interrompido pelo operador."
        print(r["falha"], file=sys.stderr)
    except Exception:
        r["falha"] = "Erro inesperado; nenhum corpo de resposta, segredo ou traceback foi exportado."
        print(r["falha"], file=sys.stderr)
    finally:
        r["fim"] = agora()
        compactar_comandos(r)
        conteudo = (json.dumps(r, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
        (destino / "resultado.json").write_bytes(conteudo)
        (destino / "resultado.sha256").write_bytes((hashlib.sha256(conteudo).hexdigest() + "\n").encode("ascii"))
        print(f"Resultado: {r['resultado']}; evidência: {destino}")
    return codigo


if __name__ == "__main__":
    sys.exit(main())

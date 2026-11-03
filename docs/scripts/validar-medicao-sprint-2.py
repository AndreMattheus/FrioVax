#!/usr/bin/env python3
"""Valida evidências locais de medição; não preenche lacunas ou calcula DORA."""

import csv
from datetime import datetime
import hashlib
import json
from pathlib import Path
import re
import sys
from urllib.parse import urlparse


ROOT = Path(__file__).resolve().parents[2]
PASTA = ROOT / "docs/evidencias/sprint-2"
CABECALHOS = {
    "intervalos": "id,issue,etapa,tipo,inicio,fim,autor,fonte,precisao,observacao",
    "eventos": "id,issue,tipo,instante,autor,fonte,precisao",
    "amostra": "issue,codigo,autor,entrada,saida,motivo,fonte",
    "implantacoes": "evento_id,github_deployment_id,sha,ambiente,tipo,resultado,inicio,publicado_em,smoke_em,smoke_fonte,status_fonte,prs,observacao",
    "incidentes": "id,detectado_em,recuperado_em,deployments_causais,implantacao_recuperacao,autor,fonte_deteccao,fonte_recuperacao,observacao",
    "monitoramento": "inicio,fim,ambiente,autor,fonte,cobertura",
}


def exigir(condicao, mensagem):
    if not condicao:
        raise ValueError(mensagem)


def instante(valor):
    exigir("T" in valor, f"Timestamp sem hora: {valor!r}")
    valor = datetime.fromisoformat(valor.replace("Z", "+00:00"))
    exigir(valor.utcoffset() is not None, "Timestamp sem fuso")
    return valor


def periodo(inicio, fim):
    comeco = instante(inicio)
    if fim:
        exigir(instante(fim) >= comeco, "Fim anterior ao início")


def fonte(valor):
    exigir(bool(valor), "Fonte ausente")
    url = urlparse(valor)
    if url.scheme == "https":
        exigir(url.hostname in {"github.com", "api.github.com"}, "Usar fonte pública GitHub ou arquivo local revisado")
        exigir(not url.username and not url.password and not url.query, "Fonte com credenciais ou query string")
        return
    exigir(not url.scheme and not url.netloc, "Fonte local inválida")
    caminho, _, fragmento = valor.partition("#")
    arquivo = (ROOT / caminho).resolve()
    exigir(arquivo.is_relative_to(ROOT) and arquivo.is_file(), f"Fonte local ausente: {caminho}")
    if fragmento:
        exigir(fragmento.startswith("/"), "Usar ponteiro JSON iniciado por /")
        dado = json.loads(arquivo.read_text(encoding="utf-8"))
        for parte in fragmento[1:].split("/"):
            chave = parte.replace("~1", "/").replace("~0", "~")
            dado = dado[int(chave)] if isinstance(dado, list) else dado[chave]


def ler_csv(nome):
    with (PASTA / f"{nome}.csv").open(encoding="utf-8", newline="") as arquivo:
        leitor = csv.DictReader(arquivo)
        exigir(leitor.fieldnames == CABECALHOS[nome].split(","), f"Cabeçalho inválido em {nome}")
        linhas = list(leitor)
    for linha in linhas:
        exigir(None not in linha and all(v is not None for v in linha.values()), f"Colunas inválidas em {nome}")
    return linhas


def validar():
    tabelas = {nome: ler_csv(nome) for nome in CABECALHOS}
    for nome in ("intervalos", "eventos", "incidentes", "implantacoes"):
        campo = "evento_id" if nome == "implantacoes" else "id"
        ids = [r[campo] for r in tabelas[nome]]
        exigir(all(ids) and len(set(ids)) == len(ids), f"IDs vazios/duplicados em {nome}")
    for nome in ("intervalos", "eventos", "amostra"):
        for linha in tabelas[nome]:
            exigir(linha["issue"].isdigit(), f"Issue inválida em {nome}")
            exigir(bool(linha["autor"]), f"Autor ausente em {nome}")
            fonte(linha["fonte"])
    for r in tabelas["intervalos"]:
        exigir(r["tipo"] in {"trabalho", "automacao", "espera", "bloqueio"}, "Tipo de intervalo inválido")
        exigir(bool(r["etapa"]), "Etapa ausente")
        exigir(r["precisao"] in {"observado", "relatado", "estimado"}, "Precisão inválida")
        exigir(r["precisao"] != "estimado" or bool(r["observacao"]), "Estimativa sem explicação")
        periodo(r["inicio"], r["fim"])
    for r in tabelas["eventos"]:
        exigir(r["tipo"] in {"chegada", "inicio", "pedido_revisao", "primeira_revisao", "merge", "conclusao"}, "Evento inválido")
        exigir(r["precisao"] in {"observado", "relatado", "estimado"}, "Precisão de evento inválida")
        instante(r["instante"])
    for r in tabelas["amostra"]:
        entrada = datetime.strptime(r["entrada"], "%Y-%m-%d")
        if r["saida"]:
            exigir(datetime.strptime(r["saida"], "%Y-%m-%d") >= entrada, "Saída anterior à seleção")
            exigir(bool(r["motivo"]), "Substituição sem motivo")
    impl_ids = {r["github_deployment_id"] for r in tabelas["implantacoes"]}
    exigir(len(impl_ids) == len(tabelas["implantacoes"]), "Deployment duplicado; verificar evento real")
    for r in tabelas["implantacoes"]:
        exigir(r["github_deployment_id"].isdigit(), "Deployment sem ID GitHub")
        exigir(re.fullmatch(r"[a-f0-9]{40}", r["sha"]), "SHA inválido")
        exigir(r["ambiente"] == "producao", "Ambiente distinto de producao")
        exigir(r["tipo"] in {"publicacao", "redeploy", "rollback"}, "Tipo de implantação inválido")
        exigir(r["resultado"] in {"success", "failure", "in_progress"}, "Resultado inválido")
        instante(r["inicio"])
        fonte(r["status_fonte"])
        if r["resultado"] == "success":
            exigir(bool(r["publicado_em"] and r["smoke_em"]), "Sucesso sem publicação e smoke")
            periodo(r["inicio"], r["publicado_em"])
            periodo(r["inicio"], r["smoke_em"])
            fonte(r["smoke_fonte"])
        else:
            exigir(not r["publicado_em"], "Publicação bem-sucedida preenchida em evento sem sucesso")
        exigir(r["tipo"] == "publicacao" or bool(r["observacao"]), "Redeploy/rollback sem justificativa")
    for r in tabelas["incidentes"]:
        exigir(bool(r["autor"]), "Incidente sem autor")
        periodo(r["detectado_em"], r["recuperado_em"])
        fonte(r["fonte_deteccao"])
        if r["recuperado_em"]:
            fonte(r["fonte_recuperacao"])
        if not r["deployments_causais"]:
            exigir(bool(r["observacao"]), "Incidente sem causa nem explicação da lacuna")
        for causal in filter(None, r["deployments_causais"].split(";")):
            exigir(causal in impl_ids, f"Deployment causal {causal} não registrado")
        if r["implantacao_recuperacao"]:
            exigir(r["implantacao_recuperacao"] in impl_ids, "Deployment de recuperação ausente")
    for r in tabelas["monitoramento"]:
        exigir(bool(r["autor"] and r["ambiente"]), "Monitoramento sem autor/ambiente")
        exigir(r["cobertura"] in {"efetiva", "parcial", "indisponivel"}, "Cobertura inválida")
        periodo(r["inicio"], r["fim"])
        fonte(r["fonte"])
    cortes = sorted((PASTA / "exportacoes").glob("*/manifesto.json"))
    for arquivo in sorted(PASTA.rglob("*")):
        if arquivo.suffix in {".csv", ".json"}:
            conteudo = arquivo.read_text(encoding="utf-8")
            exigir(not re.search(r"(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|"
                                 r"(?i:authorization\s*[:=]\s*(?:bearer|basic)\s+\S+))", conteudo),
                   f"Possível credencial em {arquivo.relative_to(ROOT)}; revisar sem publicar o valor")
    for caminho in cortes:
        manifesto = json.loads(caminho.read_text(encoding="utf-8"))
        exigir(manifesto["resultado"] == "completo", "Corte incompleto")
        periodo(manifesto["inicio"], manifesto["fim"])
        for consulta in manifesto["consultas"]:
            arquivo = caminho.parent / consulta["arquivo"]
            conteudo = arquivo.read_bytes()
            exigir(hashlib.sha256(conteudo).hexdigest() == consulta["sha256"], f"Hash divergente: {arquivo.name}")
            exigir(len(json.loads(conteudo)) == consulta["registros"], f"Contagem divergente: {arquivo.name}")
            exigir(consulta["paginas"] >= 1, "Consulta sem páginas")
            periodo(consulta["inicio"], consulta["fim"])
    exigir(not list((PASTA / "exportacoes").glob(".incompleto-*")), "Há corte incompleto; investigar fora dos cortes publicados")
    print(f"Validação OK: {sum(map(len, tabelas.values()))} registros CSV; {len(cortes)} corte(s) com hashes íntegros")
    print("Fontes vazias/intervalos invertidos são rejeitados; lacunas e registros em curso continuam explícitos.")


if __name__ == "__main__":
    try:
        validar()
    except (ValueError, KeyError, OSError, json.JSONDecodeError) as erro:
        print(f"Validação falhou: {erro}", file=sys.stderr)
        sys.exit(1)

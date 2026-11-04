#!/usr/bin/env python3
"""Ensaio descartável da ADR 0004. Não aceita destino ou segredo de produção."""

import json
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import time
from urllib.error import URLError
from urllib.request import Request, build_opener, ProxyHandler
import uuid


ROOT = Path(__file__).resolve().parents[2]
FLYWAY_IMAGE = "redgate/flyway:12.0.0"
POSTGRES_IMAGE = "postgres:17-alpine"
HTTP = build_opener(ProxyHandler({}))  # Somente loopback; não enviar pelo proxy corporativo.
REDACTIONS = []


def docker(*args, check=True):
    result = subprocess.run(
        ["docker", *map(str, args)], cwd=ROOT, capture_output=True,
        encoding="utf-8", errors="replace", timeout=1800,
    )
    if check and result.returncode:
        # Diagnóstico limitado, sem argumentos, senha sintética ou connection string.
        detail = result.stderr or result.stdout
        for secret in REDACTIONS:
            detail = detail.replace(secret, "[omitido]")
        detail = re.sub(r"(?:jdbc:)?postgres(?:ql)?://[^\s]+", "[conexao omitida]", detail)
        raise RuntimeError(f"Docker falhou na operação {args[0]}; código {result.returncode}.\n{detail[-2000:]}")
    return result


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def eventually(probe, message):
    deadline = time.monotonic() + 120
    while time.monotonic() < deadline:
        try:
            return probe()
        except (RuntimeError, URLError, TimeoutError, OSError, ValueError):
            time.sleep(1)
    raise RuntimeError(message)


def main():
    identifier = "friovax-ensaio-" + uuid.uuid4().hex[:12]
    network, db, api = (identifier + suffix for suffix in ("-net", "-db", "-api"))
    image = identifier + ":local"
    password = uuid.uuid4().hex  # Válida somente no PostgreSQL temporário, não publicado.
    REDACTIONS.append(password)
    events = []

    def passed(message):
        events.append(message)
        print("OK: " + message, flush=True)

    def sql(statement, database="friovax"):
        return docker("exec", db, "psql", "-U", "ensaio", "-d", database,
                      "-v", "ON_ERROR_STOP=1", "-Atc", statement).stdout.strip()

    def request(base, path, body=None):
        data = None if body is None else json.dumps(body).encode("utf-8")
        req = Request(base + path, data=data, headers={"Content-Type": "application/json"})
        with HTTP.open(req, timeout=5) as response:
            return json.load(response)

    def start_api():
        docker("run", "-d", "--name", api, "--network", network,
               "-p", "127.0.0.1::8080",
               "-e", "QUARKUS_HTTP_HOST=0.0.0.0", "-e", "QUARKUS_HTTP_PORT=8080",
               "-e", "QUARKUS_FLYWAY_MIGRATE_AT_START=false",
               "-e", f"DB_URL=jdbc:postgresql://{db}:5432/friovax",
               "-e", "DB_USER=ensaio", "-e", f"DB_PASSWORD={password}",
               "-e", "TELEMETRY_BASE_URL=http://127.0.0.1:1", image)
        port = docker("port", api, "8080/tcp").stdout.strip().rsplit(":", 1)[1]
        base = "http://127.0.0.1:" + port
        eventually(lambda: request(base, "/api/status"), "API não iniciou sem Go.")
        require(request(base, "/api/status")["status"] == "UP", "Status inesperado.")
        require(request(base, "/q/health/ready")["status"] == "UP", "Banco não está pronto.")
        return base

    with tempfile.TemporaryDirectory(prefix=identifier) as temporary:
        migrations = Path(temporary) / "sql"
        shutil.copytree(ROOT / "api/src/main/resources/db/migration", migrations)

        def flyway(*commands, check=True):
            return docker("run", "--rm", "--network", network,
                          "--mount", f"type=bind,source={migrations},target=/flyway/sql,readonly",
                          "-e", f"FLYWAY_URL=jdbc:postgresql://{db}:5432/friovax",
                          "-e", "FLYWAY_USER=ensaio", "-e", f"FLYWAY_PASSWORD={password}",
                          FLYWAY_IMAGE, "-cleanDisabled=true", *commands, check=check)

        def migrate():
            # Validate isolado rejeita pending no Flyway 12; só permitir antes de migrar.
            flyway("-ignoreMigrationPatterns=*:pending", "validate")
            flyway("migrate")
            flyway("-ignoreMigrationPatterns=", "validate")

        try:
            print("Preparando imagens Docker; o primeiro build pode demorar.", flush=True)
            docker("info")
            docker("build", "-t", image, "api")
            docker("pull", FLYWAY_IMAGE)
            docker("network", "create", network)
            docker("run", "-d", "--name", db, "--network", network,
                   "-e", "POSTGRES_DB=friovax", "-e", "POSTGRES_USER=ensaio",
                   "-e", f"POSTGRES_PASSWORD={password}", POSTGRES_IMAGE)
            eventually(lambda: sql("SELECT 1"), "PostgreSQL temporário não iniciou.")
            migrate()
            passed("Migrações do repositório aplicadas externamente com Flyway 12.0.0.")

            base = start_api()
            passed("Container inicia e saúde/prontidão respondem sem Go e sem migrar no startup.")
            camera = request(base, "/api/camaras", {
                "codigo": "ENSAIO-87", "nome": "Dado fictício", "unidade": "Ensaio local",
                "capacidade": 10, "temperaturaMinima": 2, "temperaturaMaxima": 8,
                "estado": "OPERACIONAL",
            })
            camera_id = camera["id"]
            # Usar a representação persistida (PostgreSQL pode reduzir precisão de timestamps).
            camera = request(base, f"/api/camaras/{camera_id}")
            baseline = sql("SELECT count(*) FROM flyway_schema_history WHERE success")
            docker("exec", db, "pg_dump", "-U", "ensaio", "-d", "friovax",
                   "-Fc", "--no-owner", "--no-acl", "-f", "/tmp/antes.dump")

            # A falha e a migração aditiva existem SOMENTE na cópia temporária.
            versions = [int(re.match(r"V(\d+)__", p.name)[1])
                        for p in migrations.glob("V*__*.sql")]
            candidate = migrations / f"V{max(versions) + 1}__somente_ensaio.sql"
            candidate.write_text("CREATE TABLE ensaio_parcial(id int); SELECT 1/0;\n", encoding="utf-8")
            failed = flyway("migrate", check=False)
            require(failed.returncode != 0, "Migração inválida foi aceita.")
            require(sql("SELECT to_regclass('public.ensaio_parcial') IS NULL") == "t",
                    "DDL parcial não foi revertida.")
            require(sql("SELECT count(*) FROM flyway_schema_history WHERE success") == baseline,
                    "Histórico mudou após migração inválida.")
            require(request(base, f"/api/camaras/{camera_id}") == camera,
                    "Dados anteriores mudaram com a falha.")
            # Não há hook neste ensaio; o retorno não zero é o portão que interrompe a publicação.
            passed("Migração inválida falha, reverte DDL e preserva histórico/dados anteriores.")

            candidate.write_text("CREATE TABLE ensaio_aditivo(id int PRIMARY KEY);\n", encoding="utf-8")
            migrate()
            docker("rm", "-f", api)
            base = start_api()  # Mesma imagem anterior, agora com schema aditivo mais novo.
            require(request(base, f"/api/camaras/{camera_id}") == camera,
                    "Redeploy perdeu ou alterou o registro.")
            require(int(sql("SELECT count(*) FROM flyway_schema_history WHERE success")) == int(baseline) + 1,
                    "Startup reverteu ou alterou o histórico esperado.")
            passed("Redeploy da imagem anterior sobre esquema aditivo mantém registro e histórico.")

            docker("exec", db, "createdb", "-U", "ensaio", "recuperacao")
            docker("exec", db, "pg_restore", "-U", "ensaio", "-d", "recuperacao",
                   "--exit-on-error", "--no-owner", "--no-acl", "/tmp/antes.dump")
            signature = "SELECT row_to_json(c)::text FROM camaras c WHERE codigo='ENSAIO-87'"
            require(sql(signature, "recuperacao") == sql(signature), "Backup não recuperou o registro.")
            require(sql("SELECT count(*) FROM flyway_schema_history WHERE success", "recuperacao") == baseline,
                    "Backup não recuperou o histórico anterior.")
            require(sql("SELECT to_regclass('public.ensaio_aditivo') IS NULL", "recuperacao") == "t",
                    "Recuperação não corresponde ao backup anterior.")
            passed("Backup restaurado em outro banco, sem sobrescrever origem; registro/histórico conferidos.")
            print(f"Ensaio concluído: {len(events)} verificações. Nenhum acesso a Render/Neon.", flush=True)
        finally:
            # Nomes únicos criados nesta execução. Não remove o Compose nem volumes existentes.
            docker("rm", "-f", "-v", api, db, check=False)
            docker("network", "rm", network, check=False)
            docker("image", "rm", image, check=False)


if __name__ == "__main__":
    try:
        main()
    except subprocess.TimeoutExpired:
        raise SystemExit("Tempo limite excedido no Docker; ensaio interrompido.") from None
    except RuntimeError as error:
        raise SystemExit(str(error)) from None

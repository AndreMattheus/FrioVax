"""Executa a suíte Java com um PostgreSQL exclusivo que sobrevive aos perfis de teste."""

import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import time
import uuid


ROOT = Path(__file__).resolve().parents[2]


def verificar():
    maven = shutil.which("mvn")
    if not maven:
        raise RuntimeError("Maven não encontrado; execute pelo mise")
    # Falhar no lint antes de iniciar o banco e rodar os testes.
    subprocess.run([maven, "--batch-mode", "-f", "api/pom.xml", "spotless:check"],
                   cwd=ROOT, check=True)
    nome = "friovax-java-ci-" + uuid.uuid4().hex[:12]
    ambiente = os.environ.copy()
    ambiente["POSTGRES_PASSWORD"] = secrets.token_hex(24)
    criado = False
    try:
        subprocess.run([
            "docker", "run", "--detach", "--name", nome,
            "--publish", "127.0.0.1::5432", "--env", "POSTGRES_PASSWORD",
            "--health-cmd", "pg_isready -U postgres", "--health-interval", "2s",
            "--health-timeout", "3s", "--health-retries", "30", "postgres:17-alpine",
        ], env=ambiente, check=True, timeout=180)
        criado = True
        limite = time.monotonic() + 90
        while True:
            estado = subprocess.run(
                ["docker", "inspect", "--format", "{{.State.Health.Status}}", nome],
                check=True, capture_output=True, text=True, timeout=15).stdout.strip()
            if estado == "healthy":
                break
            if estado == "unhealthy" or time.monotonic() >= limite:
                raise RuntimeError("PostgreSQL de teste não ficou saudável")
            time.sleep(1)
        saida = subprocess.run([
            "docker", "inspect", "--format", '{{json (index .NetworkSettings.Ports "5432/tcp")}}', nome,
        ], check=True, capture_output=True, text=True, timeout=15).stdout
        porta = json.loads(saida)[0]["HostPort"]
        ambiente.update(
            QUARKUS_DATASOURCE_JDBC_URL=f"jdbc:postgresql://127.0.0.1:{porta}/postgres",
            QUARKUS_DATASOURCE_USERNAME="postgres",
            QUARKUS_DATASOURCE_PASSWORD=ambiente["POSTGRES_PASSWORD"],
        )
        subprocess.run([maven, "--batch-mode", "-f", "api/pom.xml", "verify"],
                       cwd=ROOT, env=ambiente, check=True)
        if not (ROOT / "api/target/quarkus-app/quarkus-run.jar").is_file():
            raise RuntimeError("Build não produziu quarkus-run.jar")
    finally:
        if criado:
            # Nome exclusivo desta execução; -v remove apenas seus volumes anônimos.
            subprocess.run(["docker", "rm", "--force", "--volumes", nome],
                           check=True, timeout=60)


if __name__ == "__main__":
    verificar()

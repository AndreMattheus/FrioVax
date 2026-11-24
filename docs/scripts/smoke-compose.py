"""Verifica o Compose em projeto exclusivo e remove apenas seus recursos temporários."""

import json
import os
from pathlib import Path
import secrets
import socket
import subprocess
import tempfile
import time
from urllib.request import urlopen
import uuid


ROOT = Path(__file__).resolve().parents[2]


def portas_livres():
    # Reservar as três juntas evita escolher a mesma porta duas vezes.
    sockets = [socket.socket() for _ in range(3)]
    try:
        for canal in sockets:
            canal.bind(("127.0.0.1", 0))
        return [canal.getsockname()[1] for canal in sockets]
    finally:
        for canal in sockets:
            canal.close()


def smoke():
    projeto = "friovax-smoke-" + uuid.uuid4().hex[:12]
    ambiente = os.environ.copy()
    ambiente.update(zip(("DB_PORT", "API_PORT", "TELEMETRY_HTTP_PORT"),
                        map(str, portas_livres())))
    ambiente.update(DB_NAME="friovax", DB_USER="friovax",
                    DB_PASSWORD=secrets.token_hex(24))
    inicio = time.monotonic()
    with tempfile.TemporaryDirectory(prefix="friovax-smoke-") as temporario:
        # Não carregar o .env de desenvolvimento nem reutilizar seu projeto/volumes.
        env_vazio = Path(temporario) / "compose.env"
        env_vazio.touch()
        comando = ["docker", "compose", "--project-name", projeto,
                   "--env-file", str(env_vazio), "--file", str(ROOT / "docker-compose.yml")]

        def executar(*argumentos, capturar=False, prazo=600):
            return subprocess.run(comando + list(argumentos), cwd=ROOT, env=ambiente,
                                  check=True, timeout=prazo, capture_output=capturar,
                                  text=True, encoding="utf-8")

        try:
            executar("up", "--build", "-d", "--wait", "--wait-timeout", "180")
            saida = executar("ps", "--all", "--format", "json", capturar=True).stdout.strip()
            # Compose pode produzir um array ou um objeto JSON por linha.
            containers = json.loads(saida) if saida.startswith("[") else [
                json.loads(linha) for linha in saida.splitlines() if linha.strip()]
            por_servico = {item["Service"]: item for item in containers}
            for servico in ("db", "api", "telemetry"):
                item = por_servico.get(servico, {})
                if item.get("State") != "running" or item.get("Health") != "healthy":
                    raise RuntimeError(f"Serviço {servico} não está running/healthy")
                print(f"{servico}: running/healthy", flush=True)
            preparo = por_servico.get("auth-init", {})
            if preparo.get("State") != "exited" or preparo.get("ExitCode") != 0:
                raise RuntimeError("auth-init não concluiu com código zero")

            url = f"http://127.0.0.1:{ambiente['API_PORT']}/q/health/ready"
            with urlopen(url, timeout=5) as resposta:
                dados = json.load(resposta)
                if resposta.status != 200 or dados.get("status") != "UP":
                    raise RuntimeError("Readiness da API não retornou HTTP 200 e status UP")
            print("GET /q/health/ready: HTTP 200, status UP", flush=True)
        finally:
            # O nome aleatório é criado nesta execução, nunca recebido do usuário.
            # down -v é restrito a esse projeto; não toca os volumes de desenvolvimento.
            executar("down", "--volumes", "--remove-orphans", prazo=90)
    print(f"Smoke concluído e recursos removidos em {time.monotonic() - inicio:.1f}s", flush=True)


if __name__ == "__main__":
    smoke()

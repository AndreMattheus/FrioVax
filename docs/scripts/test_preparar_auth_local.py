"""Regressões do ambiente local: Bash real, OpenSSL e parser do Compose."""

import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
# No Windows, evitar o bash.exe do WSL: o fluxo documentado usa Git Bash.
GIT_BASH = Path(os.environ.get("ProgramFiles", "C:/Program Files")) / "Git/bin/bash.exe"
BASH = str(GIT_BASH) if os.name == "nt" and GIT_BASH.is_file() else "bash"
VARIAVEIS = ("JWT_PRIVATE_KEY", "JWT_PUBLIC_KEY", "AUTH_DEMO_ADMINISTRADOR",
             "AUTH_DEMO_OPERADOR", "AUTH_DEMO_AUDITOR")


class TestPrepararAuthLocal(unittest.TestCase):
    def setUp(self):
        self.temporario = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporario.cleanup)
        self.base = Path(self.temporario.name).resolve()
        self.repo = self.base / "O'Connor com espacos"
        (self.repo / "docs/scripts").mkdir(parents=True)
        shutil.copyfile(ROOT / "docs/scripts/preparar-auth-local.sh",
                        self.repo / "docs/scripts/preparar-auth-local.sh")
        shutil.copyfile(ROOT / "docker-compose.yml", self.repo / "docker-compose.yml")

    def bash(self, comando, *args, check=True):
        return subprocess.run([BASH, "-euc", comando, "bash", *map(str, args)],
                              cwd=self.repo, check=check, capture_output=True,
                              text=True, encoding="utf-8")

    def preparar(self):
        return self.bash('bash docs/scripts/preparar-auth-local.sh')

    def ler_variaveis(self):
        # Não usar parser dotenv em Python: reproduzir o source do README.
        resultado = self.bash('set -a; source .env.auth; set +a; '
                              'printf "%s\\0" "$JWT_PRIVATE_KEY" "$JWT_PUBLIC_KEY" '
                              '"$AUTH_DEMO_ADMINISTRADOR" "$AUTH_DEMO_OPERADOR" "$AUTH_DEMO_AUDITOR"')
        self.assertEqual("", resultado.stderr)
        return dict(zip(VARIAVEIS, resultado.stdout.rstrip("\0").split("\0")))

    def conferir_chaves(self, variaveis):
        for nome, arquivo in (("JWT_PRIVATE_KEY", "privateKey.pem"),
                              ("JWT_PUBLIC_KEY", "publicKey.pem")):
            esperado = self.repo.as_posix() + "/.secrets/auth/" + arquivo
            self.assertEqual(esperado, variaveis[nome])
            self.assertTrue(Path(variaveis[nome]).is_file())
        # O Maven muda o diretório para api/: as chaves continuam acessíveis.
        (self.repo / "api").mkdir(exist_ok=True)
        self.bash('source .env.auth; cd api; test -r "$JWT_PRIVATE_KEY"; test -r "$JWT_PUBLIC_KEY"')

    def test_gera_e_carrega_caminhos_com_apostrofo_e_espacos(self):
        self.preparar()
        valores = self.ler_variaveis()
        self.conferir_chaves(valores)
        for nome in VARIAVEIS[2:]:
            self.assertRegex(valores[nome], r"^[0-9a-f]{36}$")
        self.bash('openssl pkey -in .secrets/auth/privateKey.pem -pubout > publica.pem; '
                  'cmp publica.pem .secrets/auth/publicKey.pem')

    def test_atualiza_arquivo_antigo_invalido_sem_trocar_chaves_ou_senhas(self):
        self.preparar()
        valores = self.ler_variaveis()
        chaves = {p.name: p.read_bytes() for p in (self.repo / ".secrets/auth").iterdir()}
        env = self.repo / ".env.auth"
        linhas = [linha for linha in env.read_text().splitlines() if not linha.startswith("JWT_")]
        linhas += ["JWT_ISSUER=friovax-api",
                   "JWT_PRIVATE_KEY='/old/O'Connor/privateKey.pem'",
                   "JWT_PUBLIC_KEY='/old/O'Connor/publicKey.pem'"]
        env.write_text("\n".join(linhas) + "\n")
        self.preparar()
        self.preparar()
        self.assertEqual(valores, self.ler_variaveis())
        self.assertEqual(chaves, {p.name: p.read_bytes() for p in (self.repo / ".secrets/auth").iterdir()})
        for nome in VARIAVEIS[:2]:
            self.assertEqual(1, env.read_text().count(nome + "="))

    def test_atualiza_caminhos_apos_mover_repositorio(self):
        self.preparar()
        antes = self.ler_variaveis()
        destino = self.base / "Outro O'Connor"
        self.repo.rename(destino)
        self.repo = destino
        self.preparar()
        depois = self.ler_variaveis()
        self.conferir_chaves(depois)
        for nome in VARIAVEIS[2:]:
            self.assertEqual(antes[nome], depois[nome])

    @unittest.skipIf(os.name == "nt", 'Windows não permite aspas duplas em nomes de pastas')
    def test_source_preserva_metacaracteres_sem_executar_comandos(self):
        destino = self.base / 'O\'Connor $USER ${HOME} $(touch EXECUTOU) `touch EXECUTOU` "aspas" \\barra'
        self.repo.rename(destino)
        self.repo = destino
        self.preparar()
        self.conferir_chaves(self.ler_variaveis())
        self.assertFalse((self.repo / "EXECUTOU").exists())

    def test_compose_le_arquivo_e_usa_chaves_do_container(self):
        if not shutil.which("docker"):
            if os.environ.get("FRIOVAX_TEST_COMPOSE") == "1":
                self.fail("Docker Compose obrigatório nesta execução")
            self.skipTest("Docker Compose não instalado")
        self.preparar()
        valores = self.ler_variaveis()
        resultado = subprocess.run(["docker", "compose", "config", "--format", "json"],
                                   cwd=self.repo, check=True, capture_output=True,
                                   text=True, encoding="utf-8")
        ambiente = json.loads(resultado.stdout)["services"]["api"]["environment"]
        self.assertEqual("/run/secrets/auth/privateKey.pem", ambiente["JWT_PRIVATE_KEY"])
        self.assertEqual("/run/secrets/auth/publicKey.pem", ambiente["JWT_PUBLIC_KEY"])
        for nome in VARIAVEIS[2:]:
            self.assertEqual(valores[nome], ambiente[nome])


if __name__ == "__main__":
    unittest.main()

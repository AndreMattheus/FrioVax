#!/usr/bin/env bash
set -euo pipefail

raiz="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$raiz"
umask 077

# Caminhos absolutos: `mvn -f api/pom.xml quarkus:dev` executa a aplicação em api/,
# não na raiz. No Git Bash, `pwd -W` gera o caminho C:/... que o Java entende.
raiz_nativa="$(pwd -W 2>/dev/null || pwd)"
escrever_caminhos_chaves() {
  printf "JWT_PRIVATE_KEY='%s'\n" "$raiz_nativa/.secrets/auth/privateKey.pem"
  printf "JWT_PUBLIC_KEY='%s'\n" "$raiz_nativa/.secrets/auth/publicKey.pem"
}

if [[ -f .env.auth && -f .secrets/auth/privateKey.pem && -f .secrets/auth/publicKey.pem ]]; then
  env_temporario="$(mktemp .env.auth.XXXXXX)"
  trap 'rm -f "$env_temporario"' EXIT
  grep -vE '^JWT_(PRIVATE|PUBLIC)_KEY=' .env.auth > "$env_temporario" || true
  escrever_caminhos_chaves >> "$env_temporario"
  mv "$env_temporario" .env.auth
  trap - EXIT
  echo "Configuração local já existe; caminhos das chaves atualizados em .env.auth. Chaves e senhas foram mantidas."
  exit 0
elif [[ -e .env.auth || -e .secrets/auth ]]; then
  echo "Configuração local incompleta; confira .env.auth e .secrets/auth antes de gerar novamente." >&2
  exit 1
fi

mkdir -p .secrets
pasta_temporaria="$(mktemp -d .secrets/auth.XXXXXX)"
trap 'rm -rf "$pasta_temporaria"' EXIT
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 \
  -out "$pasta_temporaria/privateKey.pem" 2>/dev/null
openssl pkey -in "$pasta_temporaria/privateKey.pem" -pubout \
  -out "$pasta_temporaria/publicKey.pem"
{
  echo '# Segredos exclusivos do ambiente local; não versionar nem compartilhar.'
  echo 'JWT_ISSUER=friovax-api'
  escrever_caminhos_chaves
  echo 'AUTH_DEMO_ENABLED=true'
  for perfil in ADMINISTRADOR OPERADOR AUDITOR; do
    printf 'AUTH_DEMO_%s=%s\n' "$perfil" "$(openssl rand -hex 18)"
  done
} > "$pasta_temporaria/env.auth"
mv "$pasta_temporaria/env.auth" .env.auth
mv "$pasta_temporaria" .secrets/auth
trap - EXIT

echo 'Chaves e senhas locais criadas em .secrets/auth e .env.auth (arquivos ignorados pelo Git).'
echo 'Para desenvolvimento: set -a; source .env.auth; set +a; mise exec -- mvn -f api/pom.xml quarkus:dev'
echo 'Para Docker Compose: docker compose up --build'

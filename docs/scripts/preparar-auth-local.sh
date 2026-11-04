#!/usr/bin/env bash
set -euo pipefail

raiz="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$raiz"
umask 077

if [[ -f .env.auth && -f .secrets/auth/privateKey.pem && -f .secrets/auth/publicKey.pem ]]; then
  echo "Configuração local já existe; reutilize .env.auth e .secrets/auth. Nenhum arquivo foi substituído."
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
  echo 'JWT_PRIVATE_KEY=.secrets/auth/privateKey.pem'
  echo 'JWT_PUBLIC_KEY=.secrets/auth/publicKey.pem'
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

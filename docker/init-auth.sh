#!/bin/sh
set -eu
umask 077
# env.auth is the completion marker; interrupted initialization is recoverable.
if [ ! -f /auth/env.auth ]; then
    openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out /auth/privateKey.pem 2>/dev/null
    openssl pkey -in /auth/privateKey.pem -pubout -out /auth/publicKey.pem
    {
        echo 'JWT_ISSUER=friovax-api'
        echo 'AUTH_DEMO_ENABLED=true'
        for role in ADMINISTRADOR OPERADOR AUDITOR; do
            printf 'AUTH_DEMO_%s=%s\n' "$role" "$(openssl rand -hex 18)"
        done
    } > /auth/env.auth.tmp
    mv /auth/env.auth.tmp /auth/env.auth
fi
test -s /auth/privateKey.pem
test -s /auth/publicKey.pem
chown -R 10001:10001 /auth
chmod 700 /auth
chmod 600 /auth/*
echo 'Autenticação local pronta; credenciais preservadas no volume auth-data.'

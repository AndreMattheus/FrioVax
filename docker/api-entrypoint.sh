#!/bin/sh
set -eu
# Only Compose opts into local credentials. Public deployments supply their own.
if [ -n "${FRIOVAX_LOCAL_AUTH:-}" ]; then
    set -a
    . "$FRIOVAX_LOCAL_AUTH"
    set +a
fi
exec java -jar /app/quarkus-run.jar "$@"

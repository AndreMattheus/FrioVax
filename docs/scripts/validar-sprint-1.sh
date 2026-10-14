#!/usr/bin/env bash
set -euo pipefail

# Execute a partir da raiz do repositório, após docker compose up --build -d.
# Cria registros de demonstração no banco local e reinicia somente a API.
command -v jq >/dev/null || { echo 'jq é necessário' >&2; exit 1; }
command -v docker >/dev/null || { echo 'Docker Compose é necessário' >&2; exit 1; }

api_url="${API_URL:-http://localhost:8080}"
tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT
response="$tmp_dir/response.json"
suffix="$(printf '%04x%04x' "$RANDOM" "$RANDOM")"

request() {
  local expected="$1" method="$2" path="$3" body="${4:-}" status
  if [[ -n "$body" ]]; then
    status="$(curl -sS -L -o "$response" -w '%{http_code}' -X "$method" \
      -H 'Content-Type: application/json' --data "$body" "$api_url$path")"
  else
    status="$(curl -sS -L -o "$response" -w '%{http_code}' -X "$method" "$api_url$path")"
  fi
  if [[ "$status" != "$expected" ]]; then
    echo "$method $path: esperado HTTP $expected, recebido $status" >&2
    cat "$response" >&2
    exit 1
  fi
}

check() {
  if ! jq -e "$@" "$response" >/dev/null; then
    echo "Resposta inesperada: $(cat "$response")" >&2
    exit 1
  fi
}

request 200 GET '/api/status'
request 200 GET '/q/openapi?format=json'
check '.openapi | startswith("3.")'
request 200 GET '/q/swagger-ui'
echo 'Saúde, OpenAPI e Swagger UI: OK'

request 201 POST '/api/camaras' "{\"codigo\":\"CAM-A-$suffix\",\"nome\":\"Câmara A\",\"unidade\":\"UBS Centro\",\"capacidade\":10,\"temperaturaMinima\":2,\"temperaturaMaxima\":8,\"estado\":\"OPERACIONAL\"}"
cam_a="$(jq -r '.id' "$response")"
check '.ocupacao == 0 and .ativo == true'
request 201 POST '/api/camaras' "{\"codigo\":\"CAM-B-$suffix\",\"nome\":\"Câmara B\",\"unidade\":\"UBS Centro\",\"capacidade\":10,\"temperaturaMinima\":2,\"temperaturaMaxima\":8,\"estado\":\"OPERACIONAL\"}"
cam_b="$(jq -r '.id' "$response")"
request 201 POST '/api/lotes' "{\"codigo\":\"LOT-A-$suffix\",\"imunobiologico\":\"Febre amarela\",\"fabricante\":\"Fabricante inicial\",\"validade\":\"2099-12-31\",\"quantidade\":8,\"camaraId\":$cam_a}"
lot_a="$(jq -r '.id' "$response")"
request 201 POST '/api/lotes' "{\"codigo\":\"LOT-B-$suffix\",\"imunobiologico\":\"Influenza\",\"fabricante\":\"Fabricante inicial\",\"validade\":\"2099-12-31\",\"quantidade\":4,\"camaraId\":$cam_b}"
lot_b="$(jq -r '.id' "$response")"
request 200 GET "/api/camaras/$cam_a"
check '.ocupacao == 8'
echo 'Cadastro de câmaras e lotes, vínculo e ocupação: OK'

request 200 PUT "/api/camaras/$cam_a" '{"nome":"Câmara A editada","unidade":"UBS Centro","capacidade":9,"temperaturaMinima":2,"temperaturaMaxima":8,"estado":"OPERACIONAL"}'
check '.nome == "Câmara A editada" and .capacidade == 9 and .ocupacao == 8'
request 409 PUT "/api/camaras/$cam_a" '{"nome":"Alteração indevida","unidade":"UBS Centro","capacidade":7,"temperaturaMinima":2,"temperaturaMaxima":8,"estado":"OPERACIONAL"}'
check '.type == "/problemas/capacidade-excedida"'
request 200 GET "/api/camaras/$cam_a"
check '.nome == "Câmara A editada" and .capacidade == 9 and .ocupacao == 8'

request 409 PUT "/api/lotes/$lot_a" "{\"imunobiologico\":\"Febre amarela\",\"fabricante\":\"Alteração indevida\",\"validade\":\"2099-12-31\",\"quantidade\":8,\"camaraId\":$cam_b}"
check '.type == "/problemas/capacidade-excedida"'
request 200 GET "/api/lotes/$lot_a"
check --argjson cam "$cam_a" '.camaraId == $cam and .quantidade == 8 and .fabricante == "Fabricante inicial"'
request 200 GET "/api/camaras/$cam_b"
check '.ocupacao == 4'

request 200 PUT "/api/camaras/$cam_b" '{"nome":"Câmara B","unidade":"UBS Centro","capacidade":12,"temperaturaMinima":2,"temperaturaMaxima":8,"estado":"OPERACIONAL"}'
request 200 PUT "/api/lotes/$lot_a" "{\"imunobiologico\":\"Febre amarela\",\"fabricante\":\"Fabricante revisado\",\"validade\":\"2099-12-31\",\"quantidade\":8,\"camaraId\":$cam_b}"
check --argjson cam "$cam_b" '.camaraId == $cam and .fabricante == "Fabricante revisado"'
request 200 GET "/api/camaras/$cam_a"
check '.ocupacao == 0'
request 200 GET "/api/camaras/$cam_b"
check '.ocupacao == 12'
echo 'Edição, capacidade excedida sem gravação parcial e transferência: OK'

request 200 GET '/api/camaras?unidade=ubs%20centro&page=0&size=1'
check '.page == 0 and .size == 1 and .totalElements == 2 and (.items | length) == 1'
request 200 GET '/api/camaras?unidade=ubs%20centro&page=1&size=1'
check '.page == 1 and .totalElements == 2 and (.items | length) == 1'
request 200 GET "/api/lotes?imunobiologico=febre&camaraId=$cam_b&validadeDe=2099-01-01&validadeAte=2099-12-31&page=0&size=1"
check --argjson id "$lot_a" '.totalElements == 1 and .items[0].id == $id'
echo 'Filtros e paginação: OK'

request 204 DELETE "/api/camaras/$cam_a"
request 200 GET "/api/camaras/$cam_a"
check '.ativo == false'
request 200 GET '/api/camaras?ativo=false'
check --argjson id "$cam_a" 'any(.items[]; .id == $id)'
request 409 DELETE "/api/camaras/$cam_b"
check '.type == "/problemas/estado-incompativel"'
echo 'Inativação e bloqueio de câmara ocupada: OK'

docker compose restart api >/dev/null
ready=false
for _ in {1..30}; do
  if curl -fsS "$api_url/api/status" >/dev/null 2>&1; then ready=true; break; fi
  sleep 1
done
if [[ "$ready" != true ]]; then echo 'API não ficou pronta após reinício' >&2; exit 1; fi
request 200 GET "/api/camaras/$cam_a"
check '.ativo == false'
request 200 GET "/api/camaras/$cam_b"
check '.ocupacao == 12'
request 200 GET "/api/lotes/$lot_a"
check --argjson cam "$cam_b" '.camaraId == $cam and .fabricante == "Fabricante revisado"'
echo 'Persistência após reinício da API: OK'

request 204 DELETE "/api/lotes/$lot_a"
request 200 GET "/api/camaras/$cam_b"
check '.ocupacao == 4'
request 204 DELETE "/api/lotes/$lot_b"
request 200 GET "/api/camaras/$cam_b"
check '.ocupacao == 0'
request 204 DELETE "/api/camaras/$cam_b"
request 200 GET "/api/camaras/$cam_b"
check '.ativo == false'
echo 'Inativação de lotes, liberação de capacidade e inativação final: OK'
echo "IDs para conferência posterior: câmaras $cam_a e $cam_b; lotes $lot_a e $lot_b"

# Roteiro de validação da Sprint 1

Este roteiro verifica US01 (câmaras), US02 (lotes e alocação) e US03 (inventário) contra a API e o PostgreSQL do Docker Compose. Os campos, filtros e códigos HTTP estão no [contrato da API](contrato-api.md); o [README](../README.md#como-rodar) explica o ambiente, as variáveis e os testes.

## Preparação em clone limpo

Pré-requisitos: Git, mise, Docker com Compose, Bash, `curl` e `jq`. O banco local recebe dados de demonstração. Execute na raiz do clone:

```bash
git clone https://github.com/AndreMattheus/FrioVax.git
cd FrioVax
mise trust
mise install
mise run ci
docker compose up --build -d
docker compose ps
curl -fsS http://localhost:8080/api/status
curl -fsS http://localhost:8081/health
```

Espere a API ficar pronta antes das chamadas HTTP. `mise run ci` executa `mvn verify` sem filtros (testes unitários, HTTP, arquitetura e integração PostgreSQL via Dev Services), além de formatação, `go vet`, testes e build Go. Para uma verificação sem Docker, use `mise run test-unit` e `mise run test-http`. A interface Swagger UI fica em <http://localhost:8080/q/swagger-ui>; a especificação gerada fica em <http://localhost:8080/q/openapi>.

O Compose aplica as migrações Flyway ao iniciar a API. Confira o histórico no banco:

```bash
docker compose exec db psql -U friovax -d friovax -c 'SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;'
```

Altere `friovax` no comando se configurou `DB_USER` ou `DB_NAME` no `.env`.

## Roteiro HTTP

Na raiz do clone, execute:

```bash
bash docs/scripts/validar-sprint-1.sh
```

O script usa `API_URL=http://localhost:8080` por padrão, cria códigos aleatórios para permitir novas execuções e deixa os registros de demonstração inativos ao terminar. Ele interrompe na primeira resposta inesperada. As etapas e resultados esperados são:

| Etapa | Verificação |
|---|---|
| Saúde e documentação | `/api/status`, `/q/openapi?format=json` e Swagger UI respondem `200`. |
| Cadastro e relacionamento | Duas câmaras e dois lotes são criados com `201`; a ocupação da câmara A passa a 8 doses. |
| Edição de câmara | `PUT` altera nome e capacidade para 9; reduzir para 7 retorna `409` e mantém nome, capacidade e ocupação anteriores. |
| Capacidade e atomicidade | Mover 8 doses para a câmara B, que já possui 4 de 10, retorna `409` com `/problemas/capacidade-excedida`. `GET` confirma que fabricante, câmara de origem e ocupação das duas câmaras não foram alterados. |
| Edição e transferência válidas | A capacidade de B sobe para 12; o `PUT` do lote altera fabricante e câmara. A ocupação fica A=0 e B=12. |
| Filtros e paginação | Filtro de unidade sem diferenciar caixa, páginas 0 e 1 com `size=1`, e filtros combinados de imunobiológico, câmara e intervalo de validade retornam contagens e itens esperados. |
| Inativação | Câmara A vazia é inativada com `204` e aparece em `ativo=false`; inativar B ocupada retorna `409`. |
| Persistência | `docker compose restart api` reinicia a aplicação sem remover o volume; `GET` confirma a câmara A inativa, ocupação de B=12 e a transferência do lote. |
| Liberação de capacidade | Inativar os lotes reduz a ocupação de B de 12 para 4 e depois 0; B pode então ser inativada. |

O script reinicia somente a API. Para verificar persistência também após parar todos os contêineres, rode `docker compose down`, depois `docker compose up --build -d` e consulte um dos IDs exibidos ou registrados na execução. **Não use `docker compose down -v` nesse teste**: `-v` remove o volume do PostgreSQL.

## Execução comprovada em clone limpo

Em **14/10/2026**, a aplicação foi clonada de `https://github.com/AndreMattheus/FrioVax.git` em um diretório temporário, no commit `048dce6` da `main`; `git status --porcelain` não apontou alterações rastreadas. Ambiente: macOS arm64, mise 2026.9.0, Docker 29.6.2 e Compose 5.3.1. O volume local do Compose já existia, portanto esta execução comprova um **clone novo do código**, mas não um volume PostgreSQL recém-criado; os testes de migração no `mise run ci` usam o banco temporário do Dev Services.

| Comando/verificação | Resultado observado |
|---|---|
| `mise trust`, `mise install`, `mise run ci` | Ferramentas instaladas; Maven `BUILD SUCCESS`; 472 testes Java, 0 falhas, 0 erros, 0 ignorados; formatação, `go vet`, teste e build Go concluídos. |
| `docker compose up --build -d`, `docker compose ps` | API, telemetria e PostgreSQL iniciaram; banco e telemetria ficaram saudáveis. |
| Saúde, Swagger UI e Flyway | `/api/status`, `/health` da telemetria e Swagger UI responderam `200`; `flyway_schema_history` mostrou V1 e V2 com `success=true`. |
| Roteiro HTTP desta alteração, executado contra o clone | Todas as etapas acima passaram, inclusive `409` sem alteração parcial, filtros, paginação, inativação e persistência após `docker compose restart api`. O script ainda não existia na `main` clonada, então foi chamado a partir desta worktree com o diretório de trabalho no clone. |
| `docker compose down` e `docker compose up -d` | Após recriar os contêineres sem remover o volume, as câmaras 1 e 2 e os lotes 1 e 2 permaneceram consultáveis e inativos, com ocupação zero e vínculo dos lotes com a câmara 2. |

Ao terminar a validação local, use `docker compose down` para parar os serviços e preservar o volume.

# Validação de containers e clone limpo — #84

Execução local em 18/11/2026, Windows e Docker Desktop 4.94.0, Engine 29.8.2.
Base: `89ed2539ec6e4c089c9053a515446224e0891e36` mais as alterações da #84.

Foi exportado `git archive HEAD` para uma pasta temporária, acrescentando somente
o Compose, Dockerfile e scripts Docker novos desta alteração. Isso reproduz os
arquivos de um clone limpo sem trazer `.env.auth`, `.secrets`, `target`, `bin`
ou caches do host. Não foi feito um novo clone remoto nem um deploy público.
Todos os builds e testes Java/Go abaixo executaram dentro do Docker.

Projeto exclusivo: `friovax-84-20261118`. Volumes novos criados pelo Compose:
`friovax-84-20261118_db-data` e `friovax-84-20261118_auth-data`.
Portas do host: banco 15484, API 18084 e Go HTTP 18184, em 127.0.0.1.

## Resultados observados

| Ensaio | Resultado |
|---|---|
| `docker compose -p friovax-84-20261118 up --build -d --wait --wait-timeout 180` | Build dos três artefatos sem linguagens do host; API, db e telemetry saudáveis; auth-init saiu com código 0 |
| Geração Java | Maven registrou geração Protobuf e compilação dentro do build, sem stub manual |
| `/q/health/live` | HTTP 200, `UP` |
| `/q/health/ready` | HTTP 200, `UP`, com `Database connections health check` |
| Go `/health` | HTTP 200, `UP` |
| Go parado + `up -d --force-recreate --wait api` | API iniciou saudável; Go permaneceu parado; log confirmou `Profile prod activated` |
| Imagem com configuração pública, sem `FRIOVAX_LOCAL_AUTH`, sem provisionamento demo, Go `go-ausente.invalid` | Processo iniciou em `prod`; prontidão HTTP 200/UP com banco acessível (sondagem IPv4) |
| `restart telemetry` | Go retornou UP; ID do contêiner da API permaneceu igual |
| `stop db` | Prontidão HTTP 503/DOWN; vivacidade HTTP 200/UP |
| `start db` + espera de saúde | Prontidão voltou a UP |
| Logins locais | Administrador, operador e auditor autenticaram com os respectivos perfis |
| Nova imagem + `down` e `up -d --wait` | Banco e autenticação preservados; todos os serviços saudáveis |
| Comparação de segredos | Hashes da chave privada e de env.auth iguais antes/depois; valores secretos não publicados |
| Comparação de persistência | GET da câmara ID 1 igual antes/depois, incluindo datas |
| Usuário dos runtimes | API e Go executam com UID 10001; JRE final sem Maven/javac |

Resumo de `compose ps -a` da primeira subida:

```text
api         Up (healthy)       127.0.0.1:18084->8080/tcp
db          Up (healthy)       127.0.0.1:15484->5432/tcp
telemetry   Up (healthy)       127.0.0.1:18184->8081/tcp
auth-init   Exited (0)
```

Na recriação, uma label de validação foi acrescentada com `docker build --label`
para produzir uma identidade de imagem nova, reaproveitando as camadas compiladas.
A imagem inicial foi `sha256:340de8c67bceb2af7d8342ec0422fca1f44de8887ad8d3414f65173c5d7c3ef1`.
A primeira substituição foi `sha256:d61fa84d837a60c1921ab27302d05b5db437886c2d0b9f8cc41b3304e34b1946`.
A repetição com baseline GET usou `sha256:e16f4988c7349db066963d06f3b58c190a368fe3469b83fe3d18aea03d2d5bf2`.

A referência de persistência foi obtida por GET: a resposta inicial do POST
carregava nanosegundos e offset local, enquanto o PostgreSQL armazena microssegundos
e a leitura retorna UTC. Essa diferença de representação não foi atribuída ao
reinício; o ensaio foi repetido comparando GET antes/depois. A câmara permaneceu
com código `VALIDACAO-84`, capacidade 10, faixa 2–8 e datas persistidas
`2026-11-18T15:39:07.570519Z`.

## Testes e limites

- Maven `verify` no estágio de build da API, conectado a um PostgreSQL separado
  `friovax-84-test-db`: **772 testes, zero falhas, erros ou ignorados; BUILD SUCCESS**.
  O banco de testes não usou o volume do ensaio nem volumes de desenvolvimento.
- `go test ./...` no estágio de build do Go: domínio, adaptador gRPC e servidor passaram.
- Regressão da preparação de autenticação no host: 4 testes passaram, 1 ignorado
  pela restrição de nomes de diretórios no Windows. Incluiu configuração Compose
  sem `.env.auth`.
- Regressão Python da publicação: 19 testes passaram.
- `git diff --check`: sem erros.

`mise run ci` não pôde ser executado porque mise não está no PATH deste host.
Os testes Java foram executados sem socket Docker: a tentativa inicial de montar
o socket foi rejeitada pela revisão automática; a alternativa usou somente JDBC
na rede isolada e passou. Não houve necessidade de conceder acesso ao daemon
ao contêiner de testes.

CI remoto, PR com `Closes #84`, aprovação de @leonardonadson, integração e datas
reais no Project continuam pendentes. A simulação com configuração pública é local
e não substitui evidência de publicação remota.

Ao terminar, os contêineres e a rede do projeto foram encerrados. Os dois volumes
novos foram removidos após conferir o rótulo `com.docker.compose.project`, assim
como as tags exclusivas de teste. Nenhum volume ou dado de desenvolvimento foi
apagado. Os logs auxiliares ficaram na pasta temporária local do ensaio.

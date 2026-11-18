# Containers, saúde e clone limpo — #84

Na raiz de um clone, execute `docker compose up --build`. Apenas Docker com
Compose é necessário depois de obter os arquivos. A primeira construção precisa
de internet. O Maven gera Java a partir de `protos/` dentro do build; o Go usa
os gerados versionados em `services/telemetry/gen/`. Nenhum stub ou saída local
de compilação é copiado manualmente. Os runtimes contêm JRE ou binário Go,
sem compiladores; ambos os processos executam como usuário 10001.

`auth-init` é um serviço de preparação: terminar com código 0 é esperado.
Os três serviços permanentes devem aparecer como `healthy` em `docker compose ps`.
A API espera `db: service_healthy` e `auth-init: service_completed_successfully`.
Não há dependência da API sobre `telemetry`.

| Serviço | Porta no host (padrão) | Configuração | Verificação |
|---|---|---|---|
| API | 127.0.0.1:8080 | `API_PORT` | `/q/health/ready` |
| Go HTTP | 127.0.0.1:8081 | `TELEMETRY_HTTP_PORT` | `/health` |
| Go gRPC | somente rede Compose:9090 | `TELEMETRY_GRPC_HOST=telemetry`, porta 9090 na API | gRPC Health Check |
| PostgreSQL | 127.0.0.1:5432 | `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | `pg_isready` |

Dentro da rede, as portas permanecem 8080, 8081, 9090 e 5432 mesmo quando as
portas do host mudam. O banco local usa `friovax` como nome, usuário e senha
por padrão. Isso é configuração de desenvolvimento. Alterar essas variáveis
não modifica credenciais de um banco já inicializado.

`GET http://localhost:8080/q/health/live` verifica vivacidade do processo HTTP.
`GET http://localhost:8080/q/health/ready` inclui a conexão PostgreSQL pelo
health check de Agroal/Quarkus. Banco indisponível deve produzir HTTP 503 na
prontidão, mantendo HTTP 200 na vivacidade. Nenhum desses checks depende do Go.
O `/health` do Go acompanha a admissão do servidor e o encerramento; o health
gRPC também expõe o estado do serviço de análise.

## Segurança e persistência locais

`auth-init` gera um par RSA 3072 e senhas aleatórias de 36 caracteres para
`administrador`, `operador` e `auditor`. Os arquivos ficam no volume `auth-data`,
com permissão 600 e diretório 700; a API monta esse volume somente para leitura.
Para consultar suas senhas localmente:

```sh
docker compose exec api cat /run/secrets/auth/env.auth
```

Não publicar essa saída. Reiniciar, recriar contêineres ou construir outra imagem
preserva chaves, senhas e registros, pois `auth-data` e `db-data` são volumes
por projeto. `docker compose down` também preserva os volumes. Não usar
`down -v` no ambiente de desenvolvimento.

O Compose não lê mais `.env.auth` nem `.secrets/auth` do host. Esses arquivos
continuam disponíveis para `quarkus:dev`. Em um banco antigo, contas existentes
conservam suas senhas anteriores; as novas senhas do volume não as substituem.
Use as credenciais originais nesse caso. A chave nova invalida JWTs anteriores;
faça login novamente. Nenhum volume antigo é removido ou migrado automaticamente.

A preparação e o provisionamento demonstrativo são exclusivos do Compose local.
A imagem final da API, usada no ambiente público, não gera chaves nem habilita
contas: sem `FRIOVAX_LOCAL_AUTH`, recebe `JWT_PRIVATE_KEY`, `JWT_PUBLIC_KEY`,
`JWT_ISSUER` e as variáveis de banco do ambiente. O perfil empacotado é `prod`.
O Compose habilita Flyway na inicialização; o ambiente público mantém o processo
de migração documentado em [implantação](implantacao.md).

## Ensaio isolado

Use uma cópia limpa dos arquivos versionados e um nome de projeto exclusivo.
Não copie `.env*`, `.secrets`, `target` ou `bin` da máquina. Em PowerShell:

```powershell
$env:DB_PORT = '15484'
$env:API_PORT = '18084'
$env:TELEMETRY_HTTP_PORT = '18184'
docker compose -p friovax-validacao-84 up --build -d --wait --wait-timeout 180
docker compose -p friovax-validacao-84 ps -a
curl.exe http://localhost:18084/q/health/live
curl.exe http://localhost:18084/q/health/ready
curl.exe http://localhost:18184/health
```

Em Bash, use `export DB_PORT=15484 API_PORT=18084 TELEMETRY_HTTP_PORT=18184`
e `curl` no lugar de `curl.exe`. Os volumes serão
`friovax-validacao-84_db-data` e `friovax-validacao-84_auth-data`.

1. Faça login com uma senha do volume, cadastre uma câmara e guarde seu ID.
   Consulte-a por GET para guardar a referência persistida, incluindo datas na
   precisão de microssegundos do PostgreSQL.
2. Execute `docker compose -p friovax-validacao-84 stop telemetry` e depois
   `docker compose -p friovax-validacao-84 up -d --force-recreate --wait api`.
   Confirme Go parado e API pronta, no perfil `prod`, com banco disponível.
3. Execute `docker compose -p friovax-validacao-84 restart telemetry`.
   Confira `/health`, `/q/health/live` e `/q/health/ready`.
4. Pare somente o banco: `docker compose -p friovax-validacao-84 stop db`.
   Confirme `/q/health/ready` com 503 e `/q/health/live` com 200. Inicie o banco
   novamente com `docker compose -p friovax-validacao-84 start db` e aguarde UP.
5. Execute `docker compose -p friovax-validacao-84 down` e
   `docker compose -p friovax-validacao-84 up --build -d --wait`.
   Faça login novamente e consulte a câmara pelo ID: campos e datas devem permanecer.
6. Encerre com `docker compose -p friovax-validacao-84 down`.
   Remova apenas os dois volumes descartáveis explicitamente, se desejar:
   `docker volume rm friovax-validacao-84_db-data friovax-validacao-84_auth-data`.

Registre resultados reais em [evidências da #84](evidencias/sprint-2/validacao/containers-84.md).

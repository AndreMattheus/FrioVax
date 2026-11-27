# FrioVax

Resumo operacional: `GET /api/resumo`, com cache local de 30 s, invalidação após
commit e métricas Prometheus. Veja [contrato, política de acesso e demonstração](docs/resumo.md).

Plataforma para gestão de câmaras de conservação, lotes de imunobiológicos e monitoramento de condições térmicas.

## Equipe

**Nome do grupo:** FrioVax
**Coorte:** B — apresentações online

| Integrante | Matrícula | GitHub | Papel principal |
|---|---:|---|---|
| Arthur Feijo de Medeiros             | 20240012792 | [`@arthurFeijo`](https://github.com/arthurFeijo)          | Qualidade e DevOps       |
| Bianca Maciel Medeiros | 20230044970 | [`@biancammedeiros`](https://github.com/biancammedeiros) | Product Owner e domínio |
| Leonardo Nadson Oliveira de Medeiros | 20240028925 | [`@leonardonadson`](https://github.com/leonardonadson) | Scrum Master e processo |
| Matheus Andre Souza Cirilo | 20240030941 | [`@AndreMattheus`](https://github.com/AndreMattheus) | Arquitetura e serviço Go |

Todos atuam como desenvolvedores. Os papéis indicam responsabilidades de coordenação e não propriedade exclusiva de código.

## Integração entre disciplinas

Este repositório contém o produto comum de:

- **DIM0547 — Desenvolvimento de Sistemas Web II:** backend Java/Quarkus, serviço Go e infraestrutura;
- **DIM0510 — Processos de Software:** acordo de trabalho, Kanban, métricas, retrospectivas e melhoria do processo.

## Documentação

- [Proposta integrada](docs/proposta.md)
- [Backlog inicial](docs/backlog.md)
- [Acordo de processo](docs/processo.md)
- [Checklist da Sprint 0](docs/checklist-sprint-0.md)
- [Checklist da Sprint 1](docs/checklist-sprint-1.md)
- [Retrospectiva da Sprint 1 — reunião de 15/10](docs/retrospectiva-01.md)
- [Métricas de fluxo da Sprint 1](docs/metricas-sprint-1.md)
- [Proteção da main e provas de bloqueio](docs/evidencias/sprint-2/protecao-main.md)
- [CI, formatação Java e smoke do Compose](docs/ci.md)
- [Contrato da API — Câmaras e Lotes](docs/contrato-api.md)
- [Contrato Protobuf — Telemetria e geração Java/Go](protos/README.md)
- [Roteiro de validação da Sprint 1](docs/validacao-sprint-1.md)
- [ADR 0001 — Arquitetura em camadas](docs/decisoes/0001-arquitetura-em-camadas.md)
- [ADR 0002 — Domínio independente e aplicação por interfaces](docs/decisoes/0002-dominio-independente-e-aplicacao-por-interfaces.md) — substitui o ADR 0001
- [ADR 0003 — Serviço Go de telemetria e regras térmicas](docs/decisoes/0003-servico-go-telemetria.md) — decisão da Sprint 2 revisada
- [Roteiro do vídeo de Web II](docs/roteiro-video-web2.md)
- [Roteiro do vídeo de Processos](docs/roteiro-video-processos.md)
- [Roteiro do vídeo único da Sprint 1 — Web II e Processos](docs/roteiro-video-sprint-1.md)
- [Registro de uso de IA](docs/uso-de-ia.md)
- **GitHub Project:** [FrioVax Project — Board Principal](https://github.com/users/AndreMattheus/projects/1)
- **Issues do backlog:** [histórias US01 a US10](https://github.com/AndreMattheus/FrioVax/issues)
- **Vídeos da Sprint 1:** https://www.youtube.com/watch?v=Jerz6RtwuYE

## Estrutura do projeto

```text
.
├── api/                    # API Java 25/Quarkus
├── services/telemetry/      # Microsserviço Go de telemetria
├── protos/                 # Contrato Protobuf compartilhado entre Java e Go
├── docs/                   # Contrato da API, arquitetura e documentos do projeto
├── .github/workflows/      # CI dos dois stacks
├── docker-compose.yml
└── mise.toml
```

A API Java concentra os casos de uso, as regras de negócio e a persistência em PostgreSQL. Sua organização em `api`, `aplicacao`, `dominio` e `persistencia` está descrita no ADR 0002. O contrato Protobuf gera código Go versionado e classes Java durante o build Maven. O serviço Go expõe a análise térmica por gRPC, com saúde HTTP/gRPC e reflection; a API possui um adaptador cliente separado dos casos de uso.

A [ADR 0003](docs/decisoes/0003-servico-go-telemetria.md) define que Java envie janelas de leituras para análise concorrente no Go, sem banco compartilhado nem contadores entre chamadas. Go devolve resultados por candidata. A integração dos casos de uso Java para revalidar dados e confirmar leituras, ocorrências e estado térmico em transações curtas continua nos cards correspondentes da sprint. A exposição gRPC não confirma esses efeitos oficiais.

## Como rodar

Para executar o sistema em um clone limpo, basta Docker Desktop (ou Docker Engine com Compose) em execução:

```bash
docker compose up --build
```

Não exige Java, Go, Maven, mise, Bash nem OpenSSL no host. O serviço transitório
`auth-init` gera chaves RSA e senhas aleatórias no volume `auth-data`; `db`, `api`
e `telemetry` ficam saudáveis. Para consultar as credenciais locais, execute
`docker compose exec api cat /run/secrets/auth/env.auth`. Veja [portas, segurança,
saúde e validação isolada](docs/containers.md). `docker compose down` conserva os
volumes de banco e autenticação para a próxima subida.

Para desenvolver fora dos contêineres, instale Git, OpenSSL e
[mise](https://mise.jdx.dev/). Execute os comandos a partir da raiz do repositório;
as versões estão definidas em `mise.toml`. A primeira instalação precisa de internet
para baixar ferramentas, dependências e imagens.

Em um clone novo:

```bash
git clone https://github.com/AndreMattheus/FrioVax.git
cd FrioVax
mise trust
mise install
mise run build
mise run test
```

`mise trust` confia nas tarefas definidas neste repositório; revise `mise.toml` antes de executá-lo. `mise run test` usa um PostgreSQL temporário iniciado pelo Quarkus Dev Services e precisa de Docker, mas não do Compose. Para verificar também o contrato, os gerados Go, a formatação, a análise estática, o build Go, o detector de condições de corrida, os scripts Python e o smoke do Compose, execute `mise run ci`. Veja os [pré-requisitos e comandos do CI](docs/ci.md).

O Maven gera as classes Java diretamente de `protos/`, em `api/target/generated-sources/grpc/`, antes de compilá-las. Não é necessário executar Buf para compilar Java. Os comandos e a separação entre código gerado e domínio estão no [guia dos contratos](protos/README.md).

Para subir a API, o serviço Go e o banco pelo atalho do mise:

```bash
mise run up
```

Esse comando mantém o Compose no terminal. Aguarde os serviços ficarem prontos; para encerrar, use `Ctrl+C` e `docker compose down`. O volume do banco permanece para a próxima subida. O [roteiro de validação](docs/validacao-sprint-1.md) exercita a API e confere os dados após reiniciar o serviço.

### Build Docker da API

Execute na raiz do repositório:

```bash
docker compose build api
# Alternativa sem Compose:
docker build -f api/Dockerfile -t friovax-api .
```

O contexto de build é o conjunto de arquivos que o Docker pode acessar. No Compose,
`context: .` disponibiliza a raiz, incluindo `api/` e `protos/`;
`dockerfile: api/Dockerfile` indica a receita de construção. Usar apenas `api/`
como contexto impediria copiar o contrato que está na pasta irmã `protos/`.

```text
Repositório (contexto .)       Estágio de build da imagem
api/pom.xml e api/src/    ->   /workspace/api/
protos/                  ->   /workspace/protos/
```

Assim, o caminho `../protos` usado pelo Maven funciona no computador e no Docker.
O `.dockerignore` da raiz exclui histórico Git, arquivos locais de ambiente e
saídas de compilação. O Dockerfile copia somente as entradas necessárias; a imagem
final recebe o aplicativo compilado, sem Maven ou fontes do projeto.

Em outro terminal, confira as rotas de saúde:

```bash
curl http://localhost:8080/api/status
curl http://localhost:8080/q/health/live
curl http://localhost:8080/q/health/ready
curl http://localhost:8081/health
```

Com a API no ar, a documentação das rotas fica disponível em:

| Recurso | Endereço |
|---|---|
| Swagger UI | <http://localhost:8080/q/swagger-ui> |
| Especificação OpenAPI (YAML) | <http://localhost:8080/q/openapi> |
| Especificação OpenAPI (JSON) | <http://localhost:8080/q/openapi?format=json> |

A API pública está em <https://friovax.onrender.com>. A documentação da mesma
publicação está em [Swagger UI](https://friovax.onrender.com/q/swagger-ui/)
e [OpenAPI JSON](https://friovax.onrender.com/q/openapi?format=json).
[Liveness](https://friovax.onrender.com/q/health/live) e
[readiness do banco](https://friovax.onrender.com/q/health/ready) são públicas;
inventário, histórico e demais operações exigem autenticação. O ambiente Render
publica somente a API Java; os endpoints dependentes de análise devem falhar
de forma controlada enquanto Go estiver inacessível. O plano gratuito pode
exigir partida a frio. Ver [resultados e reprodução do smoke público](docs/evidencias/sprint-2/smoke-publico.md).

| Comando | Resultado |
|---|---|
| `mise run build` | Compila Java e Go |
| `mise run test` | Executa os testes dos dois stacks |
| `mise run test-unit` | Executa testes Java de domínio, aplicação e suporte sem banco |
| `mise run test-http` | Executa testes HTTP de câmaras, lotes e documentação em memória, sem banco |
| `mise run proto-lint` | Valida o contrato com Buf |
| `mise run proto-generate` | Regenera os arquivos Go a partir do contrato |
| `mise run proto-check` | Valida o contrato e confere os gerados Go contra o Git |
| `mise run lint` | Verifica Buf, Spotless, `gofmt` e `go vet` |
| `mise run test-race` | Executa Go com detector de condições de corrida; no Windows usa Docker |
| `mise run smoke` | Constrói o Compose isolado, verifica saúde/readiness e remove seus recursos temporários |
| `mise run ci` | Reproduz as verificações Java, Go, contrato, scripts Python e smoke do CI |
| `mise run up` | Sobe os serviços com Docker Compose e acompanha os logs |

### Autenticação e sessões

O Compose usa credenciais próprias no volume `auth-data`, sem ler `.env.auth`.
Para desenvolvimento com Quarkus diretamente no host, execute
`bash docs/scripts/preparar-auth-local.sh` na raiz para gerar uma chave RSA
exclusiva e três senhas aleatórias. O script exige Bash e OpenSSL (no Windows,
use WSL ou Git Bash) e preserva chaves e senhas existentes em `.secrets/auth/`
e `.env.auth`, ignorados pelo Git. Esse ambiente é separado do Compose.
Se mover o repositório, execute o script novamente para atualizar os caminhos.
Para executar o Quarkus diretamente, na raiz:

```bash
set -a
source .env.auth
set +a
mise exec -- mvn -f api/pom.xml quarkus:dev
```

Os caminhos são escritos entre aspas duplas, com os metacaracteres do shell escapados, inclusive em pastas com apóstrofos como `O'Connor`. Para corrigir um `.env.auth` gerado por uma versão anterior, execute o script novamente antes de carregá-lo; chaves e senhas existentes serão mantidas. A regressão do script pode ser executada com `python -m unittest discover -s docs/scripts -p 'test_preparar_auth_local.py' -v`; ela usa Bash e OpenSSL e verifica também o Compose quando o Docker CLI está instalado, sem precisar iniciar o daemon.

O provisionamento cria `administrador`, `operador` e `auditor`, respectivamente com os perfis `ADMINISTRADOR`, `OPERADOR` e `AUDITOR`. Ele exige `AUTH_DEMO_ENABLED=true` e as três variáveis `AUTH_DEMO_ADMINISTRADOR`, `AUTH_DEMO_OPERADOR` e `AUTH_DEMO_AUDITOR` (mínimo 12 caracteres, máximo 72 bytes UTF-8). Contas já existentes mantêm sua senha e seu id; reiniciar ou gerar outra configuração não troca credenciais persistidas. As senhas são protegidas com BCrypt do Quarkus e salt aleatório. O login diferencia maiúsculas e não aplica `trim` à senha.

| Operação | Requisição | Sucesso |
|---|---|---|
| Login | `POST /api/auth/login`, JSON com `login` e `senha` | 200 com tokens e identidade |
| Renovar | `POST /api/auth/renovar`, JSON com `refreshToken` | 200 com novo par; refresh anterior inutilizado |
| Logout | `POST /api/auth/logout`, JSON com `refreshToken` | 204, refresh revogado |
| Identidade | `GET /api/auth/identidade`, `Authorization: Bearer <accessToken>` | 200 com `id`, `login` e `perfil` |

Login e renovação retornam `accessToken`, `refreshToken`, `tokenType=Bearer`, `expiresIn=900`, `expiraEm`, `refreshExpiraEm` e `identidade`. O JWT RS256 dura **15 minutos**, é validado pelo Quarkus sem consulta ao banco e carrega o UUID interno em `sub`, login em `upn` e perfil em `groups`. O refresh opaco tem 256 bits aleatórios, dura **24 horas** e só seu hash SHA-256 é persistido. Cada renovação consome o refresh e cria outro válido por 24 horas, na mesma transação; duas chamadas concorrentes não podem consumi-lo duas vezes.

**Logout revoga o refresh enviado. O access já emitido continua válido até expirar, por no máximo 15 minutos.** Use o refresh mais recente depois de renovar. Credenciais inválidas e refresh ausente, expirado, revogado ou já usado recebem o mesmo 401 genérico. Access ausente, expirado, de outro emissor ou com assinatura inválida também recebe 401. Respostas com tokens usam `Cache-Control: no-store`; senhas e tokens não são registrados pela implementação. Todas as rotas de câmaras e lotes exigem Bearer e aplicam a [matriz de acesso por operação](docs/autorizacao.md). Administrador gerencia cadastros e descarte; operador consulta e registra baixas; auditor consulta. Bearer ausente/inválido recebe 401 e perfil sem permissão recebe 403, antes da execução.

Para implantação, forneça `JWT_PRIVATE_KEY`, `JWT_PUBLIC_KEY` (caminhos para o par RSA PEM montado no ambiente) e `JWT_ISSUER`. Não há chave padrão no artefato de produção; a configuração exige chaves próprias. Mantenha a chave privada e as credenciais no gerenciador de segredos do ambiente e use HTTPS. O provisionamento demonstrativo fica desabilitado por padrão. As chaves de `api/src/test/resources/auth` são exclusivas de testes e não entram no jar da aplicação. A expiração é verificada sem tolerância adicional; `ConfiguracaoJwt` corrige a interpretação de `clock.skew=0` no SmallRye JWT 4.6.3.

Para validar a issue #80 com PostgreSQL temporário e relógio controlado:

```bash
mise exec -- mvn --batch-mode -f api/pom.xml "-Dtest=AutenticacaoServiceTest,AutenticacaoPostgresTest" test
```

Os cenários cobrem os três perfis, credenciais protegidas, erro genérico, prazo do JWT, assinatura/emissor inválidos, identidade sem acesso ao banco, expiração do refresh no limite de 24 h, rotação, concorrência e logout com access ainda válido. A configuração de teste usa somente chaves e credenciais artificiais e dispensa `.env.auth`.

### Testes

#### Regras e casos de uso sem banco

Na raiz do repositório, prepare as ferramentas e execute a suíte:

```bash
mise trust
mise install java maven
mise run test-unit
```

`mise trust` autoriza as configurações locais do mise; revise `mise.toml` antes de confiar nele. A instalação e o primeiro build podem precisar de internet para baixar ferramentas e dependências. Não é necessário iniciar Docker, PostgreSQL, Compose ou a API.

O comando seleciona as classes terminadas em `Test` nos pacotes `dominio`, `aplicacao` e `suporte`. Os testes instanciam os objetos diretamente, sem iniciar Quarkus. Repositórios em memória substituem a persistência nos casos de uso; datas explícitas e `Clock.fixed` tornam os resultados independentes do dia da execução, incluindo a referência de validade em `America/Fortaleza`.

Para reproduzir a seleção pelo Maven ou investigar apenas uma classe:

```bash
mise exec -- mvn --batch-mode -f api/pom.xml "-Dtest=**/dominio/*Test,**/aplicacao/*Test,**/suporte/*Test" test
mise exec -- mvn --batch-mode -f api/pom.xml "-Dtest=AtualizarLoteTest" test
```

Mantenha as aspas no argumento `-Dtest`, inclusive no PowerShell. No resultado, confira `BUILD SUCCESS`, uma quantidade de testes maior que zero e `Failures: 0, Errors: 0, Skipped: 0`. Os relatórios de cada classe ficam em `api/target/surefire-reports`, em arquivos `.txt` e `TEST-*.xml`.

Essa seleção não inclui testes HTTP, de persistência ou de arquitetura. Os mesmos testes unitários continuam sendo executados pelo fluxo completo do Maven e pelo CI.

#### Contrato HTTP com repositórios em memória

Na raiz do repositório, prepare Java e Maven e execute:

```bash
mise trust
mise install java maven
mise run test-http
```

Não é necessário iniciar Docker, PostgreSQL, Compose ou a API manualmente. O comando seleciona as classes terminadas em `HttpMemoriaTest`. O Quarkus inicia a aplicação numa porta de teste livre, e o REST Assured envia requisições HTTP reais às rotas. Resources, casos de uso, validações e serialização JSON continuam sendo os da aplicação.

O `HttpEmMemoriaProfile` fornece repositórios em memória e um `Clock.fixed` em `America/Fortaleza`; também desativa datasource/JDBC, Hibernate ORM, Flyway e Dev Services. O `HttpEmMemoriaSupport` limpa os registros e reinicia os IDs antes de cada cenário que usa esses repositórios. Essas substituições ficam limitadas aos testes que selecionam o perfil.

Para reproduzir a seleção pelo Maven ou executar apenas uma classe:

```bash
mise exec -- mvn --batch-mode -f api/pom.xml "-Dtest=*HttpMemoriaTest" test
mise exec -- mvn --batch-mode -f api/pom.xml "-Dtest=LoteBaixaDescarteHttpMemoriaTest" test
```

Os testes de negócio usam `AutenticacaoHttpSupport.given()` com JWT real de administrador; use `autenticado(Perfil.OPERADOR)` ou `autenticado(Perfil.AUDITOR)` para outros perfis e `RestAssured.given()` para anônimos. As guardas permanecem ativas. Toda rota nova segue a [diretriz obrigatória de autorização](docs/autorizacao.md#regra-obrigatória-para-novas-rotas), com teste de permissão e OpenAPI atualizada.

Ao adicionar um teste HTTP sem banco, use o sufixo `HttpMemoriaTest` e selecione `HttpEmMemoriaProfile` para incluí-lo neste comando.

| Seleção | O que executa | Precisa de Docker? |
|---|---|---|
| `mise run test-unit` | Domínio, aplicação e suporte, sem iniciar Quarkus | Não |
| `mise run test-http` | Rotas HTTP reais com persistência em memória | Não |
| `mise run test` | Todos os testes Java e Go, incluindo integração PostgreSQL | Sim |
| `mise run ci` | Contrato e gerados, validação Java completa, formatação, análise estática, testes e build Go | Sim |

#### Suíte completa e integração

`mise run test` executa todos os testes Java e Go. A suíte Java inclui testes unitários, HTTP em memória, arquitetura e integração com PostgreSQL. `@QuarkusTest` inicia a aplicação; o uso do banco depende do perfil. Os testes com a configuração PostgreSQL iniciam um banco temporário pelo Dev Services e verificam persistência, consultas SQL, constraints e cenários de concorrência. É necessário manter o Docker em execução para a suíte completa, sem precisar subir o Compose antes.

Para executar apenas a suíte Java ou uma classe específica:

```bash
mise exec -- mvn --batch-mode -f api/pom.xml verify
mise exec -- mvn --batch-mode -f api/pom.xml -Dtest=CamaraServiceTest test
```

`mise run test`, `mise run ci` e o workflow executam Maven até `verify`, sem filtros de classe ou de grupo. O Surefire executa `Test*`, `*Test` e `*Tests` na fase `test`, incluindo os testes atuais de rotas, arquitetura, migrações e persistência. O Failsafe executa `IT*`, `*IT` e `*ITCase` na fase `integration-test` e verifica o resultado em `verify`; atualmente não há classes com esses nomes. Assim, novas classes de integração também entram no fluxo completo.

Os relatórios Java ficam em `api/target/surefire-reports` e `api/target/failsafe-reports` (quando houver testes Failsafe). O Actions publica esses relatórios no artefato `java-test-reports`, inclusive em caso de falha. O PostgreSQL temporário usa porta dinâmica e credenciais fornecidas pelo Dev Services, sem secrets, URL fixa ou Compose. Use o ambiente de teste sem sobrescrever `QUARKUS_DATASOURCE_*` com configurações de um banco externo.

Os testes com PostgreSQL real ficam agrupados assim:

| Classe | O que prova |
| --- | --- |
| `MigracoesFlywayTest` | Um esquema vazio recebe todas as migrações e restrições; uma nova execução do Flyway não reaplica nada nem apaga dados. |
| `CamaraRepositoryPostgresTest` e `LoteRepositoryPostgresTest` | CRUD, chave estrangeira, unicidade, filtros, paginação SQL, metadados (`criadoEm`/`atualizadoEm`) e inativação. |
| `integracao.RollbackTransacionalTest` | Uma rejeição desfaz a transação inteira, inclusive gravações já enviadas ao banco antes dela. |
| `integracao.AlocacaoConcorrenteTest` | Alocações e movimentações simultâneas não excedem a capacidade; redução de capacidade e inativação da câmara concorrentes com alocações e movimentações entre câmaras de origem e destino. |

Antes de concluir uma alteração, execute `mise run ci`: ele valida o contrato e os gerados, verifica a API com Maven, confere a formatação e executa análise estática, testes e build do serviço Go. Nos testes HTTP com PostgreSQL, as transações ocorrem no servidor; os dados criados precisam de limpeza própria, pois uma transação no método de teste não engloba a chamada HTTP. A aprovação das seleções sem banco não substitui a validação do CI completo. Para comprovar o CI, confira os jobs Protobuf / Buf, Java / Quarkus e Go / Telemetria no PR e, após a integração, no commit da branch principal.

#### Domínio Go de análise térmica

O pacote `services/telemetry/internal/dominio` calcula classificação, sequência e evidências por candidata. Seus testes não precisam de servidor ou banco. Os testes de `internal/grpc` usam `bufconn` para exercitar mensagens reais pela pilha gRPC; `internal/servidor` verifica saúde, reflection, limites, logs e encerramento. O CI executa também `go test -race ./...`.

Na raiz do repositório, com o Go configurado pelo mise:

```bash
mise exec -- go -C services/telemetry test ./...
mise exec -- go -C services/telemetry test -race ./...
```

O job **Go / Telemetria** executa os dois comandos. Para reproduzir o segundo localmente, habilite CGO e disponibilize um compilador C compatível com o Go da plataforma. Os testes não dependem do Docker nem do PostgreSQL.

#### Arquitetura

O teste `ArquiteturaTest` usa ArchUnit para verificar os pacotes reais de domínio
(`camara.dominio`, `lote.dominio` e `compartilhado.dominio`) e as fronteiras do
ADR 0002. Ele roda automaticamente em `mvn test`, `mvn verify` e `mise run ci`,
sem precisar iniciar Quarkus ou Docker quando executado isoladamente:

```bash
mise exec -- mvn --batch-mode -f api/pom.xml -Dtest=ArquiteturaTest test
```

O domínio só pode depender de tipos Java e de domínio; JDBC também é proibido.
As regras verificam o sentido das dependências entre camadas, impedem acesso da
API a repositórios e mantêm HTTP e JPA fora da aplicação, preservando CDI e
transações permitidos pelo ADR. A persistência também não pode conhecer HTTP.
Domínio e aplicação não podem depender de `io.grpc`, Protobuf, `io.quarkus.grpc`
nem dos tipos gerados (`br.ufrn.friovax.telemetry..`). Somente os pacotes
`*.integracao` fazem essa tradução, e nenhuma camada os acessa diretamente.
ArchUnit analisa bytecode: uma importação não utilizada não gera dependência;
para validar uma violação, use o tipo importado em um campo, assinatura ou anotação.

### Documentação OpenAPI e Swagger UI

A especificação OpenAPI 3.1 é gerada a partir do código pelo SmallRye OpenAPI. As anotações ficam nas classes da camada `api`: rotas em `*Resource`, campos em `*Request` e `*Response`, e metadados e respostas de erro reutilizadas em `compartilhado/api/DocumentacaoApi`. A especificação cobre os campos e suas regras, as enumerações, o formato das datas, os filtros, a paginação, o vínculo entre lote e câmara e as respostas de sucesso e de erro em Problem Details. Também explica a inativação, as restrições de capacidade e o significado de cada filtro.

O Swagger UI é publicado também no jar empacotado (`quarkus.swagger-ui.always-include=true`). Por isso, os endereços acima funcionam em `mise run up`, no Compose, e em `quarkus dev`. Faça login em `POST /api/auth/login`, copie o `accessToken` e informe-o em **Authorize**. Em **Try it out**, a interface envia Bearer para a própria API local, respeitando o perfil do usuário; consultar a documentação é público.

Ao alterar uma rota, um campo ou uma regra do contrato, atualize as anotações correspondentes. `DocumentacaoApiHttpMemoriaTest`, incluído em `mise run test-http`, verifica se a especificação continua publicada e se as rotas, os filtros e as respostas principais estão documentados.

### Uso da API pelo Postman

O endereço local da API é `http://localhost:8080`. O [contrato da API](docs/contrato-api.md) define as rotas previstas, os campos e as respostas esperadas. O Postman também pode importar a especificação de `http://localhost:8080/q/openapi` para gerar a coleção de requisições.

1. Com os serviços iniciados por `mise run up`, faça login em `POST /api/auth/login` e configure **Authorization → Bearer Token** com o `accessToken` recebido. Use administrador nos exemplos de cadastro/alteração/descarte; veja os demais perfis na [matriz de acesso](docs/autorizacao.md). Crie uma requisição no Postman e escolha o método e a URL. Por exemplo, `POST http://localhost:8080/api/camaras`.
2. Em **Body → raw → JSON**, copie o JSON de um [exemplo do contrato](docs/contrato-api.md#6-exemplos). Confira o cabeçalho `Content-Type: application/json`.
3. Envie e compare o status, os cabeçalhos e o JSON retornado com o contrato. Na criação, espere `201`, um ID gerado e o cabeçalho `Location`, que identifica o recurso criado.
4. Para exercitar erros, altere uma condição por vez. No exemplo de câmara, repetir o código retorna `409`; com um novo código, `capacidade: 0` retorna `422` e `estado: "INVALIDO"` retorna `400`.
5. Nas respostas de erro, confira `Content-Type: application/problem+json`, `type`, `status`, `detail` e `instance`. Em `422`, confira também a lista `erros`, que identifica os campos inválidos.

### Banco de dados

O Compose sobe um PostgreSQL 17 com volume nomeado `db-data` e healthcheck; a API só inicia depois que o banco está pronto e executa as migrações Flyway de `api/src/main/resources/db/migration` na inicialização. O Hibernate não cria nem altera o esquema.

| Variável | Padrão local | Uso |
|---|---|---|
| `DB_NAME` | `friovax` | Nome do banco |
| `DB_USER` | `friovax` | Usuário |
| `DB_PASSWORD` | `friovax` | Senha (somente desenvolvimento) |
| `DB_PORT` | `5432` | Porta do PostgreSQL publicada no host |

O Compose define `DB_URL=jdbc:postgresql://db:5432/<DB_NAME>` para a API e repassa `DB_USER` e `DB_PASSWORD`. Fora do Compose, o perfil de produção usa `DB_URL`, `DB_USER` e `DB_PASSWORD` para conectar ao banco. `QUARKUS_HTTP_HOST=0.0.0.0` permite acesso à API pelo host. As portas HTTP publicadas são `8080` para a API e `8081` para a saúde da telemetria.

Para mudar os padrões do Compose, copie `.env.example` para `.env` e edite os valores. Se alterar as credenciais de um volume já criado, o PostgreSQL manterá as credenciais originais; use um banco novo ou ajuste-as no banco. Os dados persistem entre `docker compose down` e `up`; `docker compose down -v` **apaga** o volume e seus dados. Em `mise run test` e `quarkus dev`, o Quarkus Dev Services sobe um PostgreSQL temporário automaticamente (requer Docker).

As migrações versionadas ficam em `api/src/main/resources/db/migration` e são aplicadas pelo Flyway na inicialização da API. O Hibernate está configurado com `schema-management.strategy=none`, portanto não cria tabelas. Para ver as migrações aplicadas no banco do Compose:

```bash
docker compose exec db psql -U friovax -d friovax -c 'SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;'
```

Se personalizou `DB_USER` ou `DB_NAME`, ajuste o comando. Migrações já aplicadas não devem ser alteradas; novas mudanças de esquema exigem um novo arquivo versionado.

Para conferir os dados do ambiente local usando os valores padrão do Compose:

```bash
docker compose exec db psql -U friovax -d friovax -c "SELECT id, codigo, nome, unidade, capacidade, ativo FROM camaras ORDER BY id;"
```

Se personalizou `DB_USER` ou `DB_NAME`, ajuste o usuário ou o banco no comando. Uma requisição rejeitada não deve criar registros; repetir a consulta após reiniciar os serviços também permite conferir a persistência dos dados.

### Cliente gRPC da análise térmica

A API chama o serviço Go por gRPC, pela porta `AnaliseTermica` do domínio. O adaptador `telemetria.integracao.AnaliseTermicaGrpc` usa os stubs gerados de `protos/`. A antiga configuração HTTP `TELEMETRY_BASE_URL` foi descontinuada.

| Variável | Padrão local | Compose | Uso |
|---|---|---|---|
| `TELEMETRY_GRPC_HOST` | `localhost` | `telemetry` | Host do serviço Go |
| `TELEMETRY_GRPC_PORT` | `9090` | `9090` | Porta gRPC do serviço Go, separada da porta HTTP de saúde (`8081`) |
| `TELEMETRY_DEADLINE` | `800ms` | padrão | Prazo de cada RPC, incluindo a espera pela conexão |
| `TELEMETRY_GRPC_PLAIN_TEXT` | `true` | padrão | `false` exige TLS; o gRPC fica na rede interna e não é exposto na internet |

O servidor gRPC do Go atende em `telemetry:9090` no Compose. O healthcheck HTTP do Go acompanha sua prontidão. A API Java possui o cliente de análise; os endpoints e a confirmação dos efeitos oficiais pertencem aos cards de integração. O canal só conecta na primeira chamada. Assim, a API inicia, responde em `/q/health` e atende cadastros e consultas mesmo sem o Go. Não há repetição automática. `UNAVAILABLE`, `DEADLINE_EXCEEDED` e cancelamento viram `AnaliseIndisponivel` (`503`, `/problemas/analise-indisponivel`). Status de chamada recusada (`INVALID_ARGUMENT`, `INTERNAL` e outros) ou resposta fora do contrato viram `FalhaDeIntegracao` (`502`, `/problemas/falha-de-integracao`), sem culpar o usuário nem expor stack, payload ou a descrição do servidor. A correlação segue no corpo e no metadado `x-correlation-id`. Ela aceita de 1 a 128 caracteres: letras ASCII, dígitos, `.`, `_`, `:` e `-`. Os logs registram somente a correlação, o código gRPC e um diagnóstico controlado pela API, sem a descrição arbitrária nem a exceção do servidor. O adaptador é criado na inicialização: um `TELEMETRY_DEADLINE` zero ou negativo impede o boot. Interromper a thread que chamou cancela a RPC no servidor.

Os testes `AnaliseTermicaGrpcTest` sobem um servidor gRPC real de teste (Netty, porta livre) que responde, demora, recusa a conexão ou devolve erros. Eles conferem a conversão para décimos e `Timestamp`, os resultados de todas as candidatas, o prazo e o cancelamento. `TelemetriaSemGoHttpMemoriaTest` prova o boot e a saúde sem o Go. `AnaliseTermicaGrpcQuarkusTest` usa endereço, porta e prazo vindos da configuração. Em modo de teste, o Quarkus redireciona clientes gRPC para a própria porta de teste. Por isso, os perfis de teste definem `quarkus.grpc.clients.telemetria.test-port`.

### Servidor Go de telemetria

O Compose ainda condiciona o início da API à saúde da telemetria. A remoção
dessa dependência de inicialização pertence à #84; a capacidade do processo Java
de iniciar sem Go já é verificada pelos testes do cliente.

O processo oferece HTTP `/health` em `8081` e gRPC em `9090`. O Compose publica
somente a porta HTTP; gRPC fica acessível aos containers da rede interna.
`EXPOSE 9090` no Dockerfile documenta a porta e não a publica no host.

| Variável no Go | Padrão | Uso |
|---|---|---|
| `PORT` | `8081` | Porta HTTP de saúde |
| `GRPC_PORT` | `9090` | Porta gRPC, distinta da HTTP |
| `GRPC_MAX_MESSAGE_BYTES` | `4194304` | Limite de 4 MiB para cada mensagem recebida ou enviada |
| `GRPC_MAX_CONCURRENT_ANALYSES` | `16` | Análises simultâneas no processo, somadas todas as conexões |
| `SHUTDOWN_TIMEOUT` | `5s` | Prazo compartilhado para encerrar HTTP e gRPC |

Portas inválidas/iguais, limites não positivos e duração inválida impedem o boot.
Excesso de análises retorna `RESOURCE_EXHAUSTED` sem fila. Health e reflection
não ocupam vagas de análise. Esses valores são limites iniciais, não uma medição
de capacidade. O deadline de cada chamada continua configurado pelo cliente Java.

O health gRPC informa `SERVING` para o servidor (`service: ""`) e para
`friovax.telemetry.v1.TelemetryService`. O HTTP retorna `200 {"status":"UP"}`
quando pronto. SIGTERM ou Ctrl+C marca indisponibilidade e inicia `GracefulStop`;
ao esgotar o prazo, fecha as conexões restantes. O Compose espera 10 segundos
antes de matar o container (`TELEMETRY_STOP_GRACE_PERIOD`); mantenha esse intervalo
maior que `SHUTDOWN_TIMEOUT` se personalizar a configuração.

Os logs da análise contêm método, correlação validada do corpo, código e duração;
não registram payload nem descrições arbitrárias de erros. O metadado
`x-correlation-id` não substitui a correlação do corpo.

Para executar fora de containers: `mise exec -- go -C services/telemetry run ./cmd/server`.
Use `grpcurl -plaintext localhost:9090 list` para descobrir os serviços.

## Política térmica da câmara

Faixa segura e limiar de cada câmara ficam numa política versionada. Consulte
`GET /api/camaras/{id}/politica-termica` (qualquer perfil) e anote `versao`. Para
alterar, como administrador, envie `PUT /api/camaras/{id}/politica-termica` com
`versaoEsperada` igual a essa versão, `temperaturaMinima`, `temperaturaMaxima` e
`limiar`. Se outra pessoa alterou antes, a resposta é 409: consulte de novo e
reenvie. O histórico fica em `GET /api/camaras/{id}/politica-termica/versoes`, e
cada nova versão aparece na trilha (`GET /api/auditoria?recurso=POLITICA_TERMICA`).
Detalhes no [contrato da política](docs/contrato-api.md#12-política-térmica-sprint-2--78).

## Histórico de leituras e ocorrências

As consultas recuperam dados confirmados no PostgreSQL e funcionam sem o serviço
Go. Administrador, operador e auditor podem acessá-las com Bearer válido.

| Consulta | Rota GET | Filtros |
|---|---|---|
| Leituras de uma câmara | `/api/camaras/{id}/leituras` | `de`, `ate`, `page`, `size` |
| Ocorrências | `/api/ocorrencias` | `camaraId`, `estado`, `de`, `ate`, `page`, `size` |
| Detalhe de uma ocorrência | `/api/ocorrencias/{id}` | — |

O período inclui `de` e exclui `ate`; informe instantes ISO 8601 com offset.
Nas ocorrências, o período considera a abertura. `estado` aceita `ABERTA` ou
`RESOLVIDA`. A paginação começa em zero, com 20 itens por padrão e máximo de 100.
As listas apresentam os registros mais recentes primeiro, com desempate por ID.

No Postman, configure **Authorization → Bearer Token** e use a aba **Params**.
Por exemplo, `GET /api/camaras/1/leituras?size=20` consulta as leituras da câmara
1; `GET /api/ocorrencias?camaraId=1&estado=RESOLVIDA` lista suas ocorrências
resolvidas. Use IDs existentes no seu ambiente. Sem registros, as listas ficam
vazias; um ID inexistente no detalhe retorna 404.

O detalhe inclui as leituras de evidência e a fotografia dos lotes na abertura.
A política original e essa fotografia são preservadas após alterações de faixa,
movimentação ou inativação dos cadastros. Os instantes são retornados em UTC (`Z`).
Veja campos, filtros e erros no [contrato do histórico](docs/contrato-api.md#11-histórico-de-leituras-e-ocorrências).

## Resolução de ocorrência

Uma ocorrência aberta deixa a câmara `EM_ALERTA` até um administrador resolvê-la
com `POST /api/ocorrencias/{id}/resolucao` e o body
`{ "justificativa": "..." }`. A resolução só é aceita quando a última leitura
aceita da câmara é segura, veio depois da abertura e foi analisada na política
vigente; caso contrário, a resposta é 409 e nada muda. O autor e o instante vêm do
token e do relógio da API. Depois disso, a câmara volta a `NORMAL`, a trilha
registra a resolução e uma nova excursão abre outra ocorrência. Reenviar a mesma
justificativa devolve a resolução original. Detalhes no
[contrato da resolução](docs/contrato-api.md#103-resolução-de-ocorrência-sprint-2--76).

## Licença

[MIT](LICENSE).

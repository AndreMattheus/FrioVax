# FrioVax

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
- [Retrospectiva da Sprint 1 — preparação para a reunião](docs/retrospectiva-01.md)
- [Contrato da API — Câmaras e Lotes](docs/contrato-api.md)
- [ADR 0001 — Arquitetura em camadas](docs/adr/0001-arquitetura-em-camadas.md)
- [ADR 0002 — Domínio independente e aplicação por interfaces](docs/adr/0002-dominio-independente-e-aplicacao-por-interfaces.md) — substitui o ADR 0001
- [Roteiro do vídeo de Web II](docs/roteiro-video-web2.md)
- [Roteiro do vídeo de Processos](docs/roteiro-video-processos.md)
- [Roteiro do vídeo da Sprint 1 de Web II](docs/roteiro-video-sprint-1-web2.md)
- [Roteiro do vídeo da Sprint 1 de Processos](docs/roteiro-video-sprint-1-processos.md)
- [Registro de uso de IA](docs/uso-de-ia.md)
- **GitHub Project:** [FrioVax Project — Board Principal](https://github.com/users/AndreMattheus/projects/1)
- **Issues do backlog:** [histórias US01 a US10](https://github.com/AndreMattheus/FrioVax/issues)
- **Vídeos da Sprint 1:** adicionar os links após as gravações

## Estrutura do projeto

```text
.
├── api/                    # API Java 25/Quarkus
├── services/telemetry/      # Microsserviço Go de telemetria
├── protos/                 # Reservado aos contratos da Sprint 2
├── docs/                   # Contrato da API, arquitetura e documentos do projeto
├── .github/workflows/      # CI dos dois stacks
├── docker-compose.yml
└── mise.toml
```

A API Java concentra os casos de uso, as regras de negócio e a persistência em PostgreSQL. Sua organização em `api`, `aplicacao`, `dominio` e `persistencia` está descrita no ADR 0002. O serviço Go possui atualmente a rota de saúde; o processamento de telemetria e a integração por Protobuf/gRPC estão previstos para a Sprint 2.

## Como rodar

Pré-requisitos: [mise](https://mise.jdx.dev/) e Docker Desktop em execução. Os testes unitários e HTTP em memória abaixo dispensam Docker e PostgreSQL. Execute os comandos a partir da raiz do repositório; as versões de Java, Maven e Go estão definidas em `mise.toml`.

```bash
mise trust
mise install
mise run build
mise run test
```

Para subir a API, o serviço Go e o banco:

```bash
mise run up
```

Em outro terminal, confira as rotas de saúde:

```bash
curl http://localhost:8080/api/status
curl http://localhost:8081/health
```

Com a API no ar, a documentação das rotas fica disponível em:

| Recurso | Endereço |
|---|---|
| Swagger UI | <http://localhost:8080/q/swagger-ui> |
| Especificação OpenAPI (YAML) | <http://localhost:8080/q/openapi> |
| Especificação OpenAPI (JSON) | <http://localhost:8080/q/openapi?format=json> |

| Comando | Resultado |
|---|---|
| `mise run build` | Compila Java e Go |
| `mise run test` | Executa os testes dos dois stacks |
| `mise run test-unit` | Executa testes Java de domínio, aplicação e suporte sem banco |
| `mise run test-http` | Executa testes HTTP de câmaras, lotes e documentação em memória, sem banco |
| `mise run lint` | Verifica Maven, `gofmt` e `go vet` |
| `mise run ci` | Reproduz o pipeline localmente |
| `mise run up` | Sobe os serviços com Docker Compose |

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

Ao adicionar um teste HTTP sem banco, use o sufixo `HttpMemoriaTest` e selecione `HttpEmMemoriaProfile` para incluí-lo neste comando.

| Seleção | O que executa | Precisa de Docker? |
|---|---|---|
| `mise run test-unit` | Domínio, aplicação e suporte, sem iniciar Quarkus | Não |
| `mise run test-http` | Rotas HTTP reais com persistência em memória | Não |
| `mise run test` | Todos os testes Java e Go, incluindo integração PostgreSQL | Sim |
| `mise run ci` | Validação Java completa, formatação, análise estática, testes e build Go | Sim |

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

Antes de concluir uma alteração, execute `mise run ci`: ele verifica a API com Maven, confere a formatação e executa análise estática, testes e build do serviço Go. Nos testes HTTP com PostgreSQL, as transações ocorrem no servidor; os dados criados precisam de limpeza própria, pois uma transação no método de teste não engloba a chamada HTTP. A aprovação das seleções sem banco não substitui a validação do CI completo. Para comprovar o CI, confira os jobs Java / Quarkus e Go / Telemetria no PR e, após a integração, no commit da branch principal.

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
ArchUnit analisa bytecode: uma importação não utilizada não gera dependência;
para validar uma violação, use o tipo importado em um campo, assinatura ou anotação.

### Documentação OpenAPI e Swagger UI

A especificação OpenAPI 3.1 é gerada a partir do código pelo SmallRye OpenAPI. As anotações ficam nas classes da camada `api`: rotas em `*Resource`, campos em `*Request` e `*Response`, e metadados e respostas de erro reutilizadas em `compartilhado/api/DocumentacaoApi`. A especificação cobre os campos e suas regras, as enumerações, o formato das datas, os filtros, a paginação, o vínculo entre lote e câmara e as respostas de sucesso e de erro em Problem Details. Também explica a inativação, as restrições de capacidade e o significado de cada filtro.

O Swagger UI é publicado também no jar empacotado (`quarkus.swagger-ui.always-include=true`). Por isso, os endereços acima funcionam em `mise run up`, no Compose, e em `quarkus dev`. Em **Try it out**, a interface envia as requisições para a própria API local.

Ao alterar uma rota, um campo ou uma regra do contrato, atualize as anotações correspondentes. `DocumentacaoApiHttpMemoriaTest`, incluído em `mise run test-http`, verifica se a especificação continua publicada e se as rotas, os filtros e as respostas principais estão documentados.

### Uso da API pelo Postman

O endereço local da API é `http://localhost:8080`. O [contrato da API](docs/contrato-api.md) define as rotas previstas, os campos e as respostas esperadas. O Postman também pode importar a especificação de `http://localhost:8080/q/openapi` para gerar a coleção de requisições.

1. Com os serviços iniciados por `mise run up`, crie uma requisição no Postman e escolha o método e a URL. Por exemplo, `POST http://localhost:8080/api/camaras`.
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
| `DB_PORT` | `5432` | Porta publicada no host |

Para mudar os padrões, copie `.env.example` para `.env`. Os dados persistem entre `docker compose down` e `up`; para apagá-los use `docker compose down -v`. Em `mise run test` e `quarkus dev`, o Quarkus Dev Services sobe um PostgreSQL temporário automaticamente (requer Docker).

Para conferir os dados do ambiente local usando os valores padrão do Compose:

```bash
docker compose exec db psql -U friovax -d friovax -c "SELECT id, codigo, nome, unidade, capacidade, ativo FROM camaras ORDER BY id;"
```

Se personalizou `DB_USER` ou `DB_NAME`, ajuste o usuário ou o banco no comando. Uma requisição rejeitada não deve criar registros; repetir a consulta após reiniciar os serviços também permite conferir a persistência dos dados.

## Licença

[MIT](LICENSE).

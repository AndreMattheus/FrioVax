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
- [Contrato da API — Câmaras e Lotes](docs/contrato-api.md)
- [ADR 0001 — Arquitetura em camadas](docs/adr/0001-arquitetura-em-camadas.md)
- [ADR 0002 — Domínio independente e aplicação por interfaces](docs/adr/0002-dominio-independente-e-aplicacao-por-interfaces.md) — substitui o ADR 0001
- [Roteiro do vídeo de Web II](docs/roteiro-video-web2.md)
- [Roteiro do vídeo de Processos](docs/roteiro-video-processos.md)
- [Registro de uso de IA](docs/uso-de-ia.md)
- **GitHub Project:** [FrioVax Project — Board Principal](https://github.com/users/AndreMattheus/projects/1)
- **Issues do backlog:** [histórias US01 a US10](https://github.com/AndreMattheus/FrioVax/issues)
- **Vídeos:** adicionar os links não listados do YouTube após as gravações

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

Pré-requisitos: [mise](https://mise.jdx.dev/) e Docker Desktop em execução. Execute os comandos a partir da raiz do repositório; as versões de Java, Maven e Go estão definidas em `mise.toml`.

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

| Comando | Resultado |
|---|---|
| `mise run build` | Compila Java e Go |
| `mise run test` | Executa os testes dos dois stacks |
| `mise run lint` | Verifica Maven, `gofmt` e `go vet` |
| `mise run ci` | Reproduz o pipeline localmente |
| `mise run up` | Sobe os serviços com Docker Compose |

### Testes

`mise run test` executa os testes Java e Go. Os testes unitários de domínio e aplicação usam objetos Java, repositórios em memória e relógio fixo quando necessário. Os testes de integração com `@QuarkusTest` iniciam a aplicação e um PostgreSQL temporário pelo Dev Services; os testes HTTP usam REST Assured para verificar as respostas e os dados persistidos. É necessário manter o Docker em execução, sem precisar subir o Compose antes.

Para executar apenas a suíte Java ou uma classe específica:

```bash
mise exec -- mvn --batch-mode -f api/pom.xml verify
mise exec -- mvn --batch-mode -f api/pom.xml -Dtest=CamaraServiceTest test
```

Os relatórios Java ficam em `api/target/surefire-reports`. Antes de concluir uma alteração, execute `mise run ci`: ele verifica a API com Maven, confere a formatação e executa análise estática, testes e build do serviço Go. Os testes HTTP executam transações no servidor; os dados criados por eles precisam de limpeza própria, pois uma transação no método de teste não engloba a chamada HTTP.

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

### Uso da API pelo Postman

O endereço local da API é `http://localhost:8080`. O [contrato da API](docs/contrato-api.md) define as rotas previstas, os campos e as respostas esperadas.

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

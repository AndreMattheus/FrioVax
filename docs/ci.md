# Integração contínua

O [workflow CI](../.github/workflows/ci.yml) executa em todo push e pull request,
sem filtros por caminho. As verificações dos componentes executam em paralelo.
O smoke constrói as imagens a partir dos fontes, sem depender de um artefato
local produzido por outro job.

## Verificações dos componentes

| Job | Verificação e comando |
|---|---|
| `Java / Quarkus` | `python docs/scripts/verificar-java.py`: Spotless, PostgreSQL exclusivo e `mvn verify` |
| `Go / Telemetria` | `gofmt`, `go vet ./...`, `go test ./...`, `go test -race ./...` e build em `services/telemetry` |
| `Protobuf / Buf` | `mise run proto-check`: lint, geração e comparação dos gerados Go com o Git |
| `Smoke / Compose` | `python docs/scripts/smoke-compose.py`: build, saúde dos serviços e readiness HTTP |
| `Contrato da publicação` | Suíte Python existente de `test_deploy.py` |
| `Ambiente de autenticação` | Suíte Python existente de `test_preparar_auth_local.py`, em Linux e Windows |

O script Java primeiro executa `mvn --batch-mode -f api/pom.xml spotless:check`.
Depois inicia um PostgreSQL descartável, aguarda saúde e fornece a URL aos testes
antes de executar `mvn --batch-mode -f api/pom.xml verify`. O banco permanece
durante toda a suíte, inclusive entre reinícios da aplicação por troca de perfil.
O mesmo banco é reutilizado para verificar a persistência dos dados após
o reinício da aplicação. Ao final, o script remove apenas
o contêiner e seus volumes temporários e confere o JAR executável do Quarkus.
Os relatórios Surefire/Failsafe ficam no
artifact `java-test-reports` do workflow, inclusive quando um teste falha.

## Formatação Java

O Spotless 3.12.0 verifica `src/main/java` e `src/test/java`, sem incluir classes
geradas em `target`. As regras básicas convertem tabs de indentação em espaços
com largura quatro, removem espaços ao final das linhas, garantem newline final
e exigem quebras LF. Não há reorganização de imports, quebra automática de
linhas longas nem correção da profundidade de todos os blocos: a conversão de
tabs não equivale a um formatador que entende a estrutura Java.
O uso de LF também está definido em `.gitattributes` para evitar resultados
diferentes no Windows e no Linux.

O check está ligado à fase Maven `validate`, antes da compilação e dos testes.
Por isso, uma violação falha rapidamente e também impede `mvn verify` de passar.

```sh
mise run lint
# Aplicar as regras básicas:
mise exec -- mvn --batch-mode -f api/pom.xml spotless:apply
```

O comando `spotless:apply` modifica os arquivos automaticamente quando executado.
O CI e o build usam `spotless:check`: verificam o padrão e falham quando há
diferenças. A aplicação das correções é feita somente por `spotless:apply`.

## Reprodução local

Instalar as ferramentas de `mise.toml`, Python 3.11 ou superior e Docker com
Compose. Os testes do ambiente de autenticação precisam de Bash e OpenSSL;
no Windows, o script existente usa Git Bash. Executar na raiz:

```sh
mise run ci
```

Esse comando verifica contrato e gerados, Maven, Go, scripts Python e smoke.
Pode ser executado por partes com `mise run lint`, `mise run ci-java`,
`mise run test-race` e `mise run smoke`.
Não há alteração de arquivos pelo lint; `spotless:apply` é uma ação separada.

No Windows, `go test -race` usa a imagem `golang:1.27` com GCC em um contêiner
temporário, montando somente os fontes Go como leitura. Isso evita exigir um
compilador C na máquina. O comando de teste é o mesmo usado no runner Linux;
os testes Go comuns e o build continuam usando o Go instalado pelo mise.
A primeira execução pode baixar dependências e imagens. A execução local é
sequencial, enquanto os jobs do GitHub executam em paralelo.

## Smoke do Compose

Um smoke é uma verificação curta do sistema integrado: confirma que as imagens
constroem, os serviços ficam saudáveis e a API está pronta para atender. Os testes
de negócio e as verificações de persistência são descritos separadamente na
[documentação de containers](containers.md).

O script usa um nome `friovax-smoke-<identificador aleatório>`, escolhe portas
livres para API, Go HTTP e PostgreSQL e não carrega o `.env` de desenvolvimento.
O banco e a autenticação usam volumes exclusivos dessa execução. O fluxo é:

```text
build e up --wait → db/api/telemetry healthy → GET /q/health/ready
                                           → HTTP 200 e status UP
             sempre: down --volumes do projeto exclusivo
```

O `auth-init` deve terminar com código zero; ele prepara as credenciais, não é
um serviço permanente. O script limita a espera de saúde a 180 segundos e a
subida/construção a 600 segundos. A limpeza fica em `finally`, tanto em sucesso
quanto em falha normal do processo. A remoção de volumes se restringe ao nome
aleatório criado pelo script; o projeto de desenvolvimento não é encerrado.
As imagens construídas permanecem no cache local do Docker para reutilização;
a limpeza remove contêineres, rede e volumes dessa execução.

## Referências técnicas

- [Spotless para Maven](https://github.com/diffplug/spotless/blob/main/plugin-maven/README.md)
- [Detector de condições de corrida do Go](https://go.dev/doc/articles/race_detector)

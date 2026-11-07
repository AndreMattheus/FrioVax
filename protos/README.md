# Contrato de telemetria v1

O arquivo [telemetry.proto](friovax/telemetry/v1/telemetry.proto) é o acordo de
comunicação entre Java e Go, conforme a [ADR 0003](../docs/decisoes/0003-servico-go-telemetria.md).

## O que cada parte significa

```text
telemetry.proto                 contrato escrito por nós
       |
       +-- buf lint             verifica estrutura, nomes e comentários
       |
       +-- buf generate         executa os plugins locais fixados
                |
                +-- telemetry.pb.go       mensagens e serialização
                +-- telemetry_grpc.pb.go  interfaces e stubs gRPC
```

Uma **mensagem** é uma estrutura de dados, semelhante a um DTO. Protobuf define
seus campos e a serialização: converter dados em bytes e reconstruí-los depois.
**gRPC** permite que um programa chame um método de outro pela rede. O contrato
define `AnalyzeReadings`, sua requisição e sua resposta.

```text
Java: requisição -> cliente gRPC -> rede -> servidor Go: AnalyzeReadings
Java: resposta   <- cliente gRPC <- rede <- resultado da análise
```

Buf valida o contrato e executa dois plugins locais. A geração é explícita;
`go build` apenas compila os arquivos existentes.

| Gerador | Arquivo produzido | Conteúdo |
|---|---|---|
| `protoc-gen-go` | `telemetry.pb.go` | Structs, enums, acesso aos campos e metadados usados pelo Protobuf |
| `protoc-gen-go-grpc` | `telemetry_grpc.pb.go` | Cliente, interface do servidor e registro/encaminhamento da chamada |

Os arquivos são extensos porque incluem suporte padrão das bibliotecas, inclusive
descritores dos tipos e métodos de compatibilidade. Não contêm a regra térmica.
Mantemos à mão o `.proto`; o código Go é sempre regenerado, sem cortes manuais.

As versões são independentes: `friovax.telemetry.v1` é a versão do **contrato**;
`version: v2` nos YAMLs é a versão do **formato de configuração do Buf**;
`1.75.0` é a versão do **executável Buf**. `proto3` é a sintaxe do arquivo Protobuf.

## Instalação e geração

Na raiz do repositório, com mise instalado:

```bash
mise trust
mise install
mise run proto-lint
mise run proto-generate
mise exec -- go -C services/telemetry build ./...
mise exec -- go -C services/telemetry test ./...
```

Os comandos do mise usam `protos/` como diretório de trabalho. Os equivalentes,
dentro desse diretório e com as ferramentas do mise no PATH, são `buf lint` e
`buf generate`. Não executar `buf generate` diretamente na raiz, onde não há
configuração Buf.

| Ferramenta | Versão fixada | Local da configuração |
|---|---|---|
| Buf CLI | 1.75.0 | `mise.toml` |
| Plugin de mensagens Go | 1.36.12 | `buf.gen.yaml` |
| Plugin gRPC Go | 1.6.2 | `buf.gen.yaml` |

### Onde ficam os geradores e como são executados

`protoc-gen-go` e `protoc-gen-go-grpc` são programas, não arquivos escritos neste
repositório. O comando e a versão de cada programa são definidos na propriedade
`local` do [buf.gen.yaml](buf.gen.yaml). Por exemplo:

```yaml
local: [go, run, google.golang.org/protobuf/cmd/protoc-gen-go@v1.36.12]
```

Essa lista indica o executável `go` e seus argumentos. Equivale ao comando:

```bash
go run google.golang.org/protobuf/cmd/protoc-gen-go@v1.36.12
```

O outro plugin é executado com:

```bash
go run google.golang.org/grpc/cmd/protoc-gen-go-grpc@v1.6.2
```

Esses comandos são chamados pelo Buf, que fornece a entrada no protocolo dos
plugins e grava os arquivos retornados. Para gerar o contrato, use
`mise run proto-generate`; executar um plugin sozinho não fornece essa entrada.

```text
mise run proto-generate
  -> mise.toml: executa buf generate dentro de protos/
  -> buf.gen.yaml: indica os comandos dos plugins e o diretório de saída
  -> go run: obtém o código na versão fixada, compila e executa cada plugin
  -> Buf grava telemetry.pb.go e telemetry_grpc.pb.go
```

No primeiro uso, Go baixa o código dos plugins e suas dependências. O código
baixado fica no cache de módulos (`GOMODCACHE`), e os artefatos de compilação usam
o cache de build (`GOCACHE`). O executável usado por `go run` é administrado pelo
Go; não instalamos um `protoc-gen-go.exe` fixo no projeto nem no PATH. Para consultar
os diretórios de cache desta máquina:

```bash
mise exec -- go env GOMODCACHE GOCACHE
```

Os caches podem ser reaproveitados nas próximas execuções. As versões explícitas
evitam depender de um plugin global de versão desconhecida. Os programas executam
localmente; não são usados plugins remotos do serviço Buf.
As dependências de execução ficam em `services/telemetry/go.mod` e `go.sum`;
elas são diferentes das ferramentas que geram código.

Os dois arquivos gerados ficam em
`services/telemetry/gen/friovax/telemetry/v1/` e devem entrar no Git junto do
contrato. Nunca os editar manualmente. `clean: true` limpa **somente a saída
configurada `services/telemetry/gen/`** antes da geração para não deixar arquivos
obsoletos. Não guardar código manual nesse diretório.

## Geração Java pelo Maven

A dependência `quarkus-grpc`, em [api/pom.xml](../api/pom.xml), habilita a geração
Java. Sua versão vem do BOM do Quarkus: a lista de versões compatíveis já adotada
pela API. O plugin Maven do Quarkus executa `generate-code` antes de compilar,
usando o ciclo de build do empacotamento `quarkus`.

```bash
mise exec -- mvn --batch-mode -f api/pom.xml clean package -DskipTests
```

`clean` remove a saída anterior; `package` gera, compila e empacota; `-DskipTests`
pula a execução dos testes. Para executar a suíte existente, use `verify` sem essa opção.

```text
protos/friovax/telemetry/v1/telemetry.proto
                 |
       Maven + extensão quarkus-grpc
                 |
api/target/generated-sources/grpc/br/ufrn/friovax/telemetry/v1/
                 |
       compilação junto da API
```

O plugin recebe `quarkus.grpc.codegen.proto-directory` com o valor
`${project.basedir}/../protos`. `project.basedir` é a pasta do `pom.xml` (`api/`),
independentemente do diretório de onde o comando Maven foi executado.
Java e Go leem o mesmo contrato, sem cópias manuais.

O `.proto` define `java_package = "br.ufrn.friovax.telemetry.v1"` e
`java_multiple_files = true`: mensagens como `Reading` recebem arquivos Java
separados nesse pacote. O Quarkus também gera o suporte gRPC. Os geradores Java
são obtidos pelo Maven; não dependem dos plugins Go nem do executável Buf.

As classes geradas ficam em `target/`, já ignorado pelo Git, e são recriadas no
build. Domínio e aplicação não importam esses tipos: a conversão pertence aos
adaptadores de transporte. Gerar classes não inicia uma chamada remota.

O [Dockerfile da API](../api/Dockerfile) preserva `api/` e `protos/` como pastas
irmãs no estágio de build. Consulte os [comandos Docker](../README.md#build-docker-da-api).

## Janela, contexto e candidatas

Uma **candidata** é uma leitura nova aguardando análise e confirmação pelo Java.
O **contexto** contém leituras já aceitas, necessárias para continuar a sequência.
A **janela** agrupa a política, o contexto e as candidatas de uma câmara. Ela é
limitada por quantidade de leituras, não por uma duração fixa.

```text
Janela da câmara 1 — política versão 1 — faixa 2 a 8 °C — N=3
  Contexto:   [r1: 9 °C, r2: 9 °C]   já persistido
  Candidatas: [r3: 9 °C, r4: 4 °C]   ainda não confirmado

Resultado esperado por candidata:
  r3 -> insegura | sequência 3 | sinal sim | evidências [r1, r2, r3]
  r4 -> segura   | sequência 0 | sinal não | evidências []
```

O sinal de `r3` permanece no resultado mesmo depois da leitura segura `r4`.
Java confirma os efeitos e impede ocorrências duplicadas; Go não grava leituras
nem resolve ocorrências. O exemplo descreve o comportamento exigido pelo contrato;
a geração dos tipos não implementa essa análise.

Temperaturas são inteiros em décimos: 90 significa 9 °C, e -15 significa -1,5 °C.
Campos de temperatura usam
`optional` para distinguir ausência de 0 °C. `Timestamp` representa o instante;
a API converte o offset recebido para o mesmo instante UTC, sem arredondar.

## Invariantes e falhas

- Uma chamada contém de 1 a 50 candidatas ao todo, com uma janela por câmara.
- Cada janela tem ao menos uma candidata e até N-1 itens de contexto (máximo 99).
  N varia de 1 a 100; N=1 dispensa contexto.
- Leituras pertencem à mesma câmara/versão, com IDs únicos e instantes crescentes.
  O contexto não atravessa mudança de política ou resolução do episódio.
- Temperaturas vão de -9999 a 9999 décimos; mínimo deve ser menor que máximo.
- Sequência e evidências são limitadas a N. As evidências são IDs das últimas
  inseguras consecutivas, incluindo a candidata. Uma segura zera a sequência e a lista.
- A resposta conserva correlação, ordem das janelas e todos os resultados das
  candidatas. `oneof outcome` impede sucesso e erro simultâneos, mas ainda permite
  nenhum dos dois: o adaptador deverá rejeitar essa ausência.
- Zero em enums é `UNSPECIFIED`, nunca uma classificação ou erro válido.

| Tipo de falha | Representação no contrato |
|---|---|
| Janela inválida, política inválida, mistura de câmara/versão | `WindowError` com código tipado; outras janelas podem concluir |
| Estrutura global inválida, como câmaras duplicadas ou excesso global | gRPC `INVALID_ARGUMENT` |
| Cancelamento | gRPC `CANCELLED` |
| Prazo esgotado | gRPC `DEADLINE_EXCEEDED` |
| Falha interna que compromete a chamada | gRPC `INTERNAL` |
| Serviço inacessível | gRPC `UNAVAILABLE`, observado pelo cliente |

Comentários e `buf lint` não executam regras de negócio. Domínio e adaptadores
devem validar os dados; os detalhes de cada campo estão no `.proto`. Erros do
contrato interno não devem ser automaticamente atribuídos ao usuário HTTP.
O deadline de 800 ms é responsabilidade do adaptador Java, não do Buf.

## Evolução do contrato

O número de cada campo é sua identidade na transmissão, não seu valor padrão.
Preservar números e significados. Ao remover um campo, reservar seu número e nome
com `reserved` na mensagem, evitando reutilização. Mudanças incompatíveis exigem
avaliar uma nova versão de pacote. Regenerar sempre após alterar o contrato.

O contrato pode conter enums/valores desconhecidos de uma versão futura; adaptadores
devem rejeitar resultados desconhecidos em vez de presumir uma decisão térmica.
Tipos gerados pertencem à fronteira de transporte, não ao domínio/aplicação.

Referências: [Protobuf](https://protobuf.dev/programming-guides/proto3/),
[código Go gerado](https://protobuf.dev/reference/go/go-generated/),
[gRPC](https://grpc.io/docs/what-is-grpc/introduction/),
[Buf v2](https://buf.build/docs/configuration/v2/buf-yaml/),
[plugins locais](https://buf.build/docs/configuration/v2/buf-gen-yaml/).

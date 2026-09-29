# ADR 0002 — Domínio independente e aplicação por interfaces

- **Status:** Proposto
- **Data:** 29/09/2026
- **Issue:** [#18](https://github.com/AndreMattheus/FrioVax/issues/18)
- **Substitui:** [ADR 0001 — Arquitetura em camadas do serviço Java](0001-arquitetura-em-camadas.md)

## Contexto

O ADR 0001 propôs entidades de domínio com anotações JPA e dependência da aplicação para a implementação de infraestrutura. O código evoluiu para outra organização: `Camara` e `Lote` são classes Java sem framework. As interfaces de repositório ficam no domínio. Entidades JPA, mapeadores e implementações PostgreSQL ficam em `persistencia` e implementações em memória existem apenas em `src/test`.

Nas orientações de entrega da Sprint 1, é exigido que o domínio não importe Ktor, Quarkus, Exposed, Hibernate nem JDBC e que essa regra seja verificada por teste. A separação entre modelos de domínio e entidades JPA é a solução adotada pelo projeto para manter esse domínio independente.

## Decisão

Manter a organização por funcionalidade (`camara`, `lote`) e usar as camadas `api`, `aplicacao`, `dominio` e `persistencia`. O nome `persistencia` reflete os adaptadores de banco já existentes.

```text
br.ufrn.friovax.api
├── camara
│   ├── api             # Resource, DTOs HTTP de entrada e saída, validação de entrada
│   ├── aplicacao       # Service, comandos e resultados dos casos de uso
│   ├── dominio         # Camara, EstadoCamara, regras e interface CamaraRepository
│   └── persistencia    # CamaraEntity, CamaraMapper, CamaraRepositoryPostgres
├── lote
│   └── ...             # mesma organização, conforme seus casos de uso forem implementados
└── compartilhado
    ├── api             # ProblemDetails e ExceptionMappers
    ├── aplicacao       # configuração do Clock injetável
    └── dominio         # exceções, normalização, paginação e demais tipos independentes
```

A árvore inclui componentes planejados.

### Responsabilidades e dependências

| Camada | Responsabilidade | Restrições |
|---|---|---|
| `api` | Receber JSON, aplicar Bean Validation aos DTOs, chamar a aplicação e produzir DTOs de saída, status HTTP e `Location` | Não acessa repositórios, não expõe entidades JPA e não decide regras de negócio |
| `aplicacao` | Coordenar casos de uso, delimitar transações, obter o instante de referência, invocar o domínio e persistir pelas interfaces | Não importa DTOs da API, JAX-RS, `Response`, entidades JPA ou repositórios concretos |
| `dominio` | Representar entidades, validar invariantes, definir interfaces de repositório e lançar exceções próprias | Não importa JPA, Bean Validation, Quarkus, Hibernate, JDBC nem as outras camadas |
| `persistencia` | Implementar as interfaces de repositório com PostgreSQL/Panache e converter entidades JPA em objetos de domínio | Não decide regras de negócio nem produz respostas HTTP |

As setas abaixo representam dependências de código, não a ordem de execução de uma requisição:

```text
api ──► aplicacao ──► dominio ◄── persistencia
```

- A aplicação conhece `CamaraRepository`, definido no domínio. O Quarkus injeta `CamaraRepositoryPostgres` na execução. Testes unitários fornecem `CamaraRepositoryEmMemoria` pelo construtor.
- A camada `api` também pode usar os enums de domínio nos DTOs. Os `ExceptionMapper`s de `compartilhado.api` conhecem as exceções do domínio para convertê-las em respostas HTTP.
- Comandos e resultados dos casos de uso ficam em `aplicacao` e não possuem dependências HTTP ou JPA. A API faz a conversão entre esses tipos e seus próprios DTOs.
- Tipos de domínio podem depender de `compartilhado.dominio`, mantendo a mesma independência de framework.
- Uma funcionalidade acessa os casos de uso de outra pela camada `aplicacao`, preservando a fronteira definida no ADR anterior.

### Tempo

A aplicação recebe um `java.time.Clock` por injeção. Sua configuração fica em `compartilhado.aplicacao`, usando `America/Fortaleza`. Testes unitários fornecem um relógio fixo. O domínio recebe os valores de data e hora necessários às operações, sem consultar o relógio do sistema por conta própria.

### Validação e erros

- DTOs HTTP validam a presença e o formato dos dados na entrada. O domínio mantém suas invariantes para também proteger chamadas feitas sem HTTP.
- A normalização e as validações de comprimento respeitam a ordem definida no contrato: `trim` antes de medir o texto e código em maiúsculas antes de comparar unicidade.
- O domínio lança exceções como `ValidacaoDeNegocio` e `CodigoDuplicado` e não conhece códigos de status nem `ProblemDetails`.
- Os `ExceptionMapper`s existentes em `compartilhado.api` produzem `application/problem+json`. O Resource não repete esse tratamento com `try/catch`.
- JSON malformado ou falha de desserialização, incluindo enum inválido, retorna `400`.
- Violações de campos ou regras de entrada retornam `422`.
- Código duplicado retorna `409`.

## Alternativas consideradas

- **Retomar JPA no domínio:** contrariaria a separação já implementada e acoplaria as regras ao mecanismo de persistência.
- **Injetar `CamaraRepositoryPostgres` diretamente no serviço:** vincularia o caso de uso ao banco e dificultaria reutilizar o repositório em memória nos testes.
- **Tornar também a aplicação inteiramente independente de CDI e transações Jakarta:** exigiria uma fronteira transacional adicional. Para a entrega atual que é a sprint 1, manter `@Transactional` na aplicação preserva a decisão anterior e a simplicidade, sem acoplar o domínio.

## Consequências

- Domínio e casos de uso podem ser exercitados por testes unitários sem subir HTTP ou PostgreSQL. A transação real precisa ser verificada nos testes de integração.
- Há modelos separados para HTTP, aplicação, domínio e persistência. As conversões ficam nas fronteiras correspondentes, evitando expor entidades JPA pela API.
- Alterações na persistência não exigem que o serviço conheça a implementação concreta, enquanto a interface permanecer compatível.

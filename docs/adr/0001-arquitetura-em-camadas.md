# ADR 0001 — Arquitetura em camadas do serviço Java

- **Status:** Proposto
- **Data:** 23/09/2026
- **Issue:** [#12](https://github.com/AndreMattheus/FrioVax/issues/12)

## Contexto

A Sprint 1 implementa os CRUDs de câmaras e lotes (US01–US03) no serviço `api/` (Java 25/Quarkus). As regras de capacidade, estado e validade descritas no [contrato da API](../contrato-api.md) precisam ser testáveis sem HTTP e aplicadas de forma transacional. Na Sprint 2, o mesmo serviço passará a chamar o microsserviço Go via gRPC e a alterar o estado oficial da câmara (US04–US05). A [proposta](../proposta.md) prevê verificações de arquitetura no CI.

Precisamos de uma organização que:

- separe o contrato HTTP das entidades persistidas;
- concentre as regras de negócio num único ponto, sem duplicação entre rotas;
- defina onde começa e termina cada transação;
- permita acrescentar integrações (gRPC, cache, auditoria) sem reescrever o domínio;
- seja simples o bastante para uma equipe de quatro pessoas numa disciplina.

## Decisão

Organizar o código **por funcionalidade** (`camara`, `lote`) e, dentro de cada uma, **em quatro camadas**. Código transversal fica em `compartilhado`.

```text
br.ufrn.friovax.api
├── camara
│   ├── api              # CamaraResource, DTOs de entrada/saída, mapeadores
│   ├── aplicacao        # CamaraService (casos de uso, @Transactional)
│   ├── dominio          # Camara, EstadoCamara, regras e exceções de domínio
│   └── infraestrutura   # CamaraRepository (Hibernate ORM com Panache)
├── lote
│   └── ...              # mesma estrutura
└── compartilhado
    ├── api              # Página, ExceptionMappers → Problem Details
    └── dominio          # Exceções de domínio base, relógio (Clock)
```

### Responsabilidades

| Camada | Responsabilidade | Não pode |
|---|---|---|
| `api` | Rotas JAX-RS, conversão JSON ↔ DTO, validação de formato (Bean Validation nos DTOs), status HTTP e `Location` | Conter regra de negócio, acessar repositórios ou expor entidades JPA |
| `aplicacao` | Casos de uso; delimitar transações; buscar entidades, invocar regras de domínio e persistir; bloquear a câmara ao alocar lotes | Conhecer HTTP (`Response`, status, anotações JAX-RS) |
| `dominio` | Entidades, enums e invariantes (capacidade, faixa térmica, transições de estado, validade); lançar exceções de domínio | Depender de `api`, `aplicacao` ou `infraestrutura` |
| `infraestrutura` | Repositórios, consultas paginadas e filtradas, migrações Flyway, futuros clientes gRPC | Decidir regra de negócio |

### Regra de dependência

```text
api ──► aplicacao ──► dominio
            │            ▲
            └──► infraestrutura
```

- `api` depende apenas de `aplicacao` (e de `dominio` somente para enums usados nos DTOs).
- `aplicacao` depende de `dominio` e `infraestrutura`.
- `infraestrutura` depende de `dominio`.
- `dominio` não depende de nenhuma outra camada do projeto.
- Uma funcionalidade só acessa outra pela camada `aplicacao` (por exemplo, `LoteService` usa `CamaraService`, nunca `CamaraRepository`).

### Decisões complementares

- **Entidades JPA no domínio:** as classes de domínio levam anotações JPA. Aceitamos esse acoplamento para evitar um segundo modelo e mapeamento duplicado; o contrato HTTP continua isolado por DTOs.
- **Transações:** `@Transactional` apenas em `aplicacao`. Alocação e troca de câmara de lotes usam bloqueio pessimista na câmara de destino.
- **Erros:** o domínio lança exceções próprias (`RecursoNaoEncontrado`, `ConflitoDeNegocio`, `ValidacaoDeNegocio`); `ExceptionMapper`s em `compartilhado.api` convertem para Problem Details conforme o contrato.
- **Tempo:** a data de referência vem de um `Clock` injetável com fuso `America/Fortaleza`, para que a regra de validade seja testável.
- **Verificação:** um teste com [ArchUnit](https://www.archunit.org/) executado em `mvn verify` falha o CI se a regra de dependência for violada.

## Alternativas consideradas

- **Pacotes por camada no topo** (`resource/`, `service/`, `repository/`): comum, mas espalha cada funcionalidade por vários pacotes e dificulta ver o que pertence a câmaras ou lotes.
- **Arquitetura hexagonal completa** (portas e adaptadores, domínio sem JPA): isola mais, mas dobra o número de classes e mapeamentos para um domínio pequeno.
- **Active Record do Panache** (regras na própria entidade, sem serviço nem repositório): menos código, mas mistura persistência e regra e torna difícil testar e verificar as dependências.

## Consequências

- Regras de negócio podem ser testadas com testes unitários de domínio e de serviço, sem subir HTTP.
- A integração gRPC da Sprint 2 entra em `infraestrutura` sem alterar `api` nem `dominio`.
- Há mais classes (DTOs e mapeadores) do que num CRUD direto sobre entidades.
- Novas dependências serão adicionadas na Sprint 1: Hibernate ORM com Panache, JDBC PostgreSQL, Flyway, Hibernate Validator, SmallRye OpenAPI e ArchUnit.
- Mudanças nesta estrutura exigem um novo ADR que substitua este.

# PR: verificar independência do domínio com ArchUnit

## O que mudou

Adiciona ArchUnit 1.5.1 no escopo de testes e `ArquiteturaTest` com seis regras.
O domínio de câmaras, lotes e tipos compartilhados só pode depender de Java e
do próprio domínio, sem JDBC, frameworks ou outras camadas. As fronteiras do
ADR 0002 impedem dependências de domínio para aplicação/API/persistência,
de aplicação para API/persistência e de persistência para API/aplicação.
A API não acessa interfaces de repositório; aplicação não conhece HTTP/JPA/JDBC
e persistência não conhece HTTP. CDI e transações continuam permitidos na aplicação.

Closes #17

## Como verificar

```bash
mise exec -- mvn --batch-mode -f api/pom.xml -Dtest=ArquiteturaTest test
mise run ci
```

O Surefire descobre o teste normalmente em `mvn test` e `mvn verify`.
O workflow existente `.github/workflows/ci.yml` já executa `mvn verify`.
Não há perfil ou comando adicional obrigatório. Classes de `src/test` são
excluídas da análise, incluindo os repositórios em memória.

## Evidência de falha/passo

Validação realizada em 02/10/2026, fuso America/Fortaleza, com Java 25.

Foi inserida temporariamente a seguinte dependência em
`api/src/main/java/br/ufrn/friovax/api/camara/dominio/EstadoCamara.java`:

```java
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public enum EstadoCamara {
    // constantes e métodos existentes
}
```

O comando isolado acima retornou código **1**, com a seguinte evidência:

```text
ArquiteturaTest.dominio_independente Architecture Violation
Class <br.ufrn.friovax.api.camara.dominio.EstadoCamara> is annotated with <jakarta.enterprise.context.ApplicationScoped> in (EstadoCamara.java:0)
Tests run: 6, Failures: 1, Errors: 0, Skipped: 0
BUILD FAILURE
```

O arquivo foi restaurado byte a byte em um bloco `finally`. A violação não
integra o diff final. ArchUnit analisa bytecode: é necessário usar o tipo
importado, pois um import não utilizado não gera uma dependência compilada.

Após a restauração, `mise run ci` retornou código **0**. O Maven executou
as seis regras de arquitetura junto à suíte completa:

```text
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0 -- ArquiteturaTest
Tests run: 250, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

O fluxo também concluiu o build Quarkus e a formatação, análise estática,
testes e build do serviço Go. O serviço Go atualmente não possui testes.

Os logs completos locais são `archunit-fail.log` e `ci-validation.log`
(ignorados pelo Git); os relatórios são gerados em `api/target/surefire-reports`.

## Escopo das regras

As regras protegem dependências entre camadas. A restrição textual do ADR sobre
acesso entre funcionalidades apenas por casos de uso não é imposta neste PR:
`ConsultarCamara` já depende de `lote.dominio.LoteRepository`. Esse comportamento
existente requer alinhamento separado do ADR ou dos casos de uso.

## Publicação

Este documento contém o texto e a evidência preparados para o PR. A publicação
no GitHub está pendente: a sessão de implementação não dispõe de ferramenta
GitHub autenticada. Cole este conteúdo na descrição do PR ao publicá-lo.

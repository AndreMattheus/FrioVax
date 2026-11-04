# Ensaio da implantação — #87

Este ensaio valida o procedimento da [ADR 0004](../decisoes/0004-implantacao.md) em Docker local. Não provisiona contas, não chama deploy hooks e não aceita connection string externa. Credenciais sintéticas são geradas em memória para o PostgreSQL isolado, sem publicar sua porta.

## Executar

Pré-requisitos: Python 3.11+, Docker com containers Linux em execução, acesso às imagens Maven/Temurin/PostgreSQL/Flyway e às dependências Maven. A primeira execução pode demorar para construir a imagem. Executar na raiz:

```bash
python docs/scripts/simular-implantacao.py
```

Se Python não estiver no PATH, pode ser executado isoladamente pelo mise, sem alterar `mise.toml`: `mise exec python@3.11 -- python docs/scripts/simular-implantacao.py`.

O script constrói `api/Dockerfile`, cria rede/container com nomes únicos, usa PostgreSQL 17 e Flyway 12.0.0 (versão do BOM atual), e copia as migrações para pasta temporária. Não usa `.env`, o Compose existente, volumes da aplicação ou secrets reais. A API fica acessível somente em loopback, em porta dinâmica; Go não é iniciado. Ao terminar, remove somente seus containers/volumes anônimos, rede e imagem da API. As imagens base/cache Docker permanecem.

O resultado esperado são cinco linhas `OK` e código de saída zero:

1. Migrações reais aplicadas por processo externo à API.
2. Container Java com `migrate-at-start=false`, status e prontidão funcionais sem Go.
3. Migração inválida temporária falha e reverte inclusive DDL parcial, mantendo dados e histórico anteriores. O retorno não zero representa o portão que impede chamar o hook; não testa o workflow remoto ainda inexistente.
4. Migração aditiva temporária aplicada; a imagem anterior volta a iniciar sobre esse esquema, preservando o registro HTTP e o histórico. É um ensaio de compatibilidade/retorno, não um rollback real do Render.
5. Backup lógico restaurado em outro banco vazio, com igualdade do registro e do histórico anterior, sem sobrescrever o banco de origem.

Uma falha encerra a execução e a limpeza ocorre no `finally`. Não imprimir logs/configuração de ambientes reais para investigar. Se o processo for interrompido à força, listar os recursos com prefixo `friovax-ensaio-` e conferir os nomes antes de remover apenas os desta execução.

## Registro da execução

- Data: 04/11/2026, execução local em Windows/Docker Desktop com containers Linux.
- Base: `origin/main` em `f43749b`; alterações da branch `docs/87-definir-implantacao` deste PR.
- Comando: `mise exec python@3.11 -- python docs/scripts/simular-implantacao.py`.
- Resultado: **cinco verificações OK, código de saída zero**, com PostgreSQL 17 e Flyway 12.0.0. Nenhum acesso a Render/Neon ou uso de segredo real.
- Migração externa: V1 e V2 aplicadas antes de iniciar a API.
- Saúde sem Go: `/api/status` e `/q/health/ready` retornaram `UP`; destino de telemetria apontava para porta local sem serviço; migração no startup desabilitada.
- Falha transacional: SQL inválido na cópia temporária retornou erro; tabela parcial ausente, histórico e registro anteriores preservados.
- Redeploy/retorno: mesma imagem anterior reiniciada após migração aditiva temporária; igualdade do registro via HTTP e histórico novo mantido.
- Recuperação: `pg_dump` restaurado por `pg_restore --exit-on-error` em outro banco vazio; igualdade do registro e histórico do backup confirmada, sem sobrescrever origem.
- Limpeza: containers, volumes anônimos, rede e tag da imagem criados pelo script removidos no término.
- CI local: `mise run ci` passou; **472 testes Java, zero falhas/erros/ignorados**, formatação, `go vet`, testes e build Go passaram.
- Verificações Python do workflow: 11 testes de `test_medicao_sprint_2.py` passaram e `validar-medicao-sprint-2.py` confirmou integridade dos registros/corte existentes.
- Links locais dos documentos e `git diff --check`: sem problemas. CI remoto, revisão e aceites externos permanecem no checklist.

## Conferência humana e remota

Arthur revisa os passos com o titular @AndreMattheus e Leonardo revisa a ADR. Registrar confirmação real no [checklist](pre-requisitos.md); o ensaio não substitui aprovação, CI, merge e datas reais no Project.

Na #88, repetir no ambiente provisionado: confirmar TLS/hostname e permissões runtime, criar dado fictício, redeployar o mesmo SHA e conferir seu ID/valores; conferir SHA do deploy Render e status final do GitHub Deployment. Simular migração inválida somente em banco/branch descartável. Confirmar recuperação privada e preservação de escritas antes de liberar o primeiro deploy. Render/Neon, quotas, deploy hook e políticas do environment não são comprovados pela simulação local.

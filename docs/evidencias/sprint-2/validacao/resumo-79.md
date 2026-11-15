# Validação local do resumo — #79

Execução assistida pelo Codex em 15/11/2026, no Windows, com Java 25,
Maven 3.9.16, Quarkus 3.33.3.2 e PostgreSQL 17 temporário via Docker.
Este registro descreve verificações automatizadas locais; não comprova CI remoto,
revisão humana, publicação ou aceite da história.

## Cenários executados

| Evidência | Resultado verificado | Fonte reproduzível |
|---|---|---|
| TTL sem espera | Primeiro acesso miss; acesso em 29 s hit; em 30 s novo miss; timestamps acompanham o Clock | [ConsultarResumoTest](../../../../api/src/test/java/br/ufrn/friovax/api/resumo/aplicacao/ConsultarResumoTest.java) |
| Concorrência e falha | 16 consultas compartilham uma carga; falha de banco não preenche o cache | Mesmo teste unitário |
| Inventário e episódios | Lote de 100 doses disponível, lote de 20 doses descartado e lote inativo excluído; dois episódios abertos com o mesmo lote resultam em um lote distinto afetado | [ResumoPostgresTest](../../../../api/src/test/java/br/ufrn/friovax/api/resumo/api/ResumoPostgresTest.java), `totaisConferemComBancoSemDuplicarLoteEntreEpisodios` |
| Commit e rollback | Cadastro confirmado e baixa invalidam; cadastro dentro de transação revertida preserva o cache e os totais confirmados | Mesmo teste PostgreSQL, `commitInvalidaERollbackPreservaResultadoSemExporDadosPendentes` |
| Estados separados | Atualizar câmara para MANUTENCAO preserva SEM_LEITURAS; inativação retira a câmara dos totais | Mesmo teste PostgreSQL, `separaEstadoOperacionalDoTermicoEExcluiCamaraInativa` |
| Métricas reais e cache entre perfis | Três GETs HTTP autenticados produzem delta de **1 miss e 2 hits**; o teste confere os valores no texto Prometheus recebido de `/q/metrics`, além do MeterRegistry | Mesmo teste PostgreSQL, `httpCompartilhaCacheEntrePerfisSemGoEProtegeMetricas` |
| Autorização | Resumo: três perfis aceitos; anônimo 401 e perfil desconhecido 403, sem miss. Métricas: ADMINISTRADOR/AUDITOR 200; OPERADOR 403 e anônimo 401 | Mesmo teste HTTP |

O teste de resumo não depende de processo Go. O estado SEM_LEITURAS consultado
é o persistido. Abertura de episódios usa o repositório real e publicação após
commit; esta base ainda não oferece endpoints de ingestão/resolução para repetir
essa etapa por HTTP. O [roteiro](../../../resumo.md) explicita esse limite.

## Comandos

Suíte Java final: **772 testes, 0 falhas, 0 erros e 1 ignorado**.
Os oito testes novos de resumo passaram (quatro unitários e quatro PostgreSQL/HTTP).

```sh
mvn --batch-mode -f api/pom.xml verify
buf lint protos
# No diretório protos:
buf generate
git diff --exit-code -- ../services/telemetry/gen
# Na raiz:
gofmt -l services/telemetry
go -C services/telemetry vet ./...
go -C services/telemetry test ./...
go -C services/telemetry build -o bin/telemetry ./cmd/server
docker compose config --no-env-resolution --quiet
git diff --check
```

Buf lint e geração passaram, com stubs Go sem alterações e sem arquivos novos;
formatação, vet, testes e build Go passaram. Compose passou na validação
estrutural sem resolver `.env.auth`, ausente neste checkout. Isso não substitui
gerar a autenticação local para subir o ambiente completo.

Os relatórios Maven ficam em `api/target/surefire-reports`; são artefatos locais
ignorados pelo Git. O workflow existente publica esses relatórios ao executar no CI.
O único teste ignorado na suíte local é a conferência de persistência entre
reinícios quando não há URL de banco externo: o Dev Services recria o banco.
Esse teste preexistente não é uma dispensa de verificar persistência em produção.

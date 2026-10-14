# Sprint 1 — checklist integrado de entrega

Checklist de **DIM0547 — Desenvolvimento de Sistemas Web II** e **DIM0510 — Processos de Software**, conforme os [guias de Web II](https://github.com/fmarquesfilho/web2-2026-2/blob/main/docs/SPRINT-1.md) e de [Processos](https://github.com/fmarquesfilho/processos-2026-2/blob/main/docs/SPRINT-1.md), suas tarefas e rúbricas atualizadas.

**Prazo:** 16/10/2026, às 23:59 (horário de Fortaleza). **Situação consultada:** 14/10/2026. A Sprint 0 já foi entregue; seu checklist registra uma situação anterior à entrega e não deve ser usado como lista de pendências atual.

`[x]` indica evidência localizada no repositório ou no GitHub. `[ ]` indica entrega ou comprovação ainda não localizada nesta verificação; não afirma que a equipe deixou de executar uma atividade fora desses registros. Não marcar vídeos, reunião ou métricas como feitos sem evidência.

## Web II — incremento técnico

- [x] **CRUD de duas entidades relacionadas:** câmaras e lotes, com chave estrangeira, criação, consulta, atualização, inativação, paginação SQL e filtros. Evidências: [migrações](../api/src/main/resources/db/migration/), [recursos de câmara](../api/src/main/java/br/ufrn/friovax/api/camara/api/CamaraResource.java) e [lote](../api/src/main/java/br/ufrn/friovax/api/lote/api/LoteResource.java), histórias [US01](https://github.com/AndreMattheus/FrioVax/issues/1), [US02](https://github.com/AndreMattheus/FrioVax/issues/2) e [US03](https://github.com/AndreMattheus/FrioVax/issues/3) em `Pronto`.
- [x] **Camadas e domínio independente:** [ADR 0002](adr/0002-dominio-independente-e-aplicacao-por-interfaces.md), pacotes `dominio`, `aplicacao`, `api` e `persistencia`; [teste ArchUnit](../api/src/test/java/br/ufrn/friovax/api/arquitetura/ArquiteturaTest.java) já incluído, embora sua avaliação tenha sido transferida para a Sprint 2.
- [x] **PostgreSQL e Flyway:** [Compose](../docker-compose.yml) com volume e healthcheck, migrações versionadas, configuração da API e [instruções no README](../README.md#banco-de-dados). A [suíte com PostgreSQL real](https://github.com/AndreMattheus/FrioVax/pull/59) foi integrada.
- [x] **Validação, Problem Details e OpenAPI:** [mapeadores de erros](../api/src/main/java/br/ufrn/friovax/api/compartilhado/api/), [documentação no código](../api/src/main/java/br/ufrn/friovax/api/compartilhado/api/DocumentacaoApi.java), [Swagger UI e URLs](../README.md#documentação-openapi-e-swagger-ui). Evidência adicional: [PR #55](https://github.com/AndreMattheus/FrioVax/pull/55).
- [x] **CI verde em `main`:** [execução de 14/10](https://github.com/AndreMattheus/FrioVax/actions/runs/38565319390). A [tarefa de testes](https://github.com/fmarquesfilho/web2-2026-2/blob/main/docs/SPRINT-1-TAREFAS.md#t9--testes-movida-para-a-sprint-2) passou a ser avaliada na Sprint 2; os testes já implementados permanecem como evidência adicional.
- [ ] **Reprodução do zero por alguém de fora da equipe:** confirmar `mise run up`, CRUD e persistência após reinício em clone limpo, registrando resultado. A [issue #34](https://github.com/AndreMattheus/FrioVax/issues/34) já cobre esta verificação e a documentação técnica; está em progresso.
- [ ] **Vídeo de Web II:** gravar cerca de cinco minutos com participação dos quatro integrantes, demonstrar CRUD na Swagger UI, migrações/Compose, arquitetura, validação, CI e o que ficou para a Sprint 2. Publicar e adicionar o link ao README. [Roteiro da Sprint 1](roteiro-video-sprint-1-web2.md).

## Processos — fluxo e evidências

- [x] **Incremento funcional em `main`:** histórias [US01](https://github.com/AndreMattheus/FrioVax/issues/1), [US02](https://github.com/AndreMattheus/FrioVax/issues/2) e [US03](https://github.com/AndreMattheus/FrioVax/issues/3) concluídas; [README](../README.md#como-rodar) explica o ambiente. Falta apenas a comprovação externa de reprodução indicada acima.
- [x] **Quadro e política escrita:** [GitHub Project](https://github.com/users/AndreMattheus/projects/1) com colunas `Backlog`, `Sprint Backlog`, `Em Progresso`, `Em Revisão` e `Pronto`; as descrições das colunas agora mostram entrada, saída e limites de WIP 8/4/2, coerentes com o [acordo de processo](processo.md#fluxo-kanban-e-wip). Os cartões técnicos da Sprint 1 aparecem em `Pronto`, exceto a [issue #34](https://github.com/AndreMattheus/FrioVax/issues/34), em `Em Progresso` na consulta de 14/10.
- [x] **Movimentação real de cartões:** os históricos das [issues #12](https://github.com/AndreMattheus/FrioVax/issues/12) e [#16](https://github.com/AndreMattheus/FrioVax/issues/16) registram passagens por colunas de trabalho e revisão antes de `Pronto`.
- [ ] **Respeito aos limites de WIP e gargalo:** discutir o histórico completo do Project com a equipe e registrar qualquer período de coluna cheia ou espera. A posição atual dos cartões e exemplos isolados de movimentação não comprovam respeito ao WIP durante toda a sprint.
- [x] **Prática XP com rastro verificável:** revisão de código e integração contínua. Exemplo: o [PR #40](https://github.com/AndreMattheus/FrioVax/pull/40) teve pedido de mudanças, correções, aprovação e integração; o [PR #41](https://github.com/AndreMattheus/FrioVax/pull/41) também. O [CI de `main`](https://github.com/AndreMattheus/FrioVax/actions/runs/38565319390) passou. Descrever na retrospectiva o que funcionou e o que não funcionou nessa prática.
- [ ] **Métricas de fluxo:** obter datas de entrada no quadro, início do trabalho e entrega de cada cartão; calcular lead time, cycle time, throughput semanal e WIP. Registrar método, período, números e maior espera em `docs/retrospectiva-01.md`. Não usar a data de abertura do PR como entrada em `Em Progresso` sem declarar essa aproximação.
- [ ] **Retrospectiva com a equipe:** há uma [base factual e pauta](retrospectiva-01.md) preparada. Reunir os quatro integrantes em 15/10, discutir causas (5 Porquês), registrar métricas no fechamento, acordar até três ações verificáveis com responsável e prazo, e ajustar o [acordo de processo](processo.md) se necessário.
- [ ] **Vídeo de Processos:** gravar cerca de cinco minutos com os quatro integrantes; mostrar incremento, quadro e histórico, WIP, métricas, prática XP, retrospectiva e ações. Publicar e adicionar o link ao README. [Roteiro da Sprint 1](roteiro-video-sprint-1-processos.md).

## Fechamento integrado

- [ ] Concluir a [issue #34](https://github.com/AndreMattheus/FrioVax/issues/34): documentação, demonstração de casos de sucesso/erro, teste em clone limpo e atualização de `docs/uso-de-ia.md` com o apoio material efetivamente utilizado.
- [ ] Garantir PRs de cada integrante integrados e revisões cruzadas; os [PRs integrados da Sprint 1](https://github.com/AndreMattheus/FrioVax/pulls?q=is%3Apr+is%3Amerged) já oferecem evidências para conferir a distribuição por pessoa e semana.
- [ ] Integrar a documentação final em `main`, verificar CI verde após o merge e conferir os links públicos dos dois vídeos antes do prazo.
- [ ] Concluir a [história de entregáveis #61](https://github.com/AndreMattheus/FrioVax/issues/61), com data alvo de 15/10 no Product Backlog.

As pendências acima são da Sprint 1. Nenhuma deve ser interpretada como pendência da Sprint 0.

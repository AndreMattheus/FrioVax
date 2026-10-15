# Retrospectiva da Sprint 1 — 15/10/2026

**Reunião:** realizada em 15/10/2026, às 10h (horário de Fortaleza), com Leonardo, Matheus, Arthur e Bianca presentes. Os fatos, a análise e os encaminhamentos abaixo foram consolidados a partir dos relatos individuais fornecidos após a reunião.

**Objetivo:** avaliar o fluxo da Sprint 1 e definir melhorias verificáveis para a Sprint 2. As evidências estão no [quadro](https://github.com/users/AndreMattheus/projects/1), nos PRs citados abaixo e no [checklist da Sprint 1](checklist-sprint-1.md).

## Fatos e evidências trazidos à retrospectiva

1. O incremento de câmaras e lotes está em `main`. As histórias [US01](https://github.com/AndreMattheus/FrioVax/issues/1), [US02](https://github.com/AndreMattheus/FrioVax/issues/2) e [US03](https://github.com/AndreMattheus/FrioVax/issues/3) estão concluídas; o [CI de `main` em 14/10](https://github.com/AndreMattheus/FrioVax/actions/runs/38565319390) passou.
2. Leonardo relatou cerca de dois dias de espera por revisão nos [PRs #53](https://github.com/AndreMattheus/FrioVax/pull/53) (07–09/10) e [#55](https://github.com/AndreMattheus/FrioVax/pull/55) (10–12/10). Para manter o fluxo, assumiu a revisão final do [#47](https://github.com/AndreMattheus/FrioVax/pull/47), combinada com Arthur, e as [issues #31](https://github.com/AndreMattheus/FrioVax/issues/31), [#33](https://github.com/AndreMattheus/FrioVax/issues/33) e [#34](https://github.com/AndreMattheus/FrioVax/issues/34). Segundo seu relato, integrou 10 dos 25 PRs da sprint.
3. Matheus relatou que as revisões não seguiram sempre o anel do [acordo de processo](processo.md#revisão-e-branches) e que revisou seis PRs, conforme a [consulta de PRs fechados que revisou](https://github.com/AndreMattheus/FrioVax/pulls?q=is%3Apr+state%3Aclosed+reviewed-by%3AAndreMattheus). Bianca relatou que assumiu revisões apenas no fim da sprint, no [#55](https://github.com/AndreMattheus/FrioVax/pull/55), inclusive deixando de ser a revisora primária de PRs de Arthur durante a maior parte do período. Os relatos apontam concentração das revisões e espera por disponibilidade.
4. Arthur relatou [contribuições em intervalos irregulares](https://github.com/AndreMattheus/FrioVax/graphs/contributors?from=12%2F09%2F2026), associadas à disponibilidade entre outras disciplinas e questões pessoais. Matheus relatou ampliação das tarefas derivadas das histórias planejadas para atender a requisitos específicos da disciplina e ao setup pendente.
5. Os históricos das [issues #12](https://github.com/AndreMattheus/FrioVax/issues/12) e [#16](https://github.com/AndreMattheus/FrioVax/issues/16) mostram cartões passando por trabalho e revisão antes de `Pronto`. Isso não demonstra, por si só, se os limites de WIP foram respeitados durante toda a sprint.

## Análise das causas relatadas

O principal problema descrito por Leonardo, Matheus e Bianca foi a espera por revisão e a concentração desse trabalho. A cadeia apresentada nos relatos tem quatro perguntas respondidas:

1. **Por que PRs aguardavam revisão?** Os revisores solicitados estavam ocupados, e outras pessoas assumiram para evitar que o fluxo parasse.
2. **Por que havia pouca disponibilidade?** Demandas de trabalho e escola concorriam com o projeto. Bianca também relatou insegurança para revisar Java e Quarkus enquanto aprendia a stack.
3. **Por que a revisão não recebia prioridade consistente?** O [acordo de processo](processo.md#revisão-e-branches) define o anel e permite substituição, mas não define quando revisar um PR pendente.
4. **Por que o acúmulo não foi percebido e tratado mais cedo?** Os limites de WIP constam do [acordo](processo.md#fluxo-kanban-e-wip), mas, segundo os relatos, não eram acompanhados no quadro durante a sprint.

**Causa acordada:** a equipe não tinha uma rotina para priorizar PRs pendentes e conferir o WIP de `Em revisão` nas sincronizações. Com a disponibilidade desigual e a insegurança em revisar parte do backend, as revisões se concentraram e alguns PRs esperaram. A análise registrada nos relatos respondeu a quatro perguntas dos 5 Porquês; não há uma quinta resposta documentada.

## Práticas XP: efeito observado

A revisão de código e a integração contínua foram as práticas mais citadas. No [PR #40](https://github.com/AndreMattheus/FrioVax/pull/40), a revisão identificou rotas que responderiam `500`; no [#41](https://github.com/AndreMattheus/FrioVax/pull/41), levou a ajustes de migração, unicidade e resposta `409`; no [#47](https://github.com/AndreMattheus/FrioVax/pull/47), fortaleceu o teste de concorrência. Arthur descreveu a conferência dos critérios de aceite contra o diff e a execução local de `mise run ci` ao validar o [#39](https://github.com/AndreMattheus/FrioVax/pull/39). Também testou a persistência após reiniciar o Compose e relatou que um teste de integração com PostgreSQL expôs a tradução incorreta de violação de unicidade para `DuplicateCode`. Matheus relatou que as revisões motivaram a atualização do ADR para refletir a arquitetura implementada.

O benefício foi detectar problemas antes da integração. O custo relatado foi a espera por revisores e a concentração de conhecimento em parte da equipe.

## Acordos para a Sprint 2

Os encaminhamentos dos relatos foram registrados como acordos da reunião conforme a orientação recebida nesta solicitação. Matheus declarou apoio à ação proposta por Leonardo.

| Ação acordada, verificável na Sprint 2 | Responsável | Prazo | Como verificar |
|---|---|---|---|
| Começar as sincronizações pelos PRs aguardando revisão; com `Em revisão` no limite de WIP 2, ajudar a concluir cartões antes de iniciar outro. | Leonardo | Até o fim da Sprint 2 | Histórico do quadro e PRs revisados |
| Assumir as revisões previstas no anel, usando a Definição de Pronto como roteiro. | Bianca | Durante a Sprint 2 | Revisões de PRs e distribuição entre integrantes |
| Registrar ao menos uma contribuição por semana, como commit, revisão ou comentário de issue. | Arthur | Semanalmente na Sprint 2 | Histórico de commits, reviews ou issues |

**Alteração no acordo de processo:** as sincronizações passam a começar pela fila de PRs aguardando revisão e pelo WIP de `Em revisão`, conforme registrado em [processo.md](processo.md#cadência-e-cerimônias). A regra de concluir trabalho existente antes de iniciar outro quando o WIP está no limite já constava do acordo; o anel de revisão e a Definição de Pronto continuam orientando as revisões.

## Medição e acompanhamento

- **Evidência das ações:** acompanhar pelo histórico do [quadro](https://github.com/users/AndreMattheus/projects/1), dos PRs, commits e issues ao longo da Sprint 2. Cartões específicos para essas ações não foram informados.
- **Métricas de fluxo até 15/10, 17h48:** 24 cartões técnicos concluídos; lead time médio de **11,68 dias** e mediano de **10,19**; cycle time médio de **1,35 dia** e mediano de **0,86**. Throughput semanal: **4, 11, 3 e 6** cartões nas semanas de 21/09 a 15/10; a última semana ainda está em curso. O maior lead time foi o da [#34](https://github.com/AndreMattheus/FrioVax/issues/34), **22,35 dias**; o maior cycle time, o da [#24](https://github.com/AndreMattheus/FrioVax/issues/24), **5,74 dias**. Método, fontes, aproximações e dados por cartão estão em [métricas da Sprint 1](metricas-sprint-1.md).
- **WIP:** no instante da consulta, havia **1 cartão em `Em Progresso`**, **0 em `Em Revisão`** e **27 em `Pronto`** na milestone Sprint 1. O histórico disponível não permite comprovar o respeito aos limites de WIP durante toda a sprint; a concentração e a espera por revisão constam dos relatos.

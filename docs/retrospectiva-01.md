# Retrospectiva da Sprint 1 — preparação para a reunião de 15/10

**Estado:** base factual preparada em 14/10. A discussão dos quatro integrantes, a confirmação das causas e o acordo sobre as ações ainda precisam acontecer. Este documento não registra uma reunião que não ocorreu.

**Objetivo da reunião:** avaliar o fluxo da Sprint 1 e fechar até três ações verificáveis para a Sprint 2. Usar o [quadro](https://github.com/users/AndreMattheus/projects/1), os PRs e o [checklist](checklist-sprint-1.md) durante a conversa.

## Fatos observáveis

1. O incremento de câmaras e lotes está na branch `main`. As histórias [US01](https://github.com/AndreMattheus/FrioVax/issues/1), [US02](https://github.com/AndreMattheus/FrioVax/issues/2) e [US03](https://github.com/AndreMattheus/FrioVax/issues/3) estão concluídas; o [CI de `main` em 14/10](https://github.com/AndreMattheus/FrioVax/actions/runs/38565319390) passou.
2. A revisão cruzada mudou código antes da integração. No [PR #40](https://github.com/AndreMattheus/FrioVax/pull/40), a revisão identificou erros HTTP que retornariam `500` após a combinação com outra mudança; a autora corrigiu os pontos e o PR foi aprovado. No [PR #41](https://github.com/AndreMattheus/FrioVax/pull/41), a revisão pediu ajustes na migração e no tratamento de unicidade antes da aprovação. Essa sequência evidencia a prática XP de revisão de código.
3. O [PR #47](https://github.com/AndreMattheus/FrioVax/pull/47) recebeu pedido de fortalecimento do teste de concorrência antes da aprovação, um exemplo de espera e retrabalho na etapa de revisão. O histórico do PR permite discutir a causa concreta com a autora e os revisores.
4. Os históricos das [issues #12](https://github.com/AndreMattheus/FrioVax/issues/12) e [#16](https://github.com/AndreMattheus/FrioVax/issues/16) mostram cartões passando por trabalho e revisão antes de `Pronto`. Na consulta de 14/10, a [issue #34](https://github.com/AndreMattheus/FrioVax/issues/34) estava em `Em Progresso`, e a [#61](https://github.com/AndreMattheus/FrioVax/issues/61) em `Backlog`, com data alvo de 15/10. Esses registros não bastam para concluir se os limites de WIP foram respeitados durante toda a sprint.

## Perguntas para discutir com os quatro integrantes

- O que as revisões dos PRs #40, #41 e #47 evitaram? Em que ponto uma conversa ou teste anterior teria reduzido espera ou retrabalho?
- Em quais períodos o quadro esteve próximo dos limites de WIP? Houve cartões parados ou revisões aguardando alguém? Consultar o histórico do Project antes de concluir.
- A documentação e os vídeos ficaram para o fim da sprint? Se sim, qual foi a sequência de decisões e impedimentos? Aplicar os **5 Porquês** ao problema principal e registrar as respostas, sem atribuir causas por suposição.
- Qual prática XP foi mais útil nesta sprint? O que deve continuar e o que precisa mudar?

## Registro da reunião de 15/10

Preencher com a equipe antes de considerar a retrospectiva concluída:

- **Participantes e horário:**
- **Fato principal e evidência:**
- **Cadeia dos 5 Porquês:**
- **Causa acordada:**
- **Efeito da revisão de código e do CI:**
- **Alteração no acordo de processo:** manter como está ou registrar a mudança concreta em [processo.md](processo.md).

| Ação acordada, verificável na Sprint 2 | Responsável | Prazo | Como verificar |
|---|---|---|---|
| A definir na reunião | A definir | A definir | A definir |

Registrar no máximo três ações. Criar cartões para as ações aprovadas e vinculá-los aqui. As métricas de fluxo exigidas pelo [guia de Processos](https://github.com/fmarquesfilho/processos-2026-2/blob/main/docs/SPRINT-1.md#métricas-de-fluxo) devem ser adicionadas quando a equipe fechar a sprint; não antecipar valores enquanto o trabalho ainda está em andamento.

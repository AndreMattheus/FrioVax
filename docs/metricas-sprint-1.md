# Métricas de fluxo da Sprint 1

**Consulta:** 15/10/2026, 17h48 (horário de Fortaleza). **Período observado:** 22/09 a 15/10/2026. Fontes: [FrioVax Project](https://github.com/users/AndreMattheus/projects/1), histórico das issues e PRs vinculados.

## Método e recorte

O recorte contém os **24 cartões técnicos `[S1-]` concluídos** até a consulta. A [issue #61](https://github.com/AndreMattheus/FrioVax/issues/61) está em `Em Progresso` e fica fora das médias até ser concluída. As histórias US01–US03 foram excluídas da contagem para não duplicar as tarefas técnicas que as implementaram.

- **Chegada:** evento `AddedToProjectV2` do cartão no Project.
- **Início:** primeira entrada observada em `Em Progresso` (7 cartões). Quando esse evento não aparece no histórico retornado pela API, usa-se a abertura do PR integrado vinculado como **aproximação** (17 cartões), indicada por `PR` na tabela. Trabalho anterior à abertura do PR não entra nessa aproximação.
- **Entrega:** fechamento da issue, que ocorreu junto à integração do PR e à passagem para `Pronto`; usado como aproximação uniforme do instante de entrega.
- **Lead time:** entrega menos chegada. **Cycle time:** entrega menos início. Dias corridos de 24 horas, calculados com timestamps completos e arredondados a duas casas; datas exibidas no horário de Fortaleza.
- **Throughput:** cartões técnicos fechados por semana civil, de segunda a domingo.

## Resultados

| Métrica | Resultado até 15/10 |
|---|---:|
| Cartões técnicos concluídos | 24 |
| Lead time médio / mediano | 11,68 / 10,19 dias |
| Cycle time médio / mediano | 1,35 / 0,86 dia |
| Maior lead time | [#34](https://github.com/AndreMattheus/FrioVax/issues/34): 22,35 dias |
| Maior cycle time | [#24](https://github.com/AndreMattheus/FrioVax/issues/24): 5,74 dias |

| Semana (Fortaleza) | Cartões concluídos |
|---|---:|
| 21–27/09 | 4 |
| 28/09–04/10 | 11 |
| 05–11/10 | 3 |
| 12–16/10, parcial até 15/10 | 6 |

## Dados por cartão

Na coluna Início, `quadro` significa transição observada para `Em Progresso`; `PR` significa abertura do PR integrado vinculado. Horários em Fortaleza; tempos em dias corridos.

| Cartão | Chegada | Início | Entrega | Lead | Cycle |
|---|---|---|---|---:|---:|
| [#12](https://github.com/AndreMattheus/FrioVax/issues/12) | 22/09 08:19 | 22/09 08:36 (quadro) | 24/09 22:27 | 2.59 | 2.58 |
| [#13](https://github.com/AndreMattheus/FrioVax/issues/13) | 22/09 08:22 | 26/09 12:23 (PR) | 27/09 14:10 | 5.24 | 1.07 |
| [#14](https://github.com/AndreMattheus/FrioVax/issues/14) | 22/09 08:26 | 26/09 12:24 (PR) | 26/09 15:02 | 4.27 | 0.11 |
| [#15](https://github.com/AndreMattheus/FrioVax/issues/15) | 22/09 08:27 | 26/09 19:08 (PR) | 27/09 16:30 | 5.34 | 0.89 |
| [#16](https://github.com/AndreMattheus/FrioVax/issues/16) | 22/09 08:28 | 28/09 11:27 (PR) | 28/09 18:07 | 6.40 | 0.28 |
| [#17](https://github.com/AndreMattheus/FrioVax/issues/17) | 22/09 08:29 | 26/09 13:34 (quadro) | 28/09 15:11 | 6.28 | 2.07 |
| [#18](https://github.com/AndreMattheus/FrioVax/issues/18) | 22/09 08:38 | 29/09 19:43 (PR) | 30/09 17:27 | 8.37 | 0.91 |
| [#19](https://github.com/AndreMattheus/FrioVax/issues/19) | 22/09 08:39 | 29/09 17:25 (PR) | 30/09 17:37 | 8.37 | 1.01 |
| [#20](https://github.com/AndreMattheus/FrioVax/issues/20) | 22/09 08:40 | 02/10 10:50 (PR) | 06/10 23:47 | 14.63 | 4.54 |
| [#21](https://github.com/AndreMattheus/FrioVax/issues/21) | 22/09 08:40 | 30/09 23:55 (PR) | 01/10 17:51 | 9.38 | 0.75 |
| [#22](https://github.com/AndreMattheus/FrioVax/issues/22) | 22/09 09:57 | 03/10 19:44 (PR) | 03/10 23:40 | 11.57 | 0.16 |
| [#23](https://github.com/AndreMattheus/FrioVax/issues/23) | 22/09 09:59 | 01/10 17:55 (PR) | 02/10 13:58 | 10.17 | 0.84 |
| [#24](https://github.com/AndreMattheus/FrioVax/issues/24) | 22/09 10:01 | 03/10 20:36 (quadro) | 09/10 14:18 | 17.18 | 5.74 |
| [#25](https://github.com/AndreMattheus/FrioVax/issues/25) | 22/09 10:02 | 02/10 13:41 (PR) | 02/10 14:41 | 10.19 | 0.04 |
| [#26](https://github.com/AndreMattheus/FrioVax/issues/26) | 22/09 10:07 | 02/10 12:47 (PR) | 02/10 14:33 | 10.18 | 0.07 |
| [#27](https://github.com/AndreMattheus/FrioVax/issues/27) | 22/09 10:08 | 03/10 00:12 (PR) | 03/10 10:44 | 11.02 | 0.44 |
| [#28](https://github.com/AndreMattheus/FrioVax/issues/28) | 22/09 10:15 | 02/10 15:05 (quadro) | 02/10 16:27 | 10.26 | 0.06 |
| [#29](https://github.com/AndreMattheus/FrioVax/issues/29) | 22/09 10:16 | 10/10 19:27 (PR) | 10/10 22:01 | 18.49 | 0.11 |
| [#30](https://github.com/AndreMattheus/FrioVax/issues/30) | 22/09 10:27 | 10/10 19:29 (quadro) | 13/10 17:15 | 21.28 | 2.91 |
| [#31](https://github.com/AndreMattheus/FrioVax/issues/31) | 22/09 10:28 | 13/10 17:17 (PR) | 13/10 19:19 | 21.37 | 0.08 |
| [#32](https://github.com/AndreMattheus/FrioVax/issues/32) | 22/09 10:29 | 13/10 18:16 (PR) | 14/10 09:38 | 21.96 | 0.64 |
| [#33](https://github.com/AndreMattheus/FrioVax/issues/33) | 22/09 10:34 | 10/10 01:44 (PR) | 12/10 15:49 | 20.22 | 2.59 |
| [#34](https://github.com/AndreMattheus/FrioVax/issues/34) | 22/09 10:37 | 13/10 13:41 (quadro) | 14/10 19:04 | 22.35 | 1.22 |
| [#54](https://github.com/AndreMattheus/FrioVax/issues/54) | 09/10 11:55 | 09/10 11:56 (quadro) | 12/10 16:59 | 3.21 | 3.21 |

## WIP e limite do histórico

Às **17h48 de 15/10**, entre as 28 issues da milestone Sprint 1, o Project mostrava **1 em `Em Progresso`** ([#61](https://github.com/AndreMattheus/FrioVax/issues/61)), **0 em `Em Revisão`** e **27 em `Pronto`**. O **WIP observado nesse instante é 1**, abaixo dos limites 4 e 2 do [acordo de processo](processo.md#fluxo-kanban-e-wip). As três histórias US01–US03 estão entre os 27 cartões em `Pronto`, mas não entram no throughput técnico acima.

O histórico de mudanças de status retornado pela API do GitHub não contém todas as entradas intermediárias de vários cartões: em alguns casos há uma saída de `Em Revisão` sem a entrada correspondente. Por isso, não é possível reconstruir com segurança o WIP de cada dia nem afirmar que os limites foram respeitados durante **toda** a sprint. Os [relatos da retrospectiva](retrospectiva-01.md#análise-das-causas-relatadas) registram que a equipe não acompanhou continuamente esses limites e identificam a espera por revisão como gargalo. A rotina acordada para a Sprint 2 trata esse problema.

A semana de 12–16/10 e a issue #61 devem ser atualizadas quando a sprint for encerrada; os números acima descrevem somente o estado consultado.


# Fluxo de valor da Sprint 2

Primeira versão do mapa de fluxo de valor da [#91](https://github.com/AndreMattheus/FrioVax/issues/91) (S2-28). Acompanha três tarefas da API, da chegada ao quadro até a publicação em produção, e sustenta o gargalo do processo com números. As regras de coleta, os tipos de intervalo e as fórmulas seguem o [plano de medição](medicao-sprint-2.md); os registros estão em [evidencias/sprint-2/](evidencias/sprint-2/README.md).

## Convenções

- Horas corridas de 3.600 segundos, calculadas com timestamps completos e arredondadas só na exibição. Os horários das tabelas estão em Fortaleza (UTC−03:00); os CSVs preservam o fuso da fonte.
- **Trabalho** é atenção humana relatada ou estimada; **automação** é a execução dos jobs do GitHub Actions, sem a fila do runner; **espera** é o tempo em que o item aguardava algo observável (fila do quadro, revisão, publicação). **Lacuna** é tempo sem registro e não vira espera nem trabalho por suposição.
- Intervalos concomitantes não são somados. Cada instante recebe uma única classificação, nesta ordem de precedência: processamento (`trabalho ∪ automação`), bloqueio, espera e lacuna. Assim, o CI que roda enquanto a autora trabalha conta uma vez, e o trabalho feito durante uma espera de revisão sai da espera.
- Estimativas (`precisao=estimado`) aparecem separadas dos relatos.

## Fluxo real da ideia à publicação

```mermaid
flowchart LR
    A[Planning: issue criada] --> B[Backlog / Sprint Backlog]
    B -->|espera| C[Em Progresso]
    C -->|trabalho| D[Commits e push]
    D -->|automação| E[CI do PR]
    E --> F[Pedido de revisão]
    F -->|espera| G[Review]
    G -->|ajustes: trabalho + CI| F
    G -->|aprovação| H[Merge em main]
    H -->|automação| I[CI de main]
    I -->|espera| J[Deploy: migração, Render, smoke]
    J -->|automação| K[Publicado em produção]
```

| Etapa | Classificação | Começa | Termina | Fonte |
|---|---|---|---|---|
| Fila do quadro | espera | chegada ao Project (`AddedToProjectV2`) | entrada em `Em Progresso` | timeline da issue |
| Desenvolvimento | trabalho e automação | entrada em `Em Progresso` | primeiro `review_requested` | relato da autora ou estimativa por commits; jobs do CI |
| Revisão | espera, com trabalho e CI nos ajustes | primeiro pedido de revisão | merge | eventos e reviews do PR |
| Integração e publicação | espera e automação | merge | publicação no ambiente `producao` | CI de `main`, deployment e jobs do workflow de deploy |

A publicação é o fim da etapa *Publicar SHA e esperar Render* do job que cria o GitHub deployment com status `success`; o smoke autenticado termina segundos depois no mesmo job. Merge e CI verde não contam como publicação. Para cada item, o deploy usado é o primeiro bem-sucedido cujo SHA contém o merge do PR.

## Amostra

A amostra continua sendo [#69](https://github.com/AndreMattheus/FrioVax/issues/69) (S2-05), [#70](https://github.com/AndreMattheus/FrioVax/issues/70) (S2-06) e [#80](https://github.com/AndreMattheus/FrioVax/issues/80) (S2-16), definida em [`amostra.csv`](evidencias/sprint-2/amostra.csv).

Nenhuma das três estava publicada em 08/11, e não havia como estar: o primeiro deploy bem-sucedido em produção aconteceu em 13/11, com a entrega da [#88](https://github.com/AndreMattheus/FrioVax/issues/88). Até essa data nenhuma tarefa da API tinha sido publicada, então a troca prevista para 08/11 não tinha substituta válida. As três foram publicadas antes desta apuração e foram mantidas sem substituição. O `amostra.csv` não muda.

## Gargalo

O gargalo é a **espera**, não o trabalho. Na Sprint 1, os cartões passaram quase todo o lead em fila antes de começar, e quase um em cada quatro PRs esperou mais de um dia pela primeira revisão. A espera por revisão é a maior espera **recorrente** que a equipe controla dentro do ciclo, e é nela que atua o experimento da [#92](https://github.com/AndreMattheus/FrioVax/issues/92).

### Espera até a primeira revisão — linha de base da Sprint 1

Definição usada na linha de base da #92: da abertura do PR (ou da primeira saída de rascunho) até a primeira review submetida (`APPROVED`, `CHANGES_REQUESTED` ou `COMMENTED`) por outra pessoa, excluindo bots e autorreview. Entram os PRs #36–#64 integrados em `main`.

```bash
python3 docs/scripts/medir-fluxo-de-valor.py revisao docs/evidencias/sprint-2/exportacoes/20261103T192538818629Z
```

| Medida | Resultado |
|---|---:|
| PRs integrados com review de outra pessoa | 26 (o #63 não foi integrado; o #54 e o #61 não são PRs) |
| Acima de 24 h corridas | **6 de 26 (23,1%)** |
| Mediana | **5,32 h** |
| Máximo | **62,06 h** ([#55](https://github.com/AndreMattheus/FrioVax/pull/55)) |

Os seis acima de 24 h: [#55](https://github.com/AndreMattheus/FrioVax/pull/55) 62,06 h, [#53](https://github.com/AndreMattheus/FrioVax/pull/53) 53,06 h, [#47](https://github.com/AndreMattheus/FrioVax/pull/47) 34,11 h, [#62](https://github.com/AndreMattheus/FrioVax/pull/62) 25,96 h, [#38](https://github.com/AndreMattheus/FrioVax/pull/38) 25,77 h e [#43](https://github.com/AndreMattheus/FrioVax/pull/43) 24,18 h. O comando reproduz exatamente os 23%, a mediana de 5,3 h e o máximo de 62,1 h registrados na #92 em 21/10.

### Espera no backlog — Sprint 1

Pela [apuração de 15/10](metricas-sprint-1.md#resultados), o lead time médio dos 24 cartões técnicos foi **11,68 dias** e o cycle time médio, **1,35 dia**. A diferença, **10,33 dias (88% do lead médio)**, é o tempo entre a chegada ao Project e o início do trabalho. Esses valores vêm da apuração de 15/10, com o método e as aproximações descritos lá; não foram recalculados neste corte.

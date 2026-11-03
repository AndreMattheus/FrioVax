# Plano de medição da Sprint 2

Este é o protocolo de coleta da [#66](https://github.com/AndreMattheus/FrioVax/issues/66). Os resultados finais pertencem à [#90](https://github.com/AndreMattheus/FrioVax/issues/90) e o mapa de três itens à [#91](https://github.com/AndreMattheus/FrioVax/issues/91). O registro começa em 03/11/2026; intervalos de trabalho anteriores sem fonte permanecem desconhecidos.

## Período e convenções

- Janela da sprint: **[21/10/2026 00h, 01/12/2026 00h)**, em `America/Fortaleza` (UTC−03:00). Início inclusivo, fim exclusivo. A janela planejada não comprova início de trabalho em 21/10.
- Cortes: primeira apuração em 20/11; corte para os vídeos em 26/11, indicando a hora exata; atualização final no hash da entrega (#97). Cada extração tem seu próprio início/fim e não representa uma fotografia transacional do GitHub.
- Semanas civis de segunda a domingo em Fortaleza; indicar as semanas parciais e o período em que o ambiente público esteve disponível.
- Guardar instantes ISO 8601 com fuso, por exemplo `2026-11-03T16:19:22-03:00`. As fontes do GitHub usam UTC (`Z`), preservado na exportação. Horas corridas de 3.600 segundos; dia de 24 horas. Calcular antes de arredondar.
- Ausência de informação é campo vazio ou `null`, nunca zero, data planejada, duração presumida ou presença inferida. Usar `precisao=observado`, `relatado` ou `estimado`; explicar estimativas e apresentá-las separadamente.

## Onde registrar

Tudo fica em [evidencias/sprint-2/](evidencias/sprint-2/README.md). Os CSVs usam UTF-8, cabeçalho, vírgulas e aspas em valores que contêm vírgula. IDs são estáveis; correções conservam a fonte e explicam o motivo no PR.

| Arquivo | Conteúdo e preenchimento |
|---|---|
| `intervalos.csv` | Um intervalo por etapa: issue, tipo, início, fim, autor, fonte, precisão e observação. Tipos: `trabalho`, `automacao`, `espera`, `bloqueio`. Fim vazio significa em curso. |
| `eventos.csv` | Marcos: `chegada`, `inicio`, `pedido_revisao`, `primeira_revisao`, `merge`, `conclusao`. O timestamp vem do evento, não do dia da extração. |
| `amostra.csv` | Três tarefas iniciais e seus responsáveis; substituições preservam a linha anterior e registram saída, motivo, fonte e nova entrada. |
| `implantacoes.csv` | Publicações, redeploys e rollbacks: ID do GitHub deployment, SHA, ambiente, resultado, horários, smoke, fontes e PRs incorporados. |
| `incidentes.csv` | Detecção, recuperação validada, implantações causais e de recuperação, autor e fontes. Campos causais desconhecidos ficam vazios com justificativa; atualizar após investigar. |
| `monitoramento.csv` | Períodos de acompanhamento do ambiente com responsável, evidência e cobertura (`efetiva`, `parcial`, `indisponivel`). Necessário para interpretar ausência de incidentes. |
| `exportacoes/<corte>/` | Metadados originais selecionados do GitHub, manifesto com comandos, horários, paginação, contagens e SHA-256. Não editar a exportação após fechar o corte. |

`autor` identifica quem registrou/executou a atividade; um job usa, por exemplo, `coletor-github`, sem atribuir seu tempo como trabalho humano. Em trabalho acompanhado, identificar a pessoa e a fonte do seu relato. Uma sessão de ferramenta não comprova atenção humana contínua.

### Passo a passo para cada autor

1. Ao começar de verdade, mover o cartão para `Em Progresso`, preencher **Start date** com a data real e abrir uma linha `trabalho` com início, autor e fonte (comentário na issue ou registro versionado). Fechar o intervalo ao pausar; reabrir com outro ID ao retomar. Não deduzir início/fim pelos horários dos commits.
2. Ao executar teste/build/coleta, registrar `automacao` com o período efetivo do job. A fila do runner é `espera`, não execução. Se trabalhar durante o job, registrar ambos; o cálculo usa a união temporal, sem somar a sobreposição.
3. Ao ficar impedido, aplicar `blocked`, registrar motivo, dono da remoção e início. Na liberação, registrar fonte e fim e remover a label. O cartão continua contando no WIP. Espera por revisão é `espera`; só aplicar `blocked` se houver impedimento real.
4. Ao pedir revisão, gravar o evento `pedido_revisao` e abrir `espera`/etapa `revisao`. Usar o evento `review_requested` efetivo; abertura do PR só serve como aproximação explicitamente marcada quando não houver evento. PR em draft ainda não é pedido de revisão.
5. Na primeira review humana de outra pessoa, registrar `primeira_revisao` e fechar a espera. Contam reviews submetidas (`APPROVED`, `CHANGES_REQUESTED` ou `COMMENTED`); excluir bot, autorreview e comentário comum. Não confundir primeira review com aprovação final. Ciclos posteriores recebem novos intervalos.
6. Registrar merge, conclusão da DoD e publicação como eventos distintos. **Finished date** só recebe a data da DoD efetivamente cumprida. Fechamento administrativo da issue não comprova integração nem publicação. Preservar Planned start/Target date como planejamento.
7. Vincular cada registro à URL do evento/job/PR ou a `caminho/do/snapshot.json#/ponteiro/JSON`. Executar o validador antes do PR. Se faltar informação, deixar a lacuna explícita; não completar a jornada por suposição.

No Project, reaproveitar **Planned start**, **Target date**, **Start date** e **Finished date** existentes. São campos de data, sem hora; o timestamp completo e sua fonte ficam nos CSVs/eventos. Uma mudança para `Em Progresso` é fonte do início do fluxo; não comprova horas ativas ininterruptas.

### Amostra e orientação

| Código | Issue | Autor responsável | Coleta desde o início |
|---|---|---|---|
| S2-05 | [#69](https://github.com/AndreMattheus/FrioVax/issues/69) | Bianca (`biancammedeiros`) | política e migração de câmaras |
| S2-06 | [#70](https://github.com/AndreMattheus/FrioVax/issues/70) | Bianca (`biancammedeiros`) | persistência de leituras e ocorrências |
| S2-16 | [#80](https://github.com/AndreMattheus/FrioVax/issues/80) | Leonardo (`leonardonadson`) | autenticação e sessões |

Cada autor segue o passo a passo acima, aponta seus PRs e guarda os SHAs para localizar a primeira implantação que os contém. Trabalho anterior ao protocolo precisa de fonte contemporânea ou relato identificado; o coletor não o reconstrói a partir de commits. A seleção não afirma que as tarefas já foram concluídas/publicadas. Se alguma não estiver publicada até **08/11**, a #91 deve registrar a substituição por outra tarefa da API publicada, com motivo e fonte **antes** da apuração. Não substituir silenciosamente nem inventar publicação para cumprir a data.

## Definição de implantação e incidentes

Uma implantação contável é uma **publicação concluída da API em ambiente público**, de um SHA conhecido, com smoke público aprovado e GitHub deployment no ambiente **`producao`** com status final **`success`**. A fonte do smoke e seu horário devem estar vinculados ao evento. Essa integração será entregue em #88; este protocolo não a declara existente.

- **Merge**, push e build/CI validam etapas do desenvolvimento; não comprovam publicação. Antes do primeiro ambiente público pode haver uma série separada de **proxy: merge em main com CI verde**. Nunca concatená-la à série de implantação real.
- Duplicatas de webhook/workflow da mesma publicação contam uma vez, pelo ID do evento de publicação. O mesmo SHA só conta novamente se houver redeploy/rollback real identificado e justificado; não deduplicar indiscriminadamente todo SHA, pois rollback pode reutilizar um SHA antigo.
- Registrar redeploy e rollback como eventos próprios, com tipo, SHA e vínculo ao incidente quando houver. Publicação falha é preservada com seu resultado, sem entrar na frequência de implantações bem-sucedidas. Falha de CI antes de publicar não é incidente de produção.
- Em cada publicação, preservar a lista de PRs/commits efetivamente contidos no artefato. Para squash, guardar os commits originais do PR e o merge SHA. Um deploy não pode incorporar PR integrado depois dele.
- Incidente tem ID, implantação causal quando identificada, detecção e recuperação confirmada por smoke/health público. Correção/rollback vincula o evento de recuperação. Sem causa confirmada, registrar a investigação e não inventar associação.
- CSV vazio significa **nenhum incidente registrado nesta coleta**, não ausência comprovada de falhas. Só usar `0/N` se `N>0` e houver cobertura efetiva de monitoramento sobre as implantações avaliadas. Com `N=0`, taxa indefinida; sem incidente, recuperação **não observável**, nunca zero.

## Fórmulas a aplicar posteriormente

Todos os intervalos são limitados à janela e ao item em análise. `U` significa duração da **união** dos intervalos, fundindo sobreposições; não é soma de tempos concomitantes.

| Medida | Fórmula / fonte |
|---|---|
| Lead time do cartão | `conclusao − chegada ao Project`. Na comparação com a Sprint 1, manter o [método e recorte técnico](metricas-sprint-1.md#metodo-e-recorte); fechamento só é aproximação se coincidir com integração/DoD, declarando divergências. |
| Cycle time | `conclusao − primeiro início real`. Priorizar evento do quadro; na ausência, abertura do PR integrado é aproximação identificada, como na Sprint 1. Não substituir por Planned start. |
| Throughput | Número de tarefas técnicas concluídas por semana civil; excluir histórias agregadoras e tarefas de documentação/processo, conservando população comparável. Apresentar separadamente o total geral. |
| Espera até primeira review | `primeira review válida − primeiro pedido válido`, por PR. Ausência de review deixa observação aberta, fora da distribuição de esperas concluídas; mostrar quantidade e idade das abertas no corte. |
| Trabalho / automação | `U(trabalho)` e `U(automacao)` apresentados separadamente. Tempo de job é sua execução, não todo o intervalo do workflow. |
| Processamento do item | `U(trabalho ∪ automacao)` entre chegada e publicação. Informar a participação automática; não atribuí-la à pessoa. |
| Espera / bloqueio | `U(espera ∪ bloqueio)`; apresentar bloqueio também como subconjunto anotado, sem somá-lo duas vezes. Tempo sem classificação fica como lacuna, não espera inferida. |
| Eficiência de fluxo (#91) | `100 × processamento / (publicacao − chegada)` para item efetivamente publicado, com denominador positivo. Dados incompletos geram limite/resultado parcial identificado. Apresentar cada item e a média aritmética das três eficiências; não chamar razão dos totais de média por item. |
| Frequência de implantação | Número de publicações bem-sucedidas distintas por semana civil; indicar semanas parciais e períodos sem ambiente. |
| Tempo da mudança até implantação | `primeira implantação que contém o PR − primeiro commit original da mudança no PR`; conferir timestamps, preservar fonte do squash e sinalizar inconsistências de relógio. Mediana e percentis só com amostra e método declarados. |
| Taxa de falha de mudança | `100 × implantações avaliadas que causaram incidente / implantações avaliadas`, com denominador e cobertura explícitos. Contar uma implantação causal uma vez, mesmo com vários incidentes. |
| Recuperação | `recuperação validada − detecção`, por incidente, relacionando correção/rollback. Sem recuperação até o corte, observação em aberto. |

Não calcular média de intervalos ainda abertos; apresentar sua idade no corte. Timestamps completos são a entrada; datas do Project sozinhas não permitem precisão em horas. As fórmulas aqui são o contrato da coleta, sem resultados DORA antecipados ou inferências de produtividade individual.

## Coleta reproduzível

Pré-requisitos: Python 3.11+ e GitHub CLI autenticado com acesso de leitura ao repositório e ao Project. O script só lê o GitHub; não cria eventos, incidentes ou deployments.

```bash
python3 docs/scripts/coletar-medicao-sprint-2.py
python3 docs/scripts/validar-medicao-sprint-2.py
```

O coletor usa `gh api --paginate --slurp`, `per_page=100` nos endpoints REST e cursores/pageInfo no GraphQL. Preserva campos necessários sem corpos de comentários, patches, e-mails, payloads, logs, variáveis ou credenciais. O manifesto lista os comandos efetivamente executados, horário de cada requisição, páginas, quantidade, arquivos e hashes. Erro de API/paginação impede publicar um corte aparentemente completo; a pasta temporária fica identificada como incompleta. Projetos privados exigem permissão real, sem substituição por dados inventados.

As consultas incluem PRs abertos/fechados contra `main`, commits/reviews/eventos dos PRs relevantes à janela (e #36–#64 para a linha de base), execuções CI, deployments de `producao` e seus statuses, issues da sprint, incidentes e eventos de chegada/status das tarefas. Os dados anteriores à janela são preservados como contexto; filtrar pelo timestamp do evento ao apurar. A fotografia dos campos do Project não é histórico; eventos de status podem ser incompletos, como na Sprint 1.

Para conferir paginação contra uma segunda extração com páginas menores:

```bash
python3 docs/scripts/coletar-medicao-sprint-2.py --verificar-paginacao CAMINHO_DO_CORTE
```

Essa conferência compara IDs de PRs de uma extração paginada com `per_page=1`; se houver mudança concorrente na população, preservar a divergência e repetir com um novo corte. O validador confere esquemas, fontes locais, timestamps, ordem temporal e hashes do snapshot. Nenhum comando imprime o token; não usar `gh auth token`, `--verbose`, logs de runner ou dumps de ambiente como evidência. Revisar o diff antes de versionar.

## Primeiro registro e limites

A #66 é o item real usado para exercitar o protocolo no início da execução coletiva. O evento do Project registra entrada em `Em Progresso` em **03/11/2026 às 16h19min22s de Fortaleza** (`2026-11-03T19:19:22Z`). O coletor preserva esse evento, o início/fim da própria execução automática e suas fontes. Essa execução não é declarada como horas de trabalho humano, implantação ou conclusão da #66.

O período planejado começou antes da coleta. Não há cobertura retroativa de trabalho ativo ou incidentes; a jornada da amostra até produção será completada pelos autores e por #88/#91. Arthur deve revisar o protocolo e reproduzir a validação; aprovação e integração continuam sendo condições da DoD.

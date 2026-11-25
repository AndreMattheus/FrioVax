# Métricas da Sprint 2 — atualização de 25/11 da #90

Referência acadêmica **P-T5**. Responsável: Arthur; revisão e reprodução
independente: Leonardo. Protocolo: [plano de medição](medicao-sprint-2.md).
Este corte atualiza os dados após o encerramento do desenvolvimento informado
por Arthur. Referência técnica: `main` em
[`f762795b8e3c83135f02b73ccfbe2715af369eab`](https://github.com/AndreMattheus/FrioVax/commit/f762795b8e3c83135f02b73ccfbe2715af369eab).
O corte de **26/11 para vídeos** e a conferência do **hash de entrega na #97**
continuam pendentes; esta execução não os antecipa.

## Janela, corte e fontes

Janela: **[2026-10-21T00:00:00-03:00, 2026-12-01T00:00:00-03:00)**.
Corte exclusivo: **2026-11-25T18:57:00-03:00** (`2026-11-25T21:57:00Z`).
Semanas civis de segunda a domingo em Fortaleza (UTC−03:00). A primeira
observa só 21–25/10; a última observa 23–25/11 até 18h57 e é parcial.

Extração em **25/11, das 18:57:53 às 19:01:35 de Fortaleza**:
[snapshot imutável](evidencias/sprint-2/exportacoes/20261125T220135349129Z/manifesto.json),
**678 consultas paginadas**, com comandos, horários, páginas, contagens e
SHA-256 por arquivo. Inclui PRs, commits originais, reviews, eventos,
statuses dos commits, runs/jobs e passos, deployments/statuses de `producao`,
ancestralidade por SHA, issues, incidentes e Project. Preservam-se campos
selecionados e IDs originais, sem tokens, logs, patches, e-mails, payloads
ou credenciais do provedor.

Fontes complementares:

- [CSV locais congelados](evidencias/sprint-2/metricas-02/fontes-20261125/manifesto.json): incidentes e monitoramento só têm cabeçalho. O CSV de implantações tem apenas duas publicações da amostra #91; o universo completo vem dos deployments/jobs.
- [Metadados dos ambientes](evidencias/sprint-2/metricas-02/ambiente-20261125/manifesto.json): ID, nome, datas e proteção; sem consultar variáveis ou segredos. É fotografia atual da configuração.
- [Estado de main e das issues #90/#97](evidencias/sprint-2/metricas-02/contexto-20261125/manifesto.json): comandos, horários e hashes. Ambas estavam abertas; o SHA de main não foi declarado hash final de submissão.
- [Resultado completo](evidencias/sprint-2/metricas-02/20261125/resultado.json), [tabelas](evidencias/sprint-2/metricas-02/20261125/tabelas.md) e [contagens diárias, inclusive zeros](evidencias/sprint-2/metricas-02/20261125/dias.csv).

## Histórico dos cortes

| Corte exclusivo (Fortaleza) | Extração usada | Publicações | PRs publicados | Cartões técnicos |
|---|---|---:|---:|---:|
| 21/11 00h, fim de 20/11 | reconstruído em 23/11 | 11 | 19 | 11 |
| 23/11 18h50 | 23/11 | 16 | 24 | 16 |
| 25/11 18h57 | 25/11 | 21 | 29 | 20 |

O [resultado de 20/11](evidencias/sprint-2/metricas-02/20261120/resultado.json)
é reconstrução posterior: a primeira apuração ocorreu em 23/11 e não comprova
cumprimento do prazo de 20/11. O
[relatório de 23/11](evidencias/sprint-2/metricas-02/20261123/relatorio.md),
resultados e snapshots anteriores foram preservados. Os 24 PRs anteriores
conservam seus tempos; cinco novos PRs ampliam a população atual.

## Definições e filtros

| Medida | Definição, população e denominador |
|---|---|
| Frequência de implantação | Publicações públicas distintas por semana civil ou dia observado. Exige `producao`, status operacional `success`, publicação Render e smoke autenticado aprovados. Denominador temporal: semana/dia ou fração indicada; não tratar frações como períodos completos. |
| Tempo da mudança até implantação | Por PR integrado em `main` na janela e publicado: `primeiro success público contendo o merge SHA − menor authored_at dos commits originais do PR`, em horas corridas; n=29. PR sem publicação ficaria listado e fora da distribuição. Merge SHA e commits originais preservam a rastreabilidade após squash. |
| Taxa de falha de mudança | `100 × publicações avaliadas causais de incidente / publicações avaliadas`. Contar uma publicação causal uma vez, mesmo com vários incidentes. Exigir cobertura efetiva, responsável e fonte, contínua da publicação ao corte. CI ou tentativa de publicação falha não implica incidente em produção. |
| Recuperação | Recuperação validada menos detecção, por incidente. Sem incidente registrado: não observável, nunca tempo zero. |
| Alternativa auxiliar: retrabalho em revisão | `100 × PRs integrados com review humana CHANGES_REQUESTED antes do merge / PRs integrados com review humana preservada antes do merge`. Janela pelo merge; excluir bot e autorreview. Mede solicitação explícita de ajuste, não esforço ou falha em produção. |
| Lead time de cartão | Fechamento menos chegada ao Project, em dias corridos de 24 horas. Método da Sprint 1; fechamento é aproximação de entrega, não publicação. |
| Cycle time de cartão | Fechamento menos primeira entrada observada em `Em Progresso`; sem o evento, abertura do PR integrado vinculado como aproximação identificada. Não usar datas planejadas. |
| Throughput | Cartões técnicos fechados por semana civil, com PR integrado vinculado. Excluir histórias agregadoras e processo/documentação; total geral de tarefas S2 aparece separado. |
| Espera até primeira review | Primeira review válida submetida por outra pessoa menos primeiro pedido após o PR estar pronto. Sem pedido, abertura/saída de draft seria aproximação; neste corte todos os 29 têm pedido. Não mede ciclos posteriores até aprovação. |

A seleção inicial é frequência, tempo de mudança e taxa de falha. A terceira
segue **não observável**. Retrabalho em revisão aparece separado e não recebe
o nome de DORA. O proxy de entregas corretivas ligadas a falhas em produção
também exigiria vínculos causais ausentes: não inferir correção de produção
de `fix`, de uma review ou de uma tentativa de deploy falha.

Entram somente PRs integrados, não draft, contra `main`, com `merged_at`
na janela. As provas de falha não integradas
[#122](https://github.com/AndreMattheus/FrioVax/pull/122) e
[#126](https://github.com/AndreMattheus/FrioVax/pull/126) ficam fora.
Merge, push, build e abertura de deployment não são implantação. Não foi
calculada série proxy de merges com CI verde; ela precisaria ficar separada.

## Publicações e auditoria

**50 registros de deployment representam 25 tentativas**, com dois registros
por job: o automático do environment e o explícito do script. **42 registros
com sucesso correspondem a 21 publicações reais**; quatro tentativas falhas
têm oito registros. Elas ficam na auditoria, fora da frequência e sem serem
convertidas em incidentes por suposição.

A associação deployment→job exige mesmo SHA, criação dentro do intervalo do
job, tolerância de 30 segundos antes do início e passo `Registrar deployment`.
Ambiguidade aborta. Dentro do job, o registro explícito, criado depois do
automático, representa o evento. O término do job serve à associação; o
marco da implantação é o **`success` do deployment após Render e smoke**,
sem exigir término do job. Ambos os passos devem ter terminado com sucesso
antes do corte. `inactive` posterior não apaga sucesso histórico; outro
status operacional posterior prevaleceria.

No primeiro evento, smoke terminou em `2026-11-13T17:53:50Z`, sucesso foi
registrado às `17:53:51Z` e job terminou às `17:53:53Z`. O corte exclusivo
`17:53:51Z` exclui a publicação; `17:53:52Z` já a inclui uma vez. Esse limite
está no teste solicitado por Leonardo no PR #125.

São **20 SHAs e 21 eventos**. O deployment **7773456323** é um redeploy do
SHA inicial em outro [job com nova publicação Render e smoke](https://github.com/AndreMattheus/FrioVax/actions/runs/43484767266/job/132090186919).
Essa execução justifica contar o SHA novamente; o par automático 7773453502
não conta outra vez. Não há vínculo de incidente registrado para esse
redeploy nem rollback identificado.

O último evento, **8061253480**, publicou `f762795` em **25/11 às 12:05:56
de Fortaleza**, com [smoke aprovado](https://github.com/AndreMattheus/FrioVax/actions/runs/45640659673/job/139298031758).
Os cinco novos PRs publicados são #125, #127, #128, #129 e #130. Todos os
29 PRs elegíveis foram publicados antes do corte.

O JSON audita os 50 IDs, estados, jobs, inclusões/exclusões, tipos, SHAs,
smoke e PRs/commits incorporados. A inclusão usa o grafo completo de
ancestrais do SHA publicado, não apenas a ordem das datas. Grafo truncado
ou PR integrado depois da publicação que o contém aborta a conta.
**Não há commit novo sem PR conhecido após a carga inicial**. Nela há três
commits da base de 04/09, anteriores à janela:
`55cfb0815d0b36b5fc70f17f24c1b570b2385369`,
`a64f659f8ed47896c58c34f3f47c062f50f39f14` e
`b2a1f602db8b87bb158181b87f36961468cfb416`.
`commits_contidos`, `commits_novos` e `commits_novos_sem_pr_conhecido`
preservam a conferência por publicação, inclusive a base anterior à sprint.

## Resultados e interpretação

Frequência semanal: **0, 0, 0, 6, 8 e 7**; extremidades parciais. A primeira
publicação comprovada foi em **13/11 às 14:53:51 de Fortaleza**. Antes disso
há ausência de publicação comprovada, não prova de lentidão do desenvolvimento
ou indisponibilidade contínua. O CSV diário inclui **36 dias observados,
25 sem publicação**; após a primeira, os dias sem evento foram **16 e 19/11**.
25/11 é parcial. Dias sem publicar não medem indisponibilidade.

Tempo de mudança: **mediana 44,38 h**, média **82,08 h**, n=29, contra
52,61 h e 94,86 h nos 24 PRs anteriores. A coorte inicial de dez PRs
publicados juntos mantém mediana **189,69 h**; os 19 seguintes têm mediana
**20,85 h**. Para esses 19, merge até publicação tem mediana **6,02 minutos**.
A cauda inicial inclui espera pela infraestrutura e formação do primeiro
lote; ampliar a população também muda a distribuição. A queda não prova
por si só melhoria de produtividade ou causalidade.

Espera até primeira review: mediana **3,52 h**, média **8,85 h**, máximo
**43,81 h**; **3/29** esperaram mais de 24 h. Ela permite discutir a fila
de revisão, mas não mede todos os ciclos de ajuste ou esforço ativo.
Tempo entre commits ou tempo do job não equivale a trabalho humano.

**Taxa de falha: não observável**. Há N=21 publicações comprovadas, mas
**N avaliado=0**, sem cobertura efetiva preservada; numerador e taxa são
`null`. A consulta de incidentes e o CSV não retornaram registros, o que
não autoriza `0/21`. Com cobertura efetiva, N positivo e nenhum incidente
seria `0/N`; com N=0, indefinida. Recuperação continua **não observável**,
sem tempo zero. Smoke comprova o instante de publicação, não acompanhamento
contínuo. Incidentes abertos não devem entrar nas durações concluídas.

Retrabalho em revisão: **12/29 = 41,38%**. Há `CHANGES_REQUESTED` humano
preservado nos PRs #101, #102, #103, #109, #110, #112, #119, #123, #124,
#125, #128 e #130; 17 não têm esse estado preservado. A distribuição binária
não mede código refeito, gravidade ou ausência de ajustes em comentários.
Reviews dispensadas podem perder o estado original; os estados são os da
extração.

Percentis usam **nearest rank**: ordenar e selecionar `x[ceil(p × n) − 1]`.
Mediana usual, média aritmética e tempos calculados com timestamps completos
antes do arredondamento. As tabelas mostram n, média, mediana, p85, p95,
extremos e dados individuais por PR/cartão.

## Fluxo e comparação com a Sprint 1

Mantém-se o [método da Sprint 1](metricas-sprint-1.md). População técnica S2:
**#68–#86, #88, #89 e #99**, incluindo contratos executáveis, testes,
containers, CI/proteção e publicação. ADRs #67/#87, medição #66, planning
#65, documentação, cerimônias, experimentos e US04–US10 ficam fora.
**#83 e #89 ainda estavam abertas** e não entram nos concluídos, mesmo após
o relato de encerramento do desenvolvimento. O total geral é de 27 tarefas S2
fechadas, separado do total técnico.

| Medida | Sprint 1 | S2 até 23/11 | S2 até 25/11 |
|---|---:|---:|---:|
| Cartões técnicos concluídos | 24 | 16 | 20 |
| Lead médio / mediano (dias) | 11,68 / 10,19 | 23,32 / 23,72 | 25,03 / 25,56 |
| Cycle médio / mediano (dias) | 1,35 / 0,86 | 4,56 / 3,41 | 3,90 / 2,74 |
| Início pelo quadro / aproximação por PR | 7 / 17 | 16 / 0 | 20 / 0 |

Os 20 cartões têm merge vinculado anterior ao fechamento, com diferença
máxima de dois segundos; nenhuma lacuna excluiu outro cartão técnico fechado.
Muitos chegaram juntos em 23/10 e começaram depois: parte do lead é backlog.
Concluir cartões antigos pode aumentar o lead sem aumentar esforço ativo;
cycle também não mede dedicação contínua.

A S1 incluiu documentação técnica #34; a S2 separa #93 e mantém CI/proteção
como engenharia. Populações, cobertura dos inícios e janelas diferem; não
comparar os totais como períodos e trabalhos iguais. Os dados subsidiam
a retrospectiva #95 e não devem ranquear integrantes.

## Reprodução e validação

Python 3.11+ basta para reprodução offline; nova coleta exige GitHub CLI
autenticado com leitura do repositório/Project. O manifesto guarda os 678
comandos reais, inclusive GraphQL com cursores/pageInfo. REST usa
`gh api --paginate --slurp` e `per_page=100`, incluindo:

```text
repos/AndreMattheus/FrioVax/pulls?state=all&base=main&per_page=100
repos/AndreMattheus/FrioVax/actions/runs?per_page=100
repos/AndreMattheus/FrioVax/deployments?environment=producao&per_page=100
repos/AndreMattheus/FrioVax/issues?state=all&labels=incidente&per_page=100
```

`state=all` preserva exclusões auditáveis; o cálculo filtra merges. Commits,
reviews, jobs, statuses e vínculos também são paginados. Erro impede declarar
o snapshot completo. Comandos executados, a partir da raiz:

```powershell
python docs/scripts/coletar-medicao-sprint-2.py
python docs/scripts/coletar-medicao-sprint-2.py --preservar-fontes docs/evidencias/sprint-2/metricas-02/fontes-20261125
python docs/scripts/coletar-medicao-sprint-2.py --coletar-ambiente docs/evidencias/sprint-2/metricas-02/ambiente-20261125
python docs/scripts/coletar-medicao-sprint-2.py --verificar-paginacao docs/evidencias/sprint-2/exportacoes/20261125T220135349129Z
python docs/scripts/calcular-metricas-02.py docs/evidencias/sprint-2/exportacoes/20261125T220135349129Z --corte 2026-11-25T21:57:00Z --fontes docs/evidencias/sprint-2/metricas-02/fontes-20261125 --saida docs/evidencias/sprint-2/metricas-02/20261125
python docs/scripts/validar-medicao-sprint-2.py
python -m unittest discover -s docs/scripts -p test_medicao_sprint_2.py -v
python -m unittest discover -s docs/scripts -p test_metricas_02.py -v
python -m unittest discover -s docs/scripts -p test_fluxo_de_valor.py -v
```

O contexto remoto foi consultado por `gh api` em `commits/main`, `issues/90`
e `issues/97`; comandos e saídas selecionadas estão no manifesto complementar.
A [conferência com páginas de um PR](evidencias/sprint-2/validacao/paginacao-20261125T220135349129Z.json)
compara IDs com o snapshot. O [script](scripts/calcular-metricas-02.py)
confere hashes antes das contas e gera `resultado.json`, `tabelas.md` e agora
também `dias.csv`. Reproduzir o comando com **outra pasta em `--saida`** e
comparar os três arquivos por `git diff --no-index`: devem ser idênticos.
Uma nova extração atual não substitui o snapshot do mesmo corte.

Verificação local: **38 testes passaram** (10 da apuração, 11 da coleta e
17 do fluxo de valor). O validador confirmou os hashes dos quatro snapshots;
a reconsulta paginada retornou os mesmos **60 IDs de PRs em 60 páginas**.

Os testes verificam resultados antigos/atuais completos, os 24 PRs anteriores
sem alteração de tempos, cinco novas mudanças, 50 registros/21 eventos,
exclusão de #122/#126, ancestralidade, ausência de observação, `0/N` com
cobertura efetiva, corte após smoke antes do término do job, dias com zeros
e limites parciais. Incidentes sintéticos só existem nas pastas temporárias
dos testes.

## Limitações e próximos cortes

A coleta não é transacional. O corte precede a extração, mas título, vínculos,
draft, `closed_at` e estados de review são fotografias atuais; reaberturas
e edições podem limitar reconstruções históricas. Eventos do Project podem
ser incompletos. Datas de commits podem refletir rebase/relógio local e não
medem esforço. Mudanças incluem processo/documentação publicados com o
artefato, não só funcionalidades da API. A associação temporal deployment→job
é um limite: o coletor não preserva `log_url`, para evitar URLs privadas.

Leonardo confirmou a reprodução dos **dois cortes anteriores** na
[revisão do PR #125](https://github.com/AndreMattheus/FrioVax/pull/125#pullrequestreview-6041835379).
Após a troca da tarefa de Matheus para Arthur, reprodução/revisão cabem a
Leonardo, sem pendência de Bianca. **O novo corte precisa da reprodução por
Leonardo**: a confirmação anterior não prova os dados novos. Conferir
21 publicações, 29 PRs, 20 cartões, semanas 6/8/7, os PRs #125/#127/#128/#129/#130
e deployment 8061253480; comparar os arquivos e registrar data/resultado no PR.

Se surgirem incidentes, preservar detecção, recuperação, causa, fontes e
monitoramento; conferir o deployment causal sem inventar associação.
Congelar novos CSVs e recalcular. Sem fontes, manter taxa/recuperação não
observáveis.

**26/11:** repetir a coleta nessa data, com corte real anterior ao início,
CSV/ambiente e saída em diretórios novos; atualizar e obter reprodução
independente. **S2-34/#97:** repetir no hash efetivamente entregue e registrar
relação com publicação pública, checks e auditoria da entrega. PRs de
documentação também podem gerar deploy: este corte não inclui o futuro merge
deste próprio relatório.

O PR de atualização usa **`Refs #90`**. `Closes #90` cabe à entrega que concluir
as etapas. Revisão, CI verde, integração e datas reais no Project continuam
condições da [Definição de Pronto](processo.md#definição-de-pronto).
Não foram declarados aprovação, integração deste PR ou submissão final.

## Tabelas do corte de 25/11

<!-- INICIO_TABELAS_GERADAS -->

<!-- Gerado por calcular-metricas-02.py; não editar números manualmente. -->
| Semana (segunda, Fortaleza) | Parcial | Publicações | Throughput técnico | Tarefas S2 fechadas (geral) |
|---|---|---:|---:|---:|
| 2026-10-19 | sim | 0 | 0 | 0 |
| 2026-10-26 | não | 0 | 0 | 0 |
| 2026-11-02 | não | 0 | 3 | 7 |
| 2026-11-09 | não | 6 | 5 | 5 |
| 2026-11-16 | não | 8 | 6 | 8 |
| 2026-11-23 | sim | 7 | 6 | 7 |

| Distribuição | n | Média | Mediana | p85 | p95 | Mínimo | Máximo |
|---|---:|---:|---:|---:|---:|---:|---:|
| Tempo de mudança (h) | 29 | 82.08 | 44.38 | 211.05 | 238.52 | 2.27 | 238.75 |
| Lead de cartão (d) | 20 | 25.03 | 25.56 | 31.58 | 31.70 | 13.53 | 32.50 |
| Cycle de cartão (d) | 20 | 3.90 | 2.74 | 6.03 | 11.76 | 0.10 | 13.72 |
| Espera até review (h) | 29 | 8.85 | 3.52 | 19.92 | 29.36 | 0.01 | 43.81 |

| Deployment (tipo) | SHA | Publicado (UTC) | Job com smoke | PRs da sprint na primeira publicação |
|---|---|---|---|---|
| 7772377365 (publicacao) | `c51e2981773f` | 2026-11-13T17:53:51Z | [132062290267](https://github.com/AndreMattheus/FrioVax/actions/runs/43476705265/job/132062290267) | #100, #101, #102, #103, #104, #105, #106, #107, #108, #109 |
| 7773456323 (redeploy) | `c51e2981773f` | 2026-11-13T18:42:18Z | [132090186919](https://github.com/AndreMattheus/FrioVax/actions/runs/43484767266/job/132090186919) | nenhum novo PR |
| 7790932145 (publicacao) | `e7cc742a9c50` | 2026-11-14T17:14:34Z | [132522421635](https://github.com/AndreMattheus/FrioVax/actions/runs/43619720406/job/132522421635) | #110 |
| 7796109019 (publicacao) | `16ead1abbcbc` | 2026-11-15T00:30:45Z | [132641719152](https://github.com/AndreMattheus/FrioVax/actions/runs/43658303169/job/132641719152) | #111 |
| 7804581655 (publicacao) | `1236585cf6fc` | 2026-11-15T14:10:52Z | [132837729960](https://github.com/AndreMattheus/FrioVax/actions/runs/43722642492/job/132837729960) | #112 |
| 7812019513 (publicacao) | `33e7cb67c0f8` | 2026-11-15T23:54:37Z | [133005437429](https://github.com/AndreMattheus/FrioVax/actions/runs/43776998423/job/133005437429) | #113 |
| 7859584241 (publicacao) | `b554623b7acb` | 2026-11-17T16:38:04Z | [134195051965](https://github.com/AndreMattheus/FrioVax/actions/runs/44127464969/job/134195051965) | #115 |
| 7859789219 (publicacao) | `1cf80c4f0e7d` | 2026-11-17T16:45:47Z | [134200358890](https://github.com/AndreMattheus/FrioVax/actions/runs/44128941726/job/134200358890) | #114 |
| 7886615972 (publicacao) | `89ed2539ec6e` | 2026-11-18T14:49:38Z | [134884012224](https://github.com/AndreMattheus/FrioVax/actions/runs/44327893740/job/134884012224) | #117 |
| 7892213738 (publicacao) | `12010998f20b` | 2026-11-18T18:21:29Z | [135027969677](https://github.com/AndreMattheus/FrioVax/actions/runs/44368322350/job/135027969677) | #118 |
| 7932635568 (publicacao) | `2b324b0610f7` | 2026-11-20T03:42:24Z | [136065857492](https://github.com/AndreMattheus/FrioVax/actions/runs/44669210609/job/136065857492) | #116 |
| 7964667958 (publicacao) | `b602ba1f1880` | 2026-11-21T14:30:59Z | [136875670254](https://github.com/AndreMattheus/FrioVax/actions/runs/44914026208/job/136875670254) | #120 |
| 7966723754 (publicacao) | `287e3d9b767f` | 2026-11-21T17:23:10Z | [136922588402](https://github.com/AndreMattheus/FrioVax/actions/runs/44929428552/job/136922588402) | #119 |
| 7984938114 (publicacao) | `d4125bb24c7e` | 2026-11-22T21:03:06Z | [137353005813](https://github.com/AndreMattheus/FrioVax/actions/runs/45069476786/job/137353005813) | #121 |
| 8004884488 (publicacao) | `291bd729e732` | 2026-11-23T16:43:45Z | [137848567331](https://github.com/AndreMattheus/FrioVax/actions/runs/45219212171/job/137848567331) | #123 |
| 8008807028 (publicacao) | `44e310bc21e8` | 2026-11-23T19:30:45Z | [137953777568](https://github.com/AndreMattheus/FrioVax/actions/runs/45249139211/job/137953777568) | #124 |
| 8034910149 (publicacao) | `c9549bb8fe98` | 2026-11-24T17:06:29Z | [138622812379](https://github.com/AndreMattheus/FrioVax/actions/runs/45444056306/job/138622812379) | #129 |
| 8036763065 (publicacao) | `1f67f68faf37` | 2026-11-24T18:19:29Z | [138671163985](https://github.com/AndreMattheus/FrioVax/actions/runs/45457607687/job/138671163985) | #127 |
| 8039072407 (publicacao) | `28b76555eea9` | 2026-11-24T19:57:48Z | [138733018300](https://github.com/AndreMattheus/FrioVax/actions/runs/45475076106/job/138733018300) | #128 |
| 8039362068 (publicacao) | `d260a4ec5eac` | 2026-11-24T20:09:26Z | [138740890112](https://github.com/AndreMattheus/FrioVax/actions/runs/45477324944/job/138740890112) | #125 |
| 8061253480 (publicacao) | `f762795b8e3c` | 2026-11-25T15:05:56Z | [139298031758](https://github.com/AndreMattheus/FrioVax/actions/runs/45640659673/job/139298031758) | #130 |

| PR | Primeiro commit (UTC) | Deployment inicial | Tempo de mudança (h) | Merge → publicação (h) |
|---|---|---|---:|---:|
| #100 | 2026-11-03T19:08:52Z | 7772377365 | 238.75 | 237.92 |
| #101 | 2026-11-03T19:22:56Z | 7772377365 | 238.52 | 236.58 |
| #102 | 2026-11-04T12:03:43Z | 7772377365 | 221.84 | 208.47 |
| #103 | 2026-11-04T05:21:52Z | 7772377365 | 228.53 | 170.16 |
| #104 | 2026-11-04T22:50:45Z | 7772377365 | 211.05 | 168.07 |
| #105 | 2026-11-06T17:33:47Z | 7772377365 | 168.33 | 123.11 |
| #106 | 2026-11-06T18:03:00Z | 7772377365 | 167.85 | 120.81 |
| #107 | 2026-11-08T02:53:58Z | 7772377365 | 135.00 | 110.02 |
| #108 | 2026-11-09T16:35:14Z | 7772377365 | 97.31 | 90.56 |
| #109 | 2026-11-10T20:50:36Z | 7772377365 | 69.05 | 50.25 |
| #110 | 2026-11-13T13:26:19Z | 7790932145 | 27.80 | 1.69 |
| #111 | 2026-11-13T18:59:07Z | 7796109019 | 29.53 | 0.08 |
| #112 | 2026-11-08T20:26:14Z | 7804581655 | 161.74 | 0.08 |
| #113 | 2026-11-15T17:34:29Z | 7812019513 | 6.34 | 0.11 |
| #114 | 2026-11-15T16:48:12Z | 7859789219 | 47.96 | 0.08 |
| #115 | 2026-11-17T14:21:44Z | 7859584241 | 2.27 | 0.08 |
| #116 | 2026-11-17T18:26:42Z | 7932635568 | 57.26 | 0.08 |
| #117 | 2026-11-17T17:58:26Z | 7886615972 | 20.85 | 0.10 |
| #118 | 2026-11-18T15:51:50Z | 7892213738 | 2.49 | 0.08 |
| #119 | 2026-11-20T01:58:44Z | 7966723754 | 39.41 | 0.10 |
| #120 | 2026-11-20T00:52:14Z | 7964667958 | 37.65 | 0.10 |
| #121 | 2026-11-21T00:40:11Z | 7984938114 | 44.38 | 0.11 |
| #123 | 2026-11-22T21:22:20Z | 8004884488 | 19.36 | 0.10 |
| #124 | 2026-11-23T16:04:57Z | 8008807028 | 3.43 | 0.11 |
| #125 | 2026-11-23T23:37:41Z | 8039362068 | 20.53 | 0.09 |
| #127 | 2026-11-24T14:30:40Z | 8036763065 | 3.81 | 0.12 |
| #128 | 2026-11-22T14:16:06Z | 8039072407 | 53.70 | 0.11 |
| #129 | 2026-11-24T04:21:29Z | 8034910149 | 12.75 | 0.12 |
| #130 | 2026-11-25T02:14:40Z | 8061253480 | 12.85 | 0.10 |

| Cartão | PRs | Chegada (UTC) | Início (UTC) | Fonte início | Fechamento (UTC) | Lead (d) | Cycle (d) | Fechamento − merge (h) |
|---|---|---|---|---|---|---:|---:|---:|
| #68 | 105 | 2026-10-24T02:56:35Z | 2026-11-05T23:36:38Z | quadro | 2026-11-08T14:47:04Z | 15.49 | 2.63 | 0.00 |
| #69 | 107 | 2026-10-24T02:56:36Z | 2026-11-05T01:33:51Z | quadro | 2026-11-09T03:52:32Z | 16.04 | 4.10 | 0.00 |
| #70 | 110 | 2026-10-24T02:56:39Z | 2026-11-09T16:16:02Z | quadro | 2026-11-14T15:32:59Z | 21.53 | 4.97 | 0.00 |
| #71 | 115 | 2026-10-24T02:56:40Z | 2026-11-17T14:02:58Z | quadro | 2026-11-17T16:33:20Z | 24.57 | 0.10 | 0.00 |
| #72 | 117 | 2026-10-24T02:56:41Z | 2026-11-17T16:37:14Z | quadro | 2026-11-18T14:43:41Z | 25.49 | 0.92 | 0.00 |
| #73 | 112 | 2026-10-24T02:56:43Z | 2026-11-03T19:45:16Z | quadro | 2026-11-15T14:05:56Z | 22.46 | 11.76 | 0.00 |
| #74 | 120 | 2026-10-24T02:56:46Z | 2026-11-20T00:17:30Z | quadro | 2026-11-21T14:25:00Z | 28.48 | 1.59 | 0.00 |
| #75 | 127 | 2026-10-24T02:56:46Z | 2026-11-23T16:47:23Z | quadro | 2026-11-24T18:12:36Z | 31.64 | 1.06 | 0.00 |
| #76 | 130 | 2026-10-24T02:56:49Z | 2026-11-24T18:29:46Z | quadro | 2026-11-25T14:59:42Z | 32.50 | 0.85 | 0.00 |
| #77 | 121 | 2026-10-24T02:56:50Z | 2026-11-19T23:49:31Z | quadro | 2026-11-22T20:56:45Z | 29.75 | 2.88 | 0.00 |
| #78 | 124 | 2026-10-24T02:56:51Z | 2026-11-17T18:37:00Z | quadro | 2026-11-23T19:24:15Z | 30.69 | 6.03 | 0.00 |
| #79 | 113 | 2026-10-24T02:56:53Z | 2026-11-04T21:24:09Z | quadro | 2026-11-15T23:47:59Z | 22.87 | 11.10 | 0.00 |
| #80 | 103 | 2026-10-24T02:56:55Z | 2026-11-03T19:13:41Z | quadro | 2026-11-06T15:44:00Z | 13.53 | 2.85 | 0.00 |
| #81 | 106 | 2026-10-24T02:56:57Z | 2026-11-06T17:51:24Z | quadro | 2026-11-08T17:05:19Z | 15.59 | 1.97 | 0.00 |
| #82 | 119 | 2026-10-24T02:56:58Z | 2026-11-17T18:37:39Z | quadro | 2026-11-21T17:17:08Z | 28.60 | 3.94 | 0.00 |
| #84 | 118 | 2026-10-24T02:57:02Z | 2026-11-18T15:29:29Z | quadro | 2026-11-18T18:16:33Z | 25.64 | 0.12 | 0.00 |
| #85 | 129 | 2026-10-24T02:57:04Z | 2026-11-23T20:05:09Z | quadro | 2026-11-24T16:59:21Z | 31.58 | 0.87 | 0.00 |
| #86 | 123 | 2026-10-24T02:57:05Z | 2026-11-09T23:24:17Z | quadro | 2026-11-23T16:37:51Z | 30.57 | 13.72 | 0.00 |
| #88 | 111 | 2026-10-24T02:57:09Z | 2026-11-10T17:43:38Z | quadro | 2026-11-15T00:25:57Z | 21.89 | 4.28 | 0.00 |
| #99 | 128 | 2026-10-24T02:57:28Z | 2026-11-22T14:10:11Z | quadro | 2026-11-24T19:51:03Z | 31.70 | 2.24 | 0.00 |

<!-- FIM_TABELAS_GERADAS -->

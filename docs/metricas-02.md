# Métricas da Sprint 2 — apuração preliminar da #90

Referência acadêmica **P-T5**. Responsável: Arthur; revisor do PR: Leonardo.
Protocolo: [plano de medição](medicao-sprint-2.md). Uso: subsidiar a
[retrospectiva #95](https://github.com/AndreMattheus/FrioVax/issues/95), sem
ranquear pessoas. Esta apuração ainda não representa o fechamento da sprint.

## Janela, corte e fontes

Janela planejada: **[2026-10-21T00:00:00-03:00,
2026-12-01T00:00:00-03:00)**. Corte desta apuração, exclusivo:
**2026-11-23T18:50:00-03:00** (`2026-11-23T21:50:00Z`). Semanas civis de
segunda a domingo em Fortaleza (UTC−03:00). A primeira começa em 19/10,
mas só observa 21–25/10; a última só observa 23/11 até 18h50.

A extração ocorreu em **23/11, das 18:58:46 às 19:02:09 de Fortaleza**:
[snapshot imutável](evidencias/sprint-2/exportacoes/20261123T220209564272Z/manifesto.json),
588 consultas paginadas, com comando, início/fim, páginas, contagens e SHA-256
por arquivo. Ela inclui PRs, commits originais, reviews, eventos, statuses dos
commits, execuções/jobs e passos, deployments/statuses de `producao`,
ancestralidade de cada SHA, issues/incidentes e Project. Os JSONs preservam
campos selecionados e IDs da fonte; não contêm tokens, logs, patches,
payloads ou credenciais do provedor. Títulos e nomes dos passos são preservados
para classificar a população e verificar a publicação.

Uma [consulta complementar dos ambientes](evidencias/sprint-2/metricas-02/ambiente-20261123/manifesto.json)
preserva ID, nome, datas e regras de proteção do ambiente escolhido, sem ler
variáveis nem segredos. É fotografia atual da configuração, não histórico
da proteção durante a sprint.

Os [CSV locais congelados](evidencias/sprint-2/metricas-02/fontes-20261123/manifesto.json)
preservam incidentes, monitoramento e o registro de publicações disponível na
coleta. O registro antigo tinha apenas duas publicações da amostra #91; a
apuração completa usa os deployments e jobs do novo snapshot, não presume que
aquele CSV fosse o universo. Incidentes e monitoramento contêm só cabeçalho.

Também foi calculado o [recorte histórico até o fim de 20/11](evidencias/sprint-2/metricas-02/20261120/resultado.json),
com fim exclusivo `2026-11-21T00:00:00-03:00`: **11 publicações, 19 PRs
publicados e 11 cartões técnicos**. A primeira apuração foi executada em 23/11;
o marco de 20/11 não foi cumprido por esta execução. Reconstruir esse corte
agora não é evidência de medição realizada no prazo. Próximos cortes: **26/11**
para vídeos, com hora exata a registrar, e **S2-34/#97**, no hash de entrega.
Não antecipar esses resultados usando este snapshot.

## Definições e filtros

| Medida | Definição, população e denominador |
|---|---|
| Frequência de implantação | Contagem de publicações públicas distintas por semana civil. Exige ambiente `producao`, conclusão operacional `success`, etapa Render e smoke autenticado aprovados. Denominador temporal: cada semana ou fração observada indicada na tabela; não dividir semanas parciais por sete dias como se fossem completas. |
| Tempo da mudança até implantação | Para cada PR integrado em `main` na janela e contido em publicação: `primeiro success público contendo o merge SHA − menor authored_at dos commits originais do PR`. Unidade: horas corridas. n=24 PRs; PR sem publicação ficaria listado e fora da distribuição. Preservar merge SHA e commits originais permite conferir squash. |
| Taxa de falha de mudança | `100 × número de publicações avaliadas causais de incidente / número de publicações avaliadas`. Uma publicação causal conta uma vez, mesmo com vários incidentes. Só avaliar com cobertura efetiva, fonte e responsável, contínua da publicação até o corte. Falha de CI ou de tentativa de publicar não implica incidente em produção. |
| Alternativa: retrabalho observado em revisão | `100 × PRs integrados com review humana CHANGES_REQUESTED antes do merge / PRs integrados com review humana preservada antes do merge`. Janela pelo merge; exclui bot e autorreview. Mede demanda explícita de revisão, não volume de código refeito nem falha em produção. |
| Lead time de cartão | Fechamento da issue menos chegada ao Project, em dias corridos de 24 h. Mesmo método da Sprint 1; fechamento é aproximação de entrega, não publicação. |
| Cycle time de cartão | Fechamento menos primeira entrada observada em `Em Progresso`; sem esse evento, abertura do PR integrado vinculado, identificada como aproximação. Não usar datas planejadas. |
| Throughput | Cartões técnicos fechados por semana civil, com PR integrado vinculado. Histórias agregadoras e processo/documentação ficam fora; total geral de tarefas S2 aparece separado. |
| Espera até primeira review (auxiliar) | Primeira review submetida válida de outra pessoa menos primeiro pedido de revisão após o PR estar pronto. Sem pedido, abertura/saída de draft seria aproximação; neste corte todos os 24 têm pedido. Não mede os ciclos posteriores até aprovação. |

As três medidas inicialmente selecionadas foram frequência, tempo da mudança e
taxa de falha. Como a terceira não é observável com a cobertura preservada,
apresenta-se a alternativa de retrabalho com definição e dados próprios;
ela **não substitui uma taxa DORA por outro nome**.

Somente PRs integrados, não draft, contra `main`, com `merged_at` na janela
entram nas mudanças. O [PR #122](https://github.com/AndreMattheus/FrioVax/pull/122),
prova de falha não integrada, fica fora. A série de publicação considera o
timestamp do status de sucesso, não a data do push, merge ou abertura do
deployment. Não se publicou uma série proxy de merges com CI verde; ela seria
separada, nunca concatenada às implantações reais.

## Publicações, duplicatas e rastreabilidade

**40 registros de deployment = 20 tentativas com dois registros por job**:
um automático do environment e outro explícito do script. **32 registros
operacionais com sucesso representam 16 publicações**, não 32. As outras
quatro tentativas têm oito registros com falha, preservados na auditoria.
Elas não entram na frequência e não foram rotuladas como incidentes.

O status administrativo posterior `inactive` significa que uma publicação foi
substituída; não apaga o sucesso histórico. O cálculo escolhe o último status
operacional anterior ao corte, ignorando apenas `inactive`. Uma falha operacional
posterior prevaleceria sobre um sucesso anterior.

A associação deployment→job exige mesmo SHA, criação dentro do intervalo do
job (tolerância de 30 s antes do início), e passo `Registrar deployment`.
Ambiguidade aborta o cálculo. Dentro de cada job, os dois registros representam
um único evento: usa-se o registro explícito, criado depois do automático.
Render e smoke devem ter terminado com sucesso antes do corte. O marco da
publicação é o `success` do deployment emitido depois do smoke, sem exigir que
o job já tenha terminado: passos finais podem continuar após a publicação.
O horário de término do job no snapshot serve apenas à associação dos
registros à execução, não define o instante da implantação; sua conclusão
posterior não é requisito da contagem. No caso do primeiro deployment,
o smoke terminou em `2026-11-13T17:53:50Z`, o `success` foi emitido às
`17:53:51Z` e o job terminou às `17:53:53Z`. Portanto o corte exclusivo
`17:53:51Z` ainda exclui a publicação, mas `17:53:52Z` já a inclui uma vez.
Esse limite está coberto por teste sobre o snapshot preservado. A associação
temporal é um limite da instrumentação atual: os statuses exportados não
preservam `log_url`; um futuro coletor pode preservar apenas esse vínculo
público validado, sem URLs privadas.

Há **15 SHAs distintos e 16 eventos reais**. O deployment **7773456323** é
um **redeploy** de `c51e2981773fed0a40fb85264c1bc5201b63269b`, em outro
[job com nova etapa Render e novo smoke](https://github.com/AndreMattheus/FrioVax/actions/runs/43484767266/job/132090186919).
Esse é o motivo explícito para contar o SHA de novo; seu par automático
7773453502 continua excluído. Não há vínculo de incidente registrado para
esse redeploy. Nenhum rollback foi identificado nesta apuração.

O [resultado auditável](evidencias/sprint-2/metricas-02/20261123/resultado.json)
lista todos os 40 IDs, estado, job, motivo de inclusão/exclusão, os 16 eventos,
tipo, SHA completo, hora e fonte do smoke, todos os PRs e commits contidos.
A inclusão é conferida pelo grafo completo de ancestrais do SHA publicado,
não apenas por `merged_at <= publicado_em`. O script rejeita grafo truncado
ou PR integrado depois da publicação que o contém. O primeiro artefato traz
também a base anterior à sprint, preservada em `commits_contidos` e
`prs_contidos`; os tempos abaixo usam somente os PRs integrados nesta janela.
Merges antigos sem PR conhecido podem ser inspecionados nesses grafos; a
apuração não afirma que todo commit do repositório corresponde a um PR.
Conferência por diferença de ancestralidade entre publicações: **nenhum
commit novo sem PR conhecido após a carga inicial**. Na primeira publicação,
há três commits sem PR conhecido, todos de 04/09, anteriores à janela:
`55cfb0815d0b36b5fc70f17f24c1b570b2385369`,
`a64f659f8ed47896c58c34f3f47c062f50f39f14` e
`b2a1f602db8b87bb158181b87f36961468cfb416`.
São parte da base inicial, não mudanças S2 omitidas. O JSON conserva
`commits_novos` e `commits_novos_sem_pr_conhecido` por evento para essa auditoria.

## Resultado e distribuições

Frequência semanal: **0, 0, 0, 6, 8 e 2**; as extremidades são parciais.
A primeira publicação comprovada ocorreu em **13/11 às 14:53:51 de Fortaleza**.
Antes disso há ausência de implantação comprovada, não evidência de lentidão
de desenvolvimento nem prova de indisponibilidade contínua de uma API.

Tempo de mudança: **mediana 52,61 h**, média **94,86 h**, n=24.
Percentis usam **nearest rank**: ordenar valores e escolher
`x[ceil(p × n) − 1]`; mediana usual, média aritmética e durações calculadas
antes de arredondar. Tabelas completas, distribuição semanal e dados por
PR/cartão aparecem abaixo e também no arquivo gerado
[tabelas.md](evidencias/sprint-2/metricas-02/20261123/tabelas.md).

**Taxa de falha: não observável**. Há N=16 publicações comprovadas, mas
**N avaliado=0**, sem cobertura efetiva preservada; numerador e taxa são
`null`. As consultas retornaram zero issues com label `incidente`, e o CSV
também está vazio: isso não autoriza escrever `0/16`.
Com N avaliado positivo, cobertura efetiva e nenhum incidente, seria `0/N`;
com N=0 a taxa é indefinida. **Recuperação não observável**, sem incidente
registrado, nunca tempo zero. Incidente aberto ficaria fora das durações
concluídas, com sua idade informada, e sem causa confirmada impediria concluir
a taxa enquanto a investigação estivesse pendente.

Alternativa de retrabalho em revisão: **9/24 = 37,5%** dos PRs revisados
integrados receberam `CHANGES_REQUESTED` humano preservado. IDs: **#101,
#102, #103, #109, #110, #112, #119, #123 e #124**. Os outros 15 não têm esse
estado preservado. Essa distribuição binária não mede gravidade, duração ou
quantidade de retrabalho e não prova ausência de ajustes sugeridos em comentários.
Reviews dispensadas podem perder o estado original; os estados são os da
extração, portanto o corte histórico também tem essa limitação.

## Comparação de fluxo com a Sprint 1

Mantém-se o [método publicado da Sprint 1](metricas-sprint-1.md), incluindo
fechamento como aproximação da entrega. População técnica S2 fixada no
script: **#68–#86, #88, #89 e #99**; inclui contratos executáveis, testes,
containers, CI/proteção da branch e publicação. ADRs #67/#87, medição #66,
planning #65, documentação, cerimônias, experimentos e agregadoras US04–US10
ficam fora do throughput técnico. Cartões abertos não entram nas médias.

| Medida | Sprint 1, publicada | Sprint 2, até este corte |
|---|---:|---:|
| Cartões técnicos concluídos | 24 | 16 |
| Lead médio / mediano (dias) | 11,68 / 10,19 | 23,32 / 23,72 |
| Cycle médio / mediano (dias) | 1,35 / 0,86 | 4,56 / 3,41 |
| Início pelo quadro / aproximação por PR | 7 / 17 | 16 / 0 |

As 16 conclusões técnicas têm merge de PR vinculado anterior ao fechamento,
com diferença máxima de **2 segundos**; a tabela conserva essa diferença
antes do arredondamento. As 23 tarefas S2 fechadas no total geral incluem
processo e cerimônias e não são comparadas diretamente aos 24 cartões S1.
A S1 incluiu documentação técnica #34; a S2 separa o pacote de documentação
#93 e mantém CI/proteção como engenharia. A fronteira de população não é
idêntica: os resultados sustentam discussão, não uma comparação causal de
produtividade. As semanas e durações observadas também diferem; não comparar
16 contra 24 como se fossem o mesmo período ou carga.

## Interpretação e limites

Dez PRs da sprint chegaram juntos à primeira publicação. Para eles, a mediana
de tempo de mudança é **189,69 h**; para os 14 seguintes, **28,67 h**.
Nesses 14, a mediana entre merge e publicação é **5,96 minutos**. A amostra
inicial acumula espera pela infraestrutura e a formação do primeiro lote;
essa mudança de população explica parte da distribuição e impede atribuir
a queda apenas a melhoria no desenvolvimento.

A espera até primeira review tem mediana **4,31 h**, média **9,40 h** e
máximo **43,81 h**; **3/24** esperaram mais de 24 h. Essa medida permite
discutir a fila de revisão, mas não explica sozinha o tempo total: faltam
ciclos posteriores, ajustes e intervalos ativos completos. O cycle time
alto de um cartão não é duração de trabalho ininterrupto. Muitos cartões
entraram no Project em 23/10 e começaram depois; parte do lead é permanência
no backlog. Não transformar espera sem classificação em esforço humano.

O snapshot não é transacional: fontes foram consultadas em instantes
diferentes. O corte é anterior ao início da extração para reduzir concorrência,
mas `closed_at`, vínculo de fechamento, título, estado de draft e reviews são
fotografias atuais; reaberturas, edições ou reviews dispensadas podem limitar
uma reconstrução histórica. Eventos de status do Project podem ser incompletos.
Datas de autor dos commits podem refletir rebase, relógio local ou trabalho
anterior ao PR; não medem esforço. A população de mudanças inclui PRs de
processo/documentação que geram artefato do repositório, além de código da API;
não equivale a uma população somente de funcionalidades. Smoke comprova o
momento da publicação, não acompanhamento contínuo nem ausência de incidentes.

## Reprodução e comandos realmente executados

Pré-requisitos: Python 3.11+; GitHub CLI autenticado com leitura do repositório
e Project **somente para nova coleta**. A reprodução do snapshot é offline.
O [manifesto](evidencias/sprint-2/exportacoes/20261123T220209564272Z/manifesto.json)
contém os 588 comandos efetivamente executados, inclusive parâmetros GraphQL.
Exemplos exatos de endpoints da execução (todos com `gh api --paginate --slurp`):

```text
repos/AndreMattheus/FrioVax/pulls?state=all&base=main&per_page=100
repos/AndreMattheus/FrioVax/actions/runs?per_page=100
repos/AndreMattheus/FrioVax/deployments?environment=producao&per_page=100
repos/AndreMattheus/FrioVax/issues?state=all&labels=incidente&per_page=100
repos/AndreMattheus/FrioVax/pulls/110/commits?per_page=100
repos/AndreMattheus/FrioVax/pulls/110/reviews?per_page=100
repos/AndreMattheus/FrioVax/deployments/7790932145/statuses?per_page=100
```

`state=all` preserva inclusive PRs não integrados para auditar exclusões;
o filtro de merge pertence ao cálculo. Commits por SHA são paginados até
concluir o grafo; jobs incluem passos e tentativa da execução. A coleta usa
REST `per_page=100` e cursores/pageInfo no GraphQL; erro aborta o snapshot.

Comandos executados nesta apuração, a partir da raiz do repositório:

```powershell
python docs/scripts/coletar-medicao-sprint-2.py
python docs/scripts/coletar-medicao-sprint-2.py --verificar-paginacao docs/evidencias/sprint-2/exportacoes/20261123T220209564272Z
python docs/scripts/coletar-medicao-sprint-2.py --preservar-fontes docs/evidencias/sprint-2/metricas-02/fontes-20261123
python docs/scripts/coletar-medicao-sprint-2.py --coletar-ambiente docs/evidencias/sprint-2/metricas-02/ambiente-20261123
python docs/scripts/calcular-metricas-02.py docs/evidencias/sprint-2/exportacoes/20261123T220209564272Z --corte 2026-11-23T21:50:00Z --fontes docs/evidencias/sprint-2/metricas-02/fontes-20261123 --saida docs/evidencias/sprint-2/metricas-02/20261123
python docs/scripts/calcular-metricas-02.py docs/evidencias/sprint-2/exportacoes/20261123T220209564272Z --corte 2026-11-21T03:00:00Z --fontes docs/evidencias/sprint-2/metricas-02/fontes-20261123 --saida docs/evidencias/sprint-2/metricas-02/20261120
python docs/scripts/validar-medicao-sprint-2.py
python -m unittest discover -s docs/scripts -p test_medicao_sprint_2.py -v
python -m unittest discover -s docs/scripts -p test_metricas_02.py -v
```

Para repetir sem modificar a evidência, executar os dois comandos de cálculo
com `--saida` apontando para pastas novas. Comparar `resultado.json` e
`tabelas.md` por `git diff --no-index` com os arquivos versionados: devem ser
idênticos. Fórmulas e filtros estão no
[script](scripts/calcular-metricas-02.py); hashes de entrada são conferidos
antes das contas. Os JSONs mantêm valores sem arredondamento e listas por
semana, PR e cartão. Os testes incluem pareamento/deduplicação real, redeploy,
primeiro deploy por PR, corte histórico, exclusão da prova de falha, percentis,
`inactive`, `0/N` com cobertura, taxa indefinida sem observação e causa contada
uma vez mesmo com dois incidentes sintéticos, apenas em diretório temporário.
Resultado local: **11 testes do contrato de coleta e 8 da apuração passaram**;
o validador confirmou os hashes dos três snapshots. A
[conferência de paginação](evidencias/sprint-2/validacao/paginacao-20261123T220209564272Z.json)
reconsultou **54 páginas de um PR**, com os mesmos 54 IDs do snapshot.

## Validação humana e atualização

Em **24/11/2026**, Leonardo confirmou que reexecutou os dois cortes e que os
resultados coincidiram com o relatório, na
[revisão do PR #125](https://github.com/AndreMattheus/FrioVax/pull/125#pullrequestreview-6041835379).
É um relato de reprodução independente; a revisão solicitou estes ajustes e
não constitui aprovação. Arthur esclareceu em 24/11 que assumiu a tarefa
após uma troca com Matheus. A referência original a Bianca correspondia à
dupla de Matheus; com Arthur como responsável, Leonardo realiza a revisão e
a validação independente. A reprodução dos dois cortes foi confirmada por
Leonardo no link acima; não há uma validação adicional pendente de Bianca.

Para as próximas reproduções, no mesmo snapshot, conferir 6/8/2 publicações
nas semanas de 09/11, 16/11 e 23/11;
24 linhas de mudança, especialmente #100–#109 na primeira publicação,
#110 no deployment 7790932145 e #124 no 8008807028. Comparar os JSONs por
semana/PR; registrar data, comandos, resultado e eventual divergência no PR.
Uma nova extração pode conter novos eventos e deve gerar outro diretório;
o snapshot preservado continua sendo a referência do mesmo corte.

Leonardo também deve conferir os registros operacionais de incidentes com os
deployments causais; não há incidente registrado para validar aqui. Havendo
novas fontes, preencher detecção, recuperação, IDs causais e de recuperação,
monitoramento efetivo e justificativa para vínculo desconhecido. Congelar
novos CSVs, recalcular e manter este resultado como histórico.

Em 26/11, repetir a coleta e registrar corte anterior ao início da extração,
preservar fontes locais e executar o cálculo com outra saída. Na #97,
repetir até o hash entregue e atualizar este relatório, mantendo os cortes
anteriores. Este PR preliminar deve usar `Refs #90`, mantendo a issue aberta
para os próximos cortes; a alteração da descrição ficará com Arthur.
O fechamento por `Closes #90` fica para a entrega que concluir a tarefa.
Revisão de Leonardo, CI remoto verde, integração e datas reais de
Start/Finished no Project continuam condições da
[Definição de Pronto](processo.md#definição-de-pronto). Esta documentação
não declara aprovação, integração, datas do Project ou cortes futuros.

## Tabelas do corte de 23/11

<!-- INICIO_TABELAS_GERADAS -->

<!-- Gerado por calcular-metricas-02.py; não editar números manualmente. -->
| Semana (segunda, Fortaleza) | Parcial | Publicações | Throughput técnico | Tarefas S2 fechadas (geral) |
|---|---|---:|---:|---:|
| 2026-10-19 | sim | 0 | 0 | 0 |
| 2026-10-26 | não | 0 | 0 | 0 |
| 2026-11-02 | não | 0 | 3 | 7 |
| 2026-11-09 | não | 6 | 5 | 5 |
| 2026-11-16 | não | 8 | 6 | 8 |
| 2026-11-23 | sim | 2 | 2 | 3 |

| Distribuição | n | Média | Mediana | p85 | p95 | Mínimo | Máximo |
|---|---:|---:|---:|---:|---:|---:|---:|
| Tempo de mudança (h) | 24 | 94.86 | 52.61 | 221.84 | 238.52 | 2.27 | 238.75 |
| Lead de cartão (d) | 16 | 23.32 | 23.72 | 29.75 | 30.69 | 13.53 | 30.69 |
| Cycle de cartão (d) | 16 | 4.56 | 3.41 | 11.10 | 13.72 | 0.10 | 13.72 |
| Espera até review (h) | 24 | 9.40 | 4.31 | 22.31 | 29.36 | 0.01 | 43.81 |

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

| Cartão | PRs | Chegada (UTC) | Início (UTC) | Fonte início | Fechamento (UTC) | Lead (d) | Cycle (d) | Fechamento − merge (h) |
|---|---|---|---|---|---|---:|---:|---:|
| #68 | 105 | 2026-10-24T02:56:35Z | 2026-11-05T23:36:38Z | quadro | 2026-11-08T14:47:04Z | 15.49 | 2.63 | 0.00 |
| #69 | 107 | 2026-10-24T02:56:36Z | 2026-11-05T01:33:51Z | quadro | 2026-11-09T03:52:32Z | 16.04 | 4.10 | 0.00 |
| #70 | 110 | 2026-10-24T02:56:39Z | 2026-11-09T16:16:02Z | quadro | 2026-11-14T15:32:59Z | 21.53 | 4.97 | 0.00 |
| #71 | 115 | 2026-10-24T02:56:40Z | 2026-11-17T14:02:58Z | quadro | 2026-11-17T16:33:20Z | 24.57 | 0.10 | 0.00 |
| #72 | 117 | 2026-10-24T02:56:41Z | 2026-11-17T16:37:14Z | quadro | 2026-11-18T14:43:41Z | 25.49 | 0.92 | 0.00 |
| #73 | 112 | 2026-10-24T02:56:43Z | 2026-11-03T19:45:16Z | quadro | 2026-11-15T14:05:56Z | 22.46 | 11.76 | 0.00 |
| #74 | 120 | 2026-10-24T02:56:46Z | 2026-11-20T00:17:30Z | quadro | 2026-11-21T14:25:00Z | 28.48 | 1.59 | 0.00 |
| #77 | 121 | 2026-10-24T02:56:50Z | 2026-11-19T23:49:31Z | quadro | 2026-11-22T20:56:45Z | 29.75 | 2.88 | 0.00 |
| #78 | 124 | 2026-10-24T02:56:51Z | 2026-11-17T18:37:00Z | quadro | 2026-11-23T19:24:15Z | 30.69 | 6.03 | 0.00 |
| #79 | 113 | 2026-10-24T02:56:53Z | 2026-11-04T21:24:09Z | quadro | 2026-11-15T23:47:59Z | 22.87 | 11.10 | 0.00 |
| #80 | 103 | 2026-10-24T02:56:55Z | 2026-11-03T19:13:41Z | quadro | 2026-11-06T15:44:00Z | 13.53 | 2.85 | 0.00 |
| #81 | 106 | 2026-10-24T02:56:57Z | 2026-11-06T17:51:24Z | quadro | 2026-11-08T17:05:19Z | 15.59 | 1.97 | 0.00 |
| #82 | 119 | 2026-10-24T02:56:58Z | 2026-11-17T18:37:39Z | quadro | 2026-11-21T17:17:08Z | 28.60 | 3.94 | 0.00 |
| #84 | 118 | 2026-10-24T02:57:02Z | 2026-11-18T15:29:29Z | quadro | 2026-11-18T18:16:33Z | 25.64 | 0.12 | 0.00 |
| #86 | 123 | 2026-10-24T02:57:05Z | 2026-11-09T23:24:17Z | quadro | 2026-11-23T16:37:51Z | 30.57 | 13.72 | 0.00 |
| #88 | 111 | 2026-10-24T02:57:09Z | 2026-11-10T17:43:38Z | quadro | 2026-11-15T00:25:57Z | 21.89 | 4.28 | 0.00 |

<!-- FIM_TABELAS_GERADAS -->

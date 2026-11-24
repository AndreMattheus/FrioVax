# Registro de uso de IA

Este registro reúne os apoios de IA declarados pela equipe no repositório e os confirmados no histórico local de sessões do Codex desta estação. A data de 01/09 foi informada no registro inicial, versionado em 04/09. Para as atividades de outubro, os links apontam os PRs e commits relacionados; as sessões locais identificam o apoio da ferramenta, que não pode ser deduzido apenas da autoria dos commits. A responsabilidade pela aceitação das mudanças de código permanece com a equipe.

| Data | Ferramenta | Uso registrado | Fonte e artefato relacionado | Verificação humana necessária |
|---|---|---|---|---|
| 01/09/2026 | OpenAI Codex | Leitura dos guias e rubricas das duas disciplinas; preparação do esqueleto Java/Go, proposta, backlog, acordo de processo e roteiros da Sprint 0 | [Registro inicial](https://github.com/AndreMattheus/FrioVax/commit/55cfb08) | Toda a equipe deve revisar, compreender e ajustar os artefatos antes de submetê-los; builds e testes devem ser executados; papéis, usuários GitHub e decisões devem ser confirmados |
| 04/09/2026 | OpenAI Codex | Apoio à validação do CI, da separação Java/Go, do Docker Compose e da documentação; apoio às correções de empacotamento Quarkus, portabilidade das tarefas e metadados do projeto | [Registro da validação](https://github.com/AndreMattheus/FrioVax/commit/a64f659) | A equipe deve revisar as correções, executar o fluxo em suas máquinas e confirmar o novo CI verde após a publicação |
| 02/10/2026 | OpenAI Codex | Leitura do diff e redação da descrição e do checklist de verificação da PR de domínio, portas e repositórios em memória | Sessão local do Codex; [PR #39](https://github.com/AndreMattheus/FrioVax/pull/39) | O autor deve conferir se o resumo e as instruções correspondem ao código e aos checks |
| 06/10/2026 | OpenAI Codex | Revisão técnica da atualização de câmara, com foco no teste de concorrência, integração local com a `main` e execução do CI para subsidiar a decisão de aprovação | Sessão local do Codex; [PR #47](https://github.com/AndreMattheus/FrioVax/pull/47) | A decisão de aprovar e integrar permanece com os revisores; conferir o estado final do PR e dos checks |
| 07/10/2026 | OpenAI Codex | Apoio à elaboração e revisão das mudanças da issue #24 (atualização de lote, capacidade e transferência), dos testes HTTP de concorrência e do contrato; execução da suíte | Sessão local do Codex; [PR #53](https://github.com/AndreMattheus/FrioVax/pull/53) e [commits da branch](https://github.com/AndreMattheus/FrioVax/pull/53/commits) | A equipe deve revisar as regras e o código, reproduzir os testes e validar os cenários de rejeição sem gravação parcial |
| 13/10/2026 | OpenAI Codex | Apoio à revisão dos testes HTTP em memória da PR #58, à resolução de conflito no README durante o rebase dos testes PostgreSQL da issue #31, à execução do CI e à preparação da PR #59 | Sessão local do Codex; [PR #58](https://github.com/AndreMattheus/FrioVax/pull/58) e [PR #59](https://github.com/AndreMattheus/FrioVax/pull/59) | A equipe deve revisar a resolução do conflito, conferir a cobertura dos testes e os checks após o rebase |
| 14/10/2026 | OpenAI Codex | Comparação dos guias atualizados da Sprint 1 com o repositório, issues, Project e PRs; elaboração do checklist integrado e de roteiros sugeridos para os dois vídeos | [Registro na branch de entregáveis](https://github.com/AndreMattheus/FrioVax/commit/d197585) | A equipe deve conferir o histórico do quadro, calcular as métricas, realizar a retrospectiva, validar os roteiros e comprovar a execução em clone limpo antes de marcar essas entregas como concluídas |
| 14/10/2026 | OpenAI Codex | Apoio na issue #34: conferência do README e contrato com o código da `main`, elaboração do roteiro e script de validação da Sprint 1, execução da suíte e do fluxo HTTP em clone limpo | [PR #63](https://github.com/AndreMattheus/FrioVax/pull/63) | A equipe deve revisar os textos e o script, repetir o roteiro no ambiente de apresentação e confirmar os resultados antes de integrar |
| 15/10/2026 | OpenAI Codex | Consolidação dos relatos e acordos da retrospectiva, consulta ao histórico do Project e aos PRs para calcular métricas, atualização do checklist e resolução da integração com `main` | [Retrospectiva](retrospectiva-01.md), [métricas](metricas-sprint-1.md) e [PR #62](https://github.com/AndreMattheus/FrioVax/pull/62) | A equipe deve conferir a interpretação dos acordos, o recorte técnico e as aproximações das métricas |

## Sprint 2

Registro de 24/11/2026 — OpenAI Codex: apoio aos ajustes da revisão de Leonardo
no PR #125: explicitação do `success` do deployment após Render/smoke como marco
da publicação, sem depender do término posterior do job; teste de regressão
do corte de 13/11 às 17:53:52 UTC e registro da reprodução relatada por Leonardo.
Validação de Bianca não foi presumida. A descrição do PR permanece sob edição
de Arthur; Leonardo deve conferir os ajustes e decidir sobre a aprovação.

Registro de 23/11/2026 — OpenAI Codex: apoio à #90 na extração paginada de
metadados do GitHub, preservação de snapshots, cálculo offline de publicações,
tempo de mudança, retrabalho em revisão e fluxo da Sprint 2, testes e redação
de `docs/metricas-02.md`. Arthur deve compreender e conferir as definições,
exclusões, redeploy e limites da observação; Leonardo deve revisar o PR.
A reprodução independente por Bianca, a confirmação de incidentes e os cortes
de 26/11 e da entrega permanecem pendentes; não foram simulados como realizados.

Registro de 18/11/2026 — OpenAI Codex: apoio à #84 na revisão do Compose,
healthcheck da API, preparação de autenticação em volume, runtime sem root,
documentação e ensaios com cópia limpa e volumes descartáveis. Evidências em
`docs/evidencias/sprint-2/validacao/containers-84.md`. Arthur deve compreender e
revisar as alterações; Leonardo deve revisar o PR. CI remoto, aprovação e datas
reais no Project permanecem sob responsabilidade da equipe.

| Data | Ferramenta | Uso registrado                                                                                                                                                                                             | Fonte e artefato relacionado                                                                                                                                          | Verificação humana necessária                                                                                                                                                                                                                                                                                                                                                                |
|---|---|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 15/11/2026 | OpenAI Codex | Apoio à implementação da #79: resumo agregado, cache local com Clock e TTL, eventos após commit, métricas Prometheus, testes e documentação                                                                | [Resumo operacional](resumo.md), código e testes no mesmo conjunto de alterações                                                                                      | Arthur deve revisar a semântica dos totais e reproduzir os testes; Leonardo deve revisar o PR. CI remoto, demonstração e datas reais no Project precisam ser conferidos pela equipe.                                                                                                                                                                                                         |
| 03/11/2026 | OpenAI Codex | Registro da planning a partir da confirmação de Leonardo; atualização das duplas de revisão, DoD e política de bloqueios; apoio ao encerramento da tarefa de planejamento                                  | [Issue #65](https://github.com/AndreMattheus/FrioVax/issues/65) e [acordo de processo](processo.md#planning-da-sprint-2--03112026)                                    | Arthur deve revisar a documentação no PR; horários, presença e consenso foram confirmados por Leonardo                                                                                                                                                                                                                                                                                       |
| 04/11/2026 | OpenAI Codex | Apoio à implementação e revisão da autenticação da issue #80, testes de sessões e correção do ambiente local para caminhos com apóstrofos; revisão independente por agente sem histórico da conversa       | [PR #103](https://github.com/AndreMattheus/FrioVax/pull/103), script de preparação e testes de regressão                                                              | Arthur deve revisar a implementação, reproduzir o fluxo local e conferir os testes e o CI; a revisão por IA subsidia a decisão humana                                                                                                                                                                                                                                                        |
| 04/11/2026 | OpenAI Codex | Apoio na explicação de conceitos de Go e do funcionamento da análise térmica no contexto do FrioVax; leitura, verificação de coerência e síntese das decisões já registradas nas issues e na documentação. | [Issue #67](https://github.com/AndreMattheus/FrioVax/issues/67), [ADR 0003](decisoes/0003-servico-go-telemetria.md) e [proposta](proposta.md#6-divisão-entre-java-e-go) | Matheus deve conferir a explicação e propor atualização da documentação; Bianca deve validar as regras de domínio e as complementações propostas na revisão do PR                                                                                                                                                                                                                            |
| 04/11/2026 | OpenAI Codex | Consulta à documentação oficial de Render, Neon e pgJDBC; apoio à redação da ADR de implantação e à simulação local de migração, API sem Go e recuperação                                                  | [Issue #87](https://github.com/AndreMattheus/FrioVax/issues/87) e [ADR 0004](decisoes/0004-implantacao.md)                                                            | Arthur confere os procedimentos e confirma contas e segredos como titular; Leonardo revisa a decisão. Simulação local não comprova deploy remoto.                                                                                                                                                                                                                                            |
| 07/11/2026 | OpenAI Codex | Apoio na compreensão, explicação, definição e implementação do contrato Protobuf, geração Go/Java, build Docker e verificação Buf no CI; explicações dos conceitos relacionados e revisão da documentação. | [Contrato e comandos](../protos/README.md), [build da API](../api/pom.xml) e [workflow](../.github/workflows/ci.yml)                                                  | Matheus deve revisar contrato e configuração; Bianca deve revisar e validar.                                                                                                                                                                                                                                                                                                                 |
| 08/11/2026 | Claude (Anthropic) | Explicação do contrato e das regras térmicas e apoio à redação das revisões de Bianca nos PRs de Matheus.                                                                                                  | [PR #102](https://github.com/AndreMattheus/FrioVax/pull/102) e [PR #105](https://github.com/AndreMattheus/FrioVax/pull/105)                                           | Os pontos levantados e a decisão de aprovar são de Bianca; Matheus respondeu a cada ponto nos PRs                                                                                                                                                                                                                                                                                            |
| 17/11/2026 | OpenAI Codex | Apoio na compreensão e planejamento e testes da análise térmica em Go                                                                                                                                      | [Issue #71](https://github.com/AndreMattheus/FrioVax/issues/71)                                                                                                       | Matheus deve compreender e revisar as regras e reproduzir os testes. Bianca deve revisar o PR e conferir o CI remoto.                                                                                                                                                                                                                                                                        |
| 17/11/2026 | OpenAI Codex | Apoio ao planejamento e à implementação do adaptador e servidor gRPC e relacionados e explicação dos conceitos | [Issue #72](https://github.com/AndreMattheus/FrioVax/issues/72) | Matheus deve compreender, completar e revisar a implementação e reproduzir as validações; Bianca deve revisar o PR e conferir CI remoto.                                                                                                                                                                                                                                                     |
| 19/11/2026 | OpenAI Codex | Apoio ao planejamento e à implementação das consultas de histórico | [Issue #77](https://github.com/AndreMattheus/FrioVax/issues/77), [contrato do histórico](contrato-api.md#11-histórico-de-leituras-e-ocorrências) | Matheus deve compreender, complementar e revisar o código e reproduzir as consultas; Bianca deve revisar o PR. |

## Política da equipe

Registro de 14/11/2026 — OpenAI Codex: consolidacao no PR #111 das evidencias
da #88 e da dispensa de backup por publicacao no ambiente academico; ajuste do
workflow, preflight, testes e ADR. Scripts e evidencias de recuperacao preservados.
Arthur solicitou a mudanca apos sugestao de Leonardo; Leonardo deve revisar
o risco aceito e a publicacao automatica apos CI verde em main, com opcao manual,
antes do merge. A escolha final de Arthur foi restaurar esse disparo automatico.

Registro de 13/11/2026 — OpenAI Codex: apoio ao diagnostico da restauracao e do
contexto Docker no Render, acompanhamento somente de leitura dos runs/deployments
e redacao do relatorio `docs/evidencias/sprint-2/validacao/implantacao-88.md`.
Arthur executou backup, restauracao e consultas autenticadas e relatou a
preservacao de dados. Leonardo deve revisar evidencia e mudancas locais antes
do encerramento; nenhuma credencial foi registrada neste relato.

Registro de 11/11/2026 — OpenAI Codex: apoio aos ajustes solicitados por Leonardo
na #88: retentativa de consultas Render dentro do prazo, status final de falha
sem URL valida, testes de regressao e retirada do fechamento automatico da issue.
Arthur deve revisar as alteracoes; o aceite operacional remoto segue pendente.

Registro de 10/11/2026 — OpenAI Codex: apoio à #88 com workflow de publicação,
scripts e testes de falha, configuração de migração por ambiente e documentação
de operação. Fontes: `docs/implantacao.md`, workflow e scripts no mesmo PR.
Arthur deve conferir configuração real, recuperação, persistência e execução
remota; Leonardo revisa. Preparação local não comprova conclusão da publicação.
Continuidade em 10/11/2026: apoio aos scripts PowerShell de backup, restauracao
e definicao de senha runtime, grants limitados no CI e proposta de backup manual
com declaracao por execucao. Resultados remotos de migracao/restore foram
relatados por Arthur. Ajuste da ADR depende de revisao de Leonardo.

- IA pode apoiar pesquisa, explicação, testes, documentação e revisão.
- O autor do PR responde pelo conteúdo submetido e deve conseguir explicá-lo.
- Código gerado não é integrado sem teste e revisão humana.
- Informação externa relevante deve apontar para fonte verificável.
- Uso material de IA é atualizado neste arquivo a cada sprint.

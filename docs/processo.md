# Acordo de processo

## Cadência e cerimônias

A cadência segue o [cronograma atualizado das disciplinas](https://github.com/fmarquesfilho/processos-2026-2/blob/main/docs/CRONOGRAMA.md): a Sprint 1 termina em 16/10/2026, e a Sprint 2 é a entrega final do semestre. O planejamento ocorre no primeiro dia útil do bloco; revisão e retrospectiva acontecem antes da entrega.

| Cerimônia | Frequência | Duração | Resultado esperado |
|---|---|---:|---|
| Sprint Planning | início de cada sprint | 45 min | objetivo, itens e capacidade acordados |
| Sincronização | 2 vezes por semana, online | 15 min | progresso, próximo passo e impedimentos |
| Refinamento | semanal | 30 min | histórias pequenas, aceites e estimativas revisados |
| Review | fim da sprint | 30 min | incremento demonstrado contra os critérios |
| Retrospectiva | após a review | 30 min | fatos, causas e até 3 ações com responsável/prazo |

Discussões rápidas ocorrem no canal da equipe. Decisões permanentes são registradas em issue, PR ou ADR; mensagem de chat não é fonte definitiva.

### Planning da Sprint 2 — 03/11/2026

A reunião ocorreu em 03/11/2026, das 14h às 14h50 (America/Fortaleza), com Leonardo, Arthur, Matheus e Bianca presentes. Leonardo confirmou os horários, a realização, a participação de todos e o consenso com o planejamento inicial na [issue #65](https://github.com/AndreMattheus/FrioVax/issues/65). A duração real foi de 50 minutos; o acordo previa 45 minutos.

Foi mantido o planejamento cadastrado em 24/10: objetivo, escopo, prioridades, estimativas, responsáveis e capacidade/compromisso da Sprint 2, sem alterações informadas. A referência é a milestone **Sprint 2 — MVP final, telemetria e entrega integrada**, com US04–US10 (#4–#10), suas 35 tarefas e prazo de 30/11/2026 às 23:59 de Fortaleza. O mapa de issues e as views do Project estão registrados na issue #65.

A primeira fila permanece a do planejamento inicial: S2-01 (#65) em Sprint Backlog e os demais itens em Backlog. Essa é a fila inicial acordada, não uma descrição permanente do quadro; o avanço segue dependências e WIP 8/4/2.

Na Sprint 2, cada sincronização começa pela fila de PRs aguardando revisão e pelo WIP de `Em revisão`. A equipe identifica quem fará as revisões pendentes antes de discutir novos cartões. Essa rotina foi acordada na [retrospectiva da Sprint 1](retrospectiva-01.md#acordos-para-a-sprint-2).

## Papéis

- **Product Owner — Bianca:** ordena backlog, esclarece valor e valida aceites.
- **Scrum Master — Leonardo:** facilita cerimônias, acompanha fluxo e remove impedimentos.
- **Arquitetura/Go — Matheus:** conduz contratos e coerência da divisão entre serviços.
- **Qualidade/DevOps — Arthur:** acompanha testes, ambiente reproduzível e CI.
- **Developers — todos:** implementam verticalmente, testam, documentam, revisam e demonstram.

Os papéis de coordenação podem ser reavaliados na retrospectiva; não conferem propriedade exclusiva de arquivos.

## Fluxo Kanban e WIP

O fluxo é acompanhado no [FrioVax Project — Board Principal](https://github.com/users/AndreMattheus/projects/1).

| Coluna | Entrada | Saída | WIP |
|---|---|---|---:|
| Backlog | história registrada | priorizada e refinada | sem limite |
| Sprint Backlog | comprometida na planning | alguém inicia o trabalho | 8 |
| Em progresso | responsável definido | PR aberto e pronto para revisão | 4 |
| Em revisão | CI verde e revisor solicitado | aprovado e integrado | 2 |
| Pronto | Definição de Pronto atendida | permanece como evidência | sem limite |

Se uma coluna atingir o limite, a equipe ajuda a concluir trabalho existente antes de iniciar outro item. Item bloqueado recebe a label `blocked`, com motivo e data registrados na issue; continua na coluna correspondente e contando no WIP. Ao remover o impedimento, registrar a resolução e retirar a label.

## Definição de Pronto

Um item só chega a **Pronto** quando:

- os critérios de aceitação foram demonstrados;
- testes relevantes foram criados ou atualizados e passam;
- `mise run ci` passa e o workflow está verde;
- código, contrato e documentação permanecem coerentes;
- não há segredo, dado pessoal ou alerta crítico conhecido;
- o PR referencia a issue, informa como verificar e recebeu uma aprovação;
- comentários de revisão foram resolvidos;
- a branch foi integrada em `main` e o cartão foi atualizado.

Na Sprint 2, o PR inclui `Closes #<número>` e recebe aprovação do revisor da dupla ou de substituto identificado no PR. O Project registra Start date e Finished date reais, sem copiar Planned start ou Target date como se fossem execução. Apoio material de IA é declarado em `docs/uso-de-ia.md` no mesmo PR.

## Revisão e branches

O acordo da Sprint 1 previa revisão primária em anel: Bianca revisa Arthur; Arthur revisa Leonardo; Leonardo revisa Matheus; Matheus revisa Bianca. Na Sprint 2, as duplas fixas são **Leonardo ⇄ Arthur** e **Matheus ⇄ Bianca**, com Leonardo como apoio. Outro integrante pode assumir para evitar espera, com a substituição registrada no PR, mas ninguém aprova o próprio PR.

O revisor primário usa a Definição de Pronto como roteiro. Na sincronização, a equipe confirma quem fará cada revisão pendente; outro integrante pode assumir para evitar espera.

Branches são curtas e partem de `main`: `feat/<issue>-descricao`, `fix/<issue>-descricao` ou `docs/<issue>-descricao`. Merge preferencial por squash após uma aprovação e CI verde. Mudança arquitetural relevante exige ADR.

## Ferramentas e evidências

- GitHub: código, issues, PRs, revisões e decisões.
- [GitHub Project](https://github.com/users/AndreMattheus/projects/1): prioridade, estimativa, responsável, sprint e fluxo.
- GitHub Actions: CI em push e pull request.
- mise e Docker Compose: ambiente reproduzível.
- Canal online da equipe: comunicação síncrona e avisos.
- Markdown no repositório: proposta, ADRs, retrospectivas, métricas e uso de IA.

Cada integrante deve, por sprint, contribuir em semanas distintas, autorar ao menos um PR integrado e revisar ao menos um PR de outra pessoa. Itens devem ser ligados aos PRs para preservar rastreabilidade.

## Estimativa, qualidade e métricas

Estimativas usam planning poker e pontos 1, 2, 3, 5, 8 e 13. História de 13 pontos deve ser dividida antes de entrar na sprint. A equipe acompanhará throughput, lead time, cycle time e WIP a partir do quadro, sem transformar pontos em horas.

Práticas XP iniciais: integração contínua, revisão, refatoração, testes automatizados e pair programming quando a tarefa envolver risco ou conhecimento concentrado. Pair programming registra `Co-authored-by`.

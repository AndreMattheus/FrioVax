# Acordo de processo

## Cadência e cerimônias

A cadência segue o cronograma das disciplinas: Sprints 1 e 2 com três semanas, Sprint 3 com quatro semanas e bloco final com três semanas. O planejamento ocorre no primeiro dia útil do bloco; revisão e retrospectiva acontecem até 48 horas antes da entrega.

| Cerimônia | Frequência | Duração | Resultado esperado |
|---|---|---:|---|
| Sprint Planning | início de cada sprint | 45 min | objetivo, itens e capacidade acordados |
| Sincronização | 2 vezes por semana, online | 15 min | progresso, próximo passo e impedimentos |
| Refinamento | semanal | 30 min | histórias pequenas, aceites e estimativas revisados |
| Review | fim da sprint | 30 min | incremento demonstrado contra os critérios |
| Retrospectiva | após a review | 30 min | fatos, causas e até 2 ações com responsável/prazo |

Discussões rápidas ocorrem no canal da equipe. Decisões permanentes são registradas em issue, PR ou ADR; mensagem de chat não é fonte definitiva.

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

Se uma coluna atingir o limite, a equipe ajuda a concluir trabalho existente antes de iniciar outro item. Item bloqueado recebe marcador, motivo e data; continua contando no WIP.

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

## Revisão e branches

Revisão primária em anel: Bianca revisa Arthur; Arthur revisa Leonardo; Leonardo revisa Matheus; Matheus revisa Bianca. Outro integrante pode assumir para evitar espera, mas ninguém aprova o próprio PR.

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

# Sprint 0 — Checklist único de entrega

Checklist integrado de **DIM0547 — Desenvolvimento de Sistemas Web II** e **DIM0510 — Processos de Software**.

**Prazo:** 11/09/2026, às 23:59.
**Produto e equipe:** FrioVax.
**Coorte:** B — apresentações online.
**Situação verificada em:** 04/09/2026.

Legenda: `[x]` concluído e verificado; `[ ]` ainda precisa ser concluído. As marcações **Web II**, **Processos** e **Ambas** indicam de onde vem a exigência.

## Checklist integrado

- [ ] **[Ambas] Publicar o repositório `friovax` no GitHub como público.**
  - [x] Repositório Git inicializado localmente com branch `main`.
  - [x] Criar o repositório remoto.
  - [ ] Adicionar os quatro integrantes como colaboradores.
  - [x] Confirmar que o repositório pode ser acessado sem autenticação.

- [ ] **[Ambas] Finalizar o `README.md`.**
  - [x] Nome do grupo e do produto.
  - [x] Nomes completos e matrículas dos quatro integrantes.
  - [x] Papéis propostos para a equipe.
  - [x] Coorte B declarada.
  - [x] Integração entre Web II e Processos declarada.
  - [x] Instruções para executar `build`, `test` e os serviços.
  - [ ] Informar o usuário GitHub de Arthur.
  - [x] Informar o usuário GitHub de Bianca.
  - [x] Informar o usuário GitHub de Leonardo.
  - [x] Informar o usuário GitHub de Matheus.
  - [ ] Adicionar o link do GitHub Project.
  - [ ] Adicionar os links dos dois vídeos.

- [ ] **[Ambas] Finalizar `docs/proposta.md` com no máximo 5 páginas.**
  - [x] Visão no formato solicitado pelo professor.
  - [x] Público-alvo, problema delimitado e evidência de que o problema existe.
  - [x] MVP com itens dentro e fora do escopo.
  - [x] Hipótese de valor.
  - [x] Critérios objetivos para considerar o MVP pronto.
  - [x] Entidades principais e relações.
  - [x] Escolha de Java 25 com Quarkus e justificativa em relação a Kotlin/Ktor.
  - [x] Divisão de responsabilidades entre Java e Go.
  - [x] Equipe, matrículas, papéis, Coorte B e integração entre disciplinas.
  - [ ] Inserir o link definitivo do GitHub Project.
  - [ ] Equipe revisar o texto e confirmar que todos conseguem explicá-lo.
  - [x] Conferir a paginação final em PDF ou impressão para garantir o limite de 5 páginas.

- [ ] **[Ambas] Criar e configurar o GitHub Project.**
  - [ ] Criar as colunas `Backlog`, `Sprint Backlog`, `Em progresso`, `Em revisão` e `Pronto`.
  - [ ] Configurar os limites: Sprint Backlog 8, Em progresso 4 e Em revisão 2.
  - [ ] Criar campos de prioridade, pontos, sprint, responsável e ID.
  - [ ] Tornar o quadro acessível e adicionar seu link ao README e à proposta.

- [ ] **[Ambas] Publicar o backlog inicial no GitHub Project.**
  - [x] Dez histórias de usuário preparadas em `docs/backlog.md`.
  - [x] Todas as histórias priorizadas como P1 ou P2.
  - [x] Todas as histórias estimadas em pontos.
  - [x] Critérios de aceitação escritos para as histórias.
  - [ ] Criar pelo menos cinco histórias como itens do GitHub Project.
  - [ ] Conferir que pelo menos três possuem estimativa e que todas possuem prioridade.
  - [ ] Colocar as histórias da Sprint 1 no `Sprint Backlog` e as demais no `Backlog`.

- [ ] **[Processos] Configurar papéis e acordo de trabalho.**
  - [x] Product Owner, Scrum Master e responsabilidades técnicas propostos.
  - [x] Revisor principal de cada integrante definido.
  - [x] Cadência e cerimônias com frequência e duração documentadas.
  - [x] Definição de Pronto documentada.
  - [x] Ferramentas, branches, PRs e limites de WIP documentados.
  - [ ] Equipe confirmar os papéis e o anel de revisão.
  - [ ] Exibir os limites de WIP também no GitHub Project.

- [x] **[Web II] Criar a estrutura mínima do monorepo.**
  - [x] Diretórios `api/`, `services/`, `protos/` e `docs/`.
  - [x] `mise.toml` com tarefas `build` e `test`.
  - [x] `docker-compose.yml` presente e validado por `docker compose config`.
  - [x] Esqueleto Java 25/Quarkus compilável.
  - [x] Esqueleto Go compilável.
  - [x] `protos/` reservado para os contratos da Sprint 2, sem antecipar a integração gRPC.

- [x] **[Web II] Validar o projeto localmente.**
  - [x] `mise run build` passa para Java e Go.
  - [x] `mise run test` passa para os dois stacks.
  - [x] `mise run ci` passa localmente.

- [ ] **[Web II] Deixar o CI verde no GitHub.**
  - [x] Workflow configurado para `push` e `pull_request`.
  - [x] Jobs separados para Java/Quarkus e Go.
  - [x] Publicar o código e observar a execução dos dois jobs.
  - [ ] Corrigir qualquer diferença entre o ambiente local e o GitHub Actions.
  - [x] Confirmar os dois jobs verdes na branch `main` para o commit inicial.
  - [ ] Confirmar novamente os dois jobs verdes após publicar as correções técnicas.

- [ ] **[Processos] Criar evidências de atividade no repositório.**
  - [ ] Criar as tarefas da Sprint 0 como issues e colocá-las no quadro.
  - [ ] Distribuir trabalho real entre os quatro integrantes.
  - [ ] Cada integrante autorar e integrar pelo menos um pull request.
  - [ ] Cada integrante revisar ao menos um pull request de outra pessoa.
  - [ ] Garantir que pelo menos metade dos PRs recebeu aprovação de outro integrante.
  - [ ] Vincular issues e itens do Project aos respectivos PRs.
  - [ ] Fazer commits em mais de uma semana da Sprint, sem concentrar tudo no último dia.
  - [ ] Usar `Co-authored-by` quando houver pair programming.

- [ ] **[Web II] Gravar e publicar o vídeo de aproximadamente 5 minutos.**
  - [x] Roteiro preparado em `docs/roteiro-video-web2.md`.
  - [ ] Todos os quatro integrantes devem falar.
  - [ ] Apresentar equipe, problema e visão.
  - [ ] Justificar Java/Quarkus em relação a Kotlin/Ktor.
  - [ ] Explicar a divisão entre o serviço principal e Go.
  - [ ] Mostrar o monorepo e o CI verde.
  - [ ] Resumir MVP, backlog e riscos.
  - [ ] Publicar no YouTube como não listado ou público e adicionar o link ao README.

- [ ] **[Processos] Gravar e publicar o vídeo de aproximadamente 5 minutos.**
  - [x] Roteiro preparado em `docs/roteiro-video-processos.md`.
  - [ ] Todos os quatro integrantes devem falar.
  - [ ] Apresentar equipe, problema, visão e MVP.
  - [ ] Mostrar o GitHub Project configurado.
  - [ ] Explicar cadência, cerimônias, Definição de Pronto, papéis e WIP.
  - [ ] Publicar no YouTube como não listado ou público e adicionar o link ao README.

- [ ] **[Ambas] Fazer a conferência final antes do prazo.**
  - [x] Registro inicial de uso de IA criado em `docs/uso-de-ia.md`.
  - [ ] Atualizar o registro caso a equipe use outras ferramentas ou prompts relevantes.
  - [ ] Confirmar que nenhum segredo, token, credencial ou arquivo `.env` foi versionado.
  - [ ] Testar as instruções do README em um clone limpo.
  - [ ] Confirmar que proposta, quadro, CI e vídeos estão acessíveis pelos links do README.
  - [ ] Confirmar que todos os integrantes entendem o que foi entregue e conseguem responder perguntas.
  - [ ] Garantir que o último commit válido esteja em `main` antes de 11/09/2026 às 23:59.

## Pendências bloqueadoras atuais

1. Usuário GitHub de Arthur.
2. Acesso dos quatro integrantes ao repositório.
3. GitHub Project com histórias, campos, colunas e WIP.
4. Issues, commits, PRs e revisões reais dos integrantes.
5. Publicação das correções técnicas e nova confirmação do CI verde em `main`.
6. Confirmação dos papéis propostos pela equipe.
7. Gravação e publicação dos dois vídeos.
8. Inclusão dos links definitivos no README e na proposta.
9. Teste final das instruções em clone limpo.

Enquanto essas nove pendências não forem resolvidas, a Sprint 0 ainda não está pronta para entrega, embora os documentos e o esqueleto técnico já estejam preparados localmente.

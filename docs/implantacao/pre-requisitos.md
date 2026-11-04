# Registro de pré-requisitos — implantação

Referência: [ADR 0004](../decisoes/0004-implantacao.md), [#87](https://github.com/AndreMattheus/FrioVax/issues/87) e [#88](https://github.com/AndreMattheus/FrioVax/issues/88). Atualizado em 04/11/2026. Este registro não contém credenciais.

## Contas e responsáveis

| Recurso | Plano decidido | Titular indicado | Estado confirmado | Fonte |
|---|---|---|---|---|
| Render, conta/workspace e Web Service | Free, uma réplica | @AndreMattheus | Titular indicado; criação e configuração pendentes de confirmação | Informação do solicitante na preparação da #87 em 04/11/2026 |
| Neon, conta/projeto PostgreSQL | Free | @AndreMattheus | Titular indicado; criação e configuração pendentes de confirmação | Informação do solicitante na preparação da #87 em 04/11/2026 |

Arthur (@arthurFeijo) conduz configuração/ensaio; Leonardo (@leonardonadson) revisa. O titular cria ou confirma as contas, aceita termos e concede somente os acessos necessários. Não compartilhar login, senha ou código de autenticação no PR/chat. Para confirmar criação, registrar aqui data, usuário titular e referência de confirmação sanitizada (comentário na issue/PR ou revisão).

## Portões antes de iniciar deploy

- [x] Dependência #65 concluída (consulta ao GitHub em 04/11/2026).
- [x] Titular indicado para Render e Neon: @AndreMattheus.
- [ ] Titular confirmou criação das duas contas gratuitas e acesso de operação.
- [ ] Regiões efetivas registradas: Render **pendente**; Neon **pendente**.
- [ ] Titular confirmou serviço Render Docker com contexto `api/`, uma réplica, porta 10000, autodeploy desligado e health check `/api/status`.
- [ ] Titular confirmou Neon PostgreSQL 17, conexão direta com TLS validado, papéis de migração/runtime e grants.
- [ ] GitHub environment `producao` criado e restrito a `main`; aprovação configurada se disponível no plano.
- [ ] Secrets do environment cadastrados: `FLYWAY_URL`, `FLYWAY_USER`, `FLYWAY_PASSWORD`, `RENDER_DEPLOY_HOOK_URL`, `RENDER_API_KEY` (somente nomes neste registro).
- [ ] Variable `API_BASE_URL` cadastrada; variáveis/segredos do runtime Render cadastrados conforme ADR, incluindo `QUARKUS_FLYWAY_MIGRATE_AT_START=false`.
- [ ] Estratégia de backup confirmada pelo titular: armazenamento privado **pendente**; retenção **pendente**; acesso/restauração **pendente**.
- [ ] Titular e revisor reconheceram o risco de mutação anônima até #81 e limitaram a demonstração a dados fictícios.
- [ ] @AndreMattheus confirmou o plano operacional; @leonardonadson aprovou a ADR/PR.

## Evidência técnica e conclusão da #87

- [x] Ensaio local executado em 04/11/2026: cinco verificações passaram; resultado e CI local registrados em [validacao.md](validacao.md).
- [ ] PR inclui `Closes #87`, CI verde, revisão aprovada e comentários resolvidos.
- [ ] Project contém Start date real; Finished date somente após conclusão real, sem copiar datas planejadas. Tentativa de registrar o início deste trabalho em 04/11/2026 recusada: token da sessão sem escopo de escrita `project`. Arthur deve registrar a data ou conceder esse escopo; nenhuma data foi alterada pela tentativa.
- [ ] PR integrado em `main` e cartão atualizado conforme [Definição de Pronto](../processo.md#definição-de-pronto).

Os campos pendentes não são evidência de conta criada nem de segredo cadastrado. Não iniciar deploy nem declarar a issue pronta enquanto os portões aplicáveis estiverem pendentes. A #88 entrega workflow e publicação; seu sucesso não deve ser inventado neste registro.

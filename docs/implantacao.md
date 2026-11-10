# Publicação da API — #88

O serviço existente é https://friovax.onrender.com. Render Free suspende o
processo por inatividade; a próxima requisição o inicia. O smoke tolera até
cinco minutos de partida a frio. Os dados devem permanecer no Neon.

## Automação

O push em `main` executa o CI existente. Ao terminar com sucesso, `deploy.yml`
recebe `workflow_run` e publica o **head_sha desse CI**, sem confundir com o SHA
do evento `workflow_run`. Também aceita execução manual em `main`, exigindo CI
verde de push para o mesmo SHA. Pull requests não recebem segredos de produção.
Com backup manual, a tentativa automatica para no preflight sem confirmacao;
ela nao altera o banco. O operador faz o ensaio e libera pela execucao manual.

O job usa `environment: producao` e exclusão mútua sem cancelar migração em curso.
Registra um GitHub Deployment antes do preflight; erro de configuração, migração,
Render ou smoke gera `failure`. Somente Render `live` no SHA solicitado, saúde
do processo, prontidão do banco e consulta autenticada permitem `success`.
O environment do próprio job também aparece no histórico do GitHub.
O hook recebe `ref=SHA`, e deploy enfileirado é localizado pela data e SHA.

Flyway 12.0.0 está fixado por digest. Executa validate permitindo somente pending,
migrate e validate estrito. `clean` fica desabilitado. Saída bruta de Flyway não é
publicada. Falha exige diagnóstico privado pelo titular; não executar repair
automático. O script HTTP não segue redirects com credenciais.

O perfil de produção desabilita migração no startup e exige `DB_URL`, `DB_USER`
e `DB_PASSWORD`, sem fallback local. O Compose habilita explicitamente migração
para preservar seu funcionamento local. O serviço Go não participa da publicação.

## Configuração antes da primeira execução

Matheus, proprietario do repositorio pessoal, cadastra os itens abaixo; Arthur
prepara os valores em canal privado. Nao publicar credenciais em issue ou PR.

No environment GitHub `producao`:

| Tipo | Nome | Uso |
|---|---|---|
| Secret | `FLYWAY_URL`, `FLYWAY_USER`, `FLYWAY_PASSWORD` | Papel de migração, JDBC direto e TLS conforme ADR |
| Secret | `RENDER_DEPLOY_HOOK_URL`, `RENDER_API_KEY` | Publicar e consultar Render |
| Secret | `SMOKE_LOGIN`, `SMOKE_PASSWORD` | Conta AUDITOR para consulta, sem permissão de mutação |
| Variable | `API_BASE_URL` | `https://friovax.onrender.com` |
| Variable | `RENDER_SERVICE_ID` | ID público `srv-...`, copiar do serviço existente |

`FLYWAY_USER` permanece `neondb_owner`; `FLYWAY_URL` deve apontar para o banco
`neondb` da branch production, sem pooler, nunca para os hosts dos ensaios.
`SMOKE_LOGIN` deve ser `auditor` e `SMOKE_PASSWORD` deve coincidir com a senha
de `AUTH_DEMO_AUDITOR` no Render; nao e senha do banco.

Checklist consolidado do GitHub:

- [x] Nomes dos sete secrets e das duas variables conferidos em 10/11/2026.
- [x] Environment restrito exclusivamente a branch `main`, conforme metadados.
- [ ] Revisor obrigatorio no environment, se disponivel; nao havia regra na consulta.
- [ ] Confirmar na execucao a permissao `deployments: write` solicitada pelo workflow.
- [ ] Integrar somente depois de CI verde, revisao de Leonardo e configuracao pronta.

Os valores de secrets nao foram lidos. Sua validade depende do teste real.
A aprovacao de Leonardo no PR permanece obrigatoria mesmo sem regra no environment.

Restringir o environment a `main` e habilitar revisão quando disponível.
No Render conferir Dockerfile `api/Dockerfile`, contexto `.`, porta 10000,
host `0.0.0.0`, health check `/api/status`, autodeploy desligado,
`QUARKUS_FLYWAY_MIGRATE_AT_START=false`, credenciais de runtime e JWT próprios.
Não usar chaves de teste ou senhas locais. Conferir grants para tabelas novas.

Configuracao sob responsabilidade de Arthur no Render/Neon:

- O papel `friovax_runtime` foi criado por SQL, com senha propria e sem atributos
  administrativos ou associacao a `neon_superuser`, conforme relato de Arthur.
- Depois de migracoes e grants, trocar `DB_USER` e `DB_PASSWORD` no Render para
  esse papel. A troca e sua validacao ainda nao foram confirmadas.
- `JWT_PRIVATE_KEY` e `JWT_PUBLIC_KEY` apontam para os secret files proprios;
  `JWT_ISSUER=friovax-api`. Arthur confirmou seu cadastro e os caminhos.
- Para provisionar contas, `AUTH_DEMO_ENABLED=true` exige todas as tres senhas:
  `AUTH_DEMO_ADMINISTRADOR`, `AUTH_DEMO_OPERADOR`, `AUTH_DEMO_AUDITOR`.
  Arthur confirmou cadastro; funcionamento depende da primeira publicacao.
- CI concede permissoes explicitas nas tabelas e sequencias apos migrar e recusa
  runtime administrativo ou com escrita no historico Flyway. Novas tabelas
  exigem revisao de `docs/scripts/runtime-grants.sql`.
  A base atual de main inclui V4 e `politicas_termicas`; os grants incluem
  consulta/insercao nessa tabela e uso de sua sequencia. Os ensaios remotos
  anteriores cobriram V1–V3; renovar o ensaio remoto com V4 antes da publicacao.

**Recuperacao manual proposta:** Arthur escolheu executar backup/restauracao
antes de cada publicacao; ajuste registrado na ADR, pendente de Leonardo.
Sem outro deploy em andamento, suspender escritas e novas publicacoes, criar
backup privado e verificar restauracao isolada. Manter a suspensao ate o smoke.
Em Run workflow informar SHA completo, SHA256 do dump e horario ISO 8601 do fim
do ensaio (com fuso) e marcar a declaracao de escritas suspensas. Ensaio precisa
ter no maximo seis horas. Inputs nao sao secrets: nunca inserir credenciais.
O CI confere formato, SHA e prazo, mas nao inspeciona o arquivo nem bloqueia
escritas. A responsabilidade por essa verificacao permanece com o operador.
Nao cadastrar BACKUP_* como variaveis permanentes. Repetir apos novas escritas.
Definir acesso, retencao e copia privada adicional conforme proposta na ADR.

## Redeploy e persistência

Para criar o backup privado da origem confirmada, executar no PowerShell
`powershell -NoProfile -File docs/scripts/backup-neon.ps1`. O script pede usuário
e senha interativamente, valida TLS e salva em `%LOCALAPPDATA%\FrioVax\backups`,
fora do repositório. Restaura as variáveis de ambiente ao terminar. A leitura do
arquivo por `pg_restore --list` não comprova restauração: ainda é obrigatório
restaurar em destino isolado e comparar o conteúdo. Definir retenção e permissões
da pasta com o titular; o arquivo pode conter dados sensíveis, não compartilhá-lo.

1. Com conta ADMINISTRADOR e dados fictícios, cadastrar uma câmara na API pública.
   Guardar ID e resposta sem tokens como referência; não usar a conta AUDITOR.
2. Conferir backup/recuperação atual e os pré-requisitos acima.
3. Em Actions → Publicar API → Run workflow, escolher `main` e informar os quatro
   campos do backup. O SHA deve corresponder ao commit selecionado de main.
4. Aguardar o job e consultar a mesma câmara pelo ID. Comparar campos e datas.
5. Registrar no PR SHA, ID Render, ID GitHub Deployment, horários reais,
   referência ao run e resultado da comparação. Sem essa execução, persistência
   remota continua pendente.

Consulta pública para DORA:

```sh
gh api 'repos/AndreMattheus/FrioVax/deployments?environment=producao'
gh api repos/AndreMattheus/FrioVax/deployments/ID/statuses
```

## Falha e rollback

Falha de migração interrompe antes do hook. Testar SQL inválido somente em
PostgreSQL/branch descartável. Conferir que o teste falhou e que o hook não foi
executado. Nunca apontar esse ensaio para produção.

Para falha da aplicação, suspender novas publicações e selecionar no Render um
deploy anterior compatível com o banco atual. Rollback restaura o código, não o
banco. Conferir variáveis, SHA, prontidão e os dados de referência; registrar um
deployment de rollback e seu resultado no GitHub conforme ADR 0004. Não reutilizar
o workflow de migração para um SHA antigo com migrações ausentes do histórico.
Rollback manual deve ocorrer sem outro job de produção em execução.

## Evidência de preparação — 10/11/2026

- A consulta autenticada aos metadados confirmou #87 CLOSED.
- A URL pública respondeu `/api/status`: `service=friovax-api`, `status=UP`.
- `/q/health/ready` retornou `UP`, incluindo `Database connections health check`.
  Isso confirma banco acessível, mas não identifica por si só o provedor Neon.
- Os cinco secrets originais e `API_BASE_URL` existem. Não foram lidos valores
  de secrets. O environment estava sem proteção de branches/revisores.
- Em conferencia posterior no mesmo dia, foram confirmados `SMOKE_LOGIN`,
  `SMOKE_PASSWORD`, `RENDER_SERVICE_ID` e restricao de deployments a `main`.
  Nao ha regra de revisor obrigatorio configurada no environment. Os valores
  secretos e seu funcionamento continuam pendentes da execucao real.
- A consulta de deployments `producao` retornou lista vazia nesse momento.
- `GET /api/camaras` sem token respondeu HTTP 500. O inventário público ainda
  nao esta validado: logs relatados por Arthur indicaram tabela `camaras` ausente.
  Saude verde nao comprova operacao do inventario.
- Não executados: mutação autenticada pública, redeploy real, comparação de
  persistência e rollback remoto. Nenhuma publicação foi disparada nesta preparação.
- Ensaio local com a imagem Flyway fixada e PostgreSQL 17 descartável: V1–V3
  aplicadas; V999 com SQL inválido falhou e foi revertida, preservando no histórico
  somente V1–V3. Nenhuma conexão de produção foi usada nesse ensaio.
- Os testes de contrato cobrem interrupção por falha, TLS/pooler, backup de outro
  SHA, CI ausente, versão Render incorreta, fila, cancelamento e bloqueio anônimo.
  Actionlint 1.7.7 aprovou os workflows. A suíte Java completa ainda depende do CI.
- Nos ensaios remotos relatados por Arthur, o PostgreSQL e 18.6 (a ADR previa 17).
  V1–V3 foram aplicadas no teste; V999 invalida foi revertida, sem mudar o historico.
- Backup privado `production-20261110T191006024Z.dump`, SHA256
  `9953317AA81B41DA63B066DB96EDBC9965C11D833CFDD9FF46E5B46A81415DF6`, foi
  restaurado sem erro no banco isolado `restore_88_20261110191437408`.
  Listas de tabelas de aplicacao vazias na origem e destino: isso nao comprova
  recuperacao de registros. Repetir com dados ficticios apos a primeira publicacao.

Referências: [ADR 0004](decisoes/0004-implantacao.md),
[hooks Render](https://render.com/docs/deploy-hooks),
[API de deploys](https://api-docs.render.com/reference/list-deploys),
[Flyway com Neon](https://neon.com/docs/guides/flyway).

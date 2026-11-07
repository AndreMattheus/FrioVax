# ADR 0004 — Implantação da API

- **Status:** Proposta para revisão; contas confirmadas, com pré-requisitos de publicação pendentes.
- **Data:** 04/11/2026
- **Responsável:** @arthurFeijo (Qualidade/DevOps)
- **Revisor:** @leonardonadson
- **Issue:** [S2-24 / #87](https://github.com/AndreMattheus/FrioVax/issues/87)
- **Implementação da publicação:** [S2-25 / #88](https://github.com/AndreMattheus/FrioVax/issues/88)
- **Decisão relacionada:** [ADR 0003 — Serviço Go de telemetria e regras térmicas](https://github.com/AndreMattheus/FrioVax/blob/main/docs/decisoes/0003-servico-go-telemetria.md), integrada à `main` pelo [PR #102](https://github.com/AndreMattheus/FrioVax/pull/102). As ADRs 0001–0002 também foram movidas para `docs/decisoes/` nessa entrega da #67.

## Contexto

O MVP acadêmico precisa de uma URL HTTPS para avaliar o inventário persistente, sem contratar recursos pagos nem tornar o serviço Go público. O Java é a autoridade dos dados; o Go analisa janelas térmicas, conforme a ADR 0003. A indisponibilidade dessa análise não pode impedir o inventário nem causar reinícios permanentes da API.

O repositório já possui container Java, PostgreSQL 17, migrações Flyway e CI. O Compose completo depende da saúde do Go, o que é útil para a avaliação integrada local, mas não é a topologia de publicação. No código atual, `telemetry.base-url` é apenas configuração: não há chamada remota na inicialização nem em `GET /api/status`.

## Decisão

### Topologia e planos

Publicar **uma réplica** da API Java como Web Service Docker no **Render Free** e manter os dados em um projeto **Neon Free / PostgreSQL 17**. Regiões confirmadas pelo titular em 05/11/2026: Render em **Virginia (US East)** e Neon em **AWS South America East 1 (São Paulo)**. Avaliar a latência dessa combinação na #88; eventual mudança de região deve ser registrada nesta decisão. Não habilitar cobrança ou upgrade automático para contornar limites.

O Render constrói o commit aprovado usando `api/Dockerfile`, com contexto Docker `api/` (root directory `api`, Dockerfile `./Dockerfile`). O SHA completo identifica a versão; registrar também o ID do deploy e, quando disponível, o digest da imagem. A réplica escuta em `0.0.0.0`, com `QUARKUS_HTTP_HOST=0.0.0.0` e `QUARKUS_HTTP_PORT=10000`; configurar a porta correspondente no serviço. O tráfego externo usa HTTPS com TLS terminado pelo Render; banco e API não compartilham disco.

O filesystem do container é efêmero: não guardar dados oficiais, backups ou uploads nele. O Neon conserva os dados entre redeploys. Render Free pode suspender a instância por inatividade e não oferece alta disponibilidade; partida a frio e indisponibilidade temporária são aceitáveis nesta demonstração. Verificar cotas e disponibilidade dos planos antes de provisionar. [Referências Render: Web Services](https://render.com/docs/web-services), [Free](https://render.com/docs/free).

### Titulares e liberação

Em **05/11/2026**, Arthur confirmou nesta preparação da #87 que é o titular (**@arthurFeijo**) das contas Render e Neon, ambas criadas em **04/11/2026**, nos planos gratuitos. Arthur também assumiu a administração dos serviços e dos backups. Leonardo permanece como revisor. Este registro se baseia no relato do responsável, sem acesso a credenciais ou verificação dos painéis.

O registro de pré-requisitos abaixo é parte desta ADR. Atualizá-lo com estado real, data e referência sem segredos antes do primeiro deploy. Conta criada, segredo cadastrado e revisão humana são verificações distintas. Não declarar a #87 pronta enquanto os aceites externos e a Definição de Pronto não estiverem demonstrados.

A data do primeiro acionamento de deploy no Render não foi registrada nesta evidência. Assim, a exigência de registrar os titulares antes de iniciar o deploy não tem sua ordem cronológica comprovada; isso não demonstra, por si só, descumprimento nem publicação bem-sucedida da API.

| Recurso | Plano confirmado | Titular | Criação | Região confirmada |
|---|---|---|---|---|
| Render | Free; decisão de uma réplica mantida | @arthurFeijo | 04/11/2026 | Virginia (US East) |
| Neon | Free | @arthurFeijo | 04/11/2026 | AWS South America East 1 (São Paulo) |

Arthur informou como cadastradas no Render as variáveis **`AUTH_DEMO_ENABLED`**, **`QUARKUS_FLYWAY_MIGRATE_AT_START`**, **`QUARKUS_HTTP_HOST`** e **`QUARKUS_HTTP_PORT`**. São configurações de execução, não credenciais do banco ou de implantação. Foram confirmados somente seus nomes; seus valores não foram informados nem verificados. O cadastro de credenciais do banco e chaves JWT no Render permanece sem confirmação.

Em **06/11/2026**, Arthur confirmou o cadastro no GitHub dos cinco secrets previstos para o environment `producao`: **`FLYWAY_URL`**, **`FLYWAY_USER`**, **`FLYWAY_PASSWORD`**, **`RENDER_DEPLOY_HOOK_URL`** e **`RENDER_API_KEY`**. Também confirmou que **`API_BASE_URL`** foi movida de Environment secrets para **Environment variables**. Na mesma data, a conferência dos metadados pela CLI do GitHub confirmou os cinco nomes em secrets e `API_BASE_URL` em variables, sem consultar os valores. Sua validade e o funcionamento da conexão, migração e implantação serão verificados na #88.

Para a publicação da #88, o titular ainda deve confirmar neste registro ou no PR, sem publicar valores:

- Serviço Render configurado com contexto `api/`, porta 10000, uma réplica, autodeploy desligado e health check `/api/status`.
- Neon PostgreSQL 17 com conexão direta, TLS validado e papéis/grants de migração e runtime.
- Restrições de publicação do environment GitHub `producao` à branch `main` e aprovação humana quando disponível; migração no startup Render desabilitada. O cadastro dos secrets e de `API_BASE_URL` já foi confirmado acima.
- Armazenamento privado, retenção e acesso ao backup definidos, com restauração conferida.
- Risco de mutação anônima até #81 reconhecido pelo titular e revisor, com dados exclusivamente fictícios.

A criação das contas, a titularidade, os planos, as regiões, o responsável pela operação/backups e o cadastro dos secrets e da variável pública no GitHub estão **confirmados por relato do titular**. Os demais itens de configuração e publicação acima continuam pendentes. A responsabilidade pelos backups não comprova que armazenamento, retenção ou restauração remota já estejam configurados. A dependência #65 foi consultada em 04/11/2026 e está concluída.

Na consulta aos metadados do GitHub em **06/11/2026**, o environment `producao` ainda estava sem restrição de branches e sem regras de aprovação configuradas. Aplicar as regras previstas acima antes de usar o workflow de publicação da #88.

### API pública e Go local

O serviço Go **não será publicado**. Avaliar a integração por `docker compose up --build` localmente, com os dois serviços e o PostgreSQL do Compose. Não usar esse Compose para subir a API no Render nem remover sua dependência local de telemetria só para publicar.

A API deve iniciar e atender inventário sem Go. Quando a integração da ADR 0003 for implementada, telemetria desabilitada/ausente deve rejeitar novas análises com erro controlado `503`, sem inventar resultados nem confirmar parcialmente efeitos oficiais. Não chamar Go em observer de startup, liveness ou health check do provedor. Registrar endpoint/flag de desabilitação e testar a degradação na issue da integração antes de publicá-la. Esta ADR não implementa antecipadamente esse contrato.

Até a autorização por perfis da [#81](https://github.com/AndreMattheus/FrioVax/issues/81), as rotas de mutação podem estar anônimas. A URL acadêmica usará **somente dados fictícios**, sem dados pessoais, credenciais de terceiros ou inventário real; qualquer visitante pode alterar esses dados. O titular e o revisor precisam reconhecer esse risco no registro antes da publicação inicial. Dados de demonstração não substituem autenticação/autorização.

### Saúde

Configurar no Render **`GET /api/status`**, que retorna `200` com `service=friovax-api` e `status=UP`. É uma verificação do processo HTTP, sem banco nem Go. Isso evita que ausência de telemetria ou partida a frio do banco provoquem uma sequência de reinícios. Não acrescentar dependências remotas a essa rota.

Separadamente, o smoke do job verifica `GET /q/health/ready` (inclui o datasource) e uma consulta de inventário, com timeout e tentativas limitadas. Se o banco não estiver disponível, o deployment falha e exige investigação; o status do processo sozinho não é evidência de prontidão do banco. Esses endpoints devem permanecer acessíveis ao health check após a autenticação. [Health checks do Render](https://render.com/docs/health-checks).

### Conexão PostgreSQL e TLS

Usar conexão **direta, sem pooler Neon**, tanto para a réplica quanto para Flyway, inicialmente. Uma réplica permite pool Agroal pequeno (`QUARKUS_DATASOURCE_JDBC_MIN_SIZE=0`, `QUARKUS_DATASOURCE_JDBC_MAX_SIZE=5`), sem acrescentar o pooler em modo transacional ao caminho de migração. Definir timeout de aquisição de 30 segundos e tentativas limitadas na automação para tolerar a ativação do Neon.

`DB_URL`/`FLYWAY_URL` têm formato JDBC, não `postgresql://` de libpq: `jdbc:postgresql://HOST:5432/BANCO?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory&connectTimeout=30`. O exemplo é um formato, não uma conexão funcional. Separar usuário e senha em variáveis; nunca embuti-los na URL. A factory Java usa as CAs confiáveis da JVM para validar certificado e hostname. Confirmar a conexão real na #88; não solucionar erro de certificado desligando TLS ou usando factory não validante. Para `psql`/`pg_dump`, usar variáveis `PGHOST`, `PGDATABASE`, `PGUSER`, `PGPASSWORD`, `PGSSLMODE=verify-full` e a CA confiável indicada pelo provedor via `PGSSLROOTCERT`.

O papel de runtime recebe apenas permissões necessárias ao inventário (tabelas e sequências); o papel de migração pode executar DDL e gerenciar o histórico Flyway. Após cada migração, conferir permissões do runtime, incluindo tabelas novas. [Neon: conexões e pooling](https://neon.com/docs/connect/connection-pooling), [pgJDBC: TLS e factory Java](https://jdbc.postgresql.org/documentation/ssl/).

### Segredos e configuração

| Local | Nomes | Finalidade |
|---|---|---|
| Render, variáveis secretas | `DB_URL`, `DB_USER`, `DB_PASSWORD` | Conexão do papel de runtime |
| GitHub Actions, secrets do environment `producao` | `FLYWAY_URL`, `FLYWAY_USER`, `FLYWAY_PASSWORD` | Conexão direta do papel de migração |
| GitHub Actions, secret do environment `producao` | `RENDER_DEPLOY_HOOK_URL` | Disparar deploy; a URL inteira é segredo |
| GitHub Actions, secret do environment `producao` | `RENDER_API_KEY` | Consultar estado e SHA do deploy específico |
| GitHub Actions, variable do environment `producao` | `API_BASE_URL` | URL HTTPS pública, sem credenciais |
| Render, variáveis comuns | `QUARKUS_HTTP_HOST`, `QUARKUS_HTTP_PORT`, `QUARKUS_FLYWAY_MIGRATE_AT_START=false` | Escuta e migração externa |
| Render, variáveis comuns | `QUARKUS_DATASOURCE_JDBC_MIN_SIZE=0`, `QUARKUS_DATASOURCE_JDBC_MAX_SIZE=5`, `QUARKUS_DATASOURCE_JDBC_ACQUISITION_TIMEOUT=30S` | Pool limitado |

Com a #80 integrada à `main`, cadastrar chaves JWT próprias como secret files no Render, apontando `JWT_PRIVATE_KEY`/`JWT_PUBLIC_KEY` para seus caminhos absolutos e definindo `JWT_ISSUER`. Não usar as chaves de teste. Caso se habilite provisionamento demonstrativo, cadastrar suas senhas exclusivamente no Render; nunca copiar a configuração local para produção. Configurar GitHub `producao` para permitir somente a branch `main`, com aprovação humana quando o plano do repositório permitir.

Não versionar valores, `.env` de produção, backups, certificados privados ou hook; não imprimir ambiente, connection string, `curl -v`, `set -x`, screenshots de telas de secrets ou saída bruta de Flyway com detalhes de conexão. Rotacionar segredo exposto. O job mascara os valores antes de uso e publica somente etapa, SHA, resultado e referências sanitizadas; tratar logs de falha como potencialmente sensíveis. O registro de pré-requisitos contém nomes de secrets e confirmação, nunca valores.

### Migração e deploy pelo GitHub Actions

A implementação do workflow pertence à **#88**. O contrato obrigatório é:

1. Disparo por push em `main` ou `workflow_dispatch` restrito a `main`. Checkout de um SHA completo; CI completo verde para esse mesmo SHA. Nenhum segredo de produção em jobs de pull request, inclusive de forks.
2. Job de publicação com **`environment: producao`**, permissões mínimas (`contents: read`, `deployments: write`) e `concurrency: group: producao`, `cancel-in-progress: false`. A região crítica cobre backup, migração, deploy e smoke. Operações manuais também precisam respeitar essa exclusão.
3. Conferir pré-requisitos e versão anterior recuperável. Antes de alterar dados, criar backup lógico protegido e verificar sua recuperação em destino isolado; restringir acesso e retenção fora do repositório e dos artefatos públicos do Actions. Um backup não validado não é garantia de recuperação.
4. Executar **Flyway validate + migrate + validate**, com as migrações do mesmo SHA que será publicado, conexão direta e `cleanDisabled=true`. No validate anterior, permitir somente migrações `pending` com `ignoreMigrationPatterns=*:pending`; depois do migrate, esvaziar os padrões ignorados e exigir validação estrita. Não ignorar checksum divergente ou migração aplicada ausente do código. A versão da CLI deve coincidir com o Flyway resolvido pelo BOM da API (12.0.0 nesta base); fixar imagem por versão e digest na #88. Versões diferentes exigem novo ensaio.
5. Se migração falhar, interromper antes do hook e registrar deployment `failure`. Nunca executar `clean`, reset, baseline sobre banco existente ou `repair` automático. SQL transacional reverte a migração que falhou; instruções não transacionais exigem procedimento específico de recuperação antes da aprovação do PR.
6. Chamar o hook HTTPS secreto com `ref=SHA_COMPLETO`, autodeploy nativo do Render **desligado**, evitando publicar antes da migração ou publicar outro commit. Um HTTP `200`/`202` do hook apenas aceita/enfileira a solicitação; ainda não comprova publicação.
7. Consultar o deploy retornado pela API Render (se enfileirado, localizar o deploy pelo SHA), esperar estado `live` do **mesmo SHA**, depois executar saúde/prontidão e smoke HTTPS. Limitar espera e retentativas; timeout, cancelamento e erro terminam como falha, não sucesso da versão antiga.
8. Somente após isso marcar o GitHub Deployment `success`, com `environment_url` da API. Registrar SHA, horários reais e fontes sem segredos conforme [medição da Sprint 2](../medicao-sprint-2.md). Conferir o evento por `gh api 'repos/AndreMattheus/FrioVax/deployments?environment=producao'` e os statuses do ID retornado.

Com a CLI Flyway instalada na versão acordada, o comando previsto para o job é:

```bash
# FLYWAY_URL, FLYWAY_USER e FLYWAY_PASSWORD já vêm dos secrets do environment.
# Não acrescentar -X nem publicar o arquivo temporário como artefato.
umask 077
log_privado=$(mktemp)
trap 'rm -f "$log_privado"' EXIT
executar_flyway() {
  if ! flyway -locations=filesystem:api/src/main/resources/db/migration \
      -cleanDisabled=true "$@" >"$log_privado" 2>&1; then
    printf 'Falha no Flyway; deploy interrompido.\n' >&2
    exit 1
  fi
}
executar_flyway '-ignoreMigrationPatterns=*:pending' validate
executar_flyway migrate
executar_flyway -ignoreMigrationPatterns= validate
```

No Render, definir `QUARKUS_FLYWAY_MIGRATE_AT_START=false` **antes da primeira publicação**, inclusive rollback. Manter o comportamento local atual (migração no startup em dev/test/Compose). Não desligar globalmente a migração nesta ADR: o workflow substituto será entregue na #88. [Neon/Flyway](https://neon.com/docs/guides/flyway), [Render/deploy hooks e ref](https://render.com/docs/deploy-hooks).

### Rollback e recuperação preservando dados

**Falha da aplicação com banco íntegro:** interromper novas publicações no environment, registrar incidente/SHA atual e escolher o último deploy aprovado que seja compatível com o esquema atual. No painel Render: serviço → Deploys → deploy bem-sucedido → Rollback → confirmar. Conferir configurações/segredos do deploy escolhido (o rollback pode reutilizar variáveis antigas); não reintroduzir migração no startup ou credenciais revogadas. Verificar SHA, status, prontidão e os IDs/valores dos dados de referência após o retorno. Registrar deployment de rollback e seu resultado também no GitHub, pela mesma automação ou registro explícito na #88.

O rollback retorna o **código**, não desfaz o PostgreSQL. Exigir migrações aditivas compatíveis com a versão em produção e com a versão de retorno: adicionar antes de remover, separar mudança de dados e remoção em entregas distintas. Não modificar SQL já aplicado. Correção de esquema é uma **nova migração versionada** revisada, sem apagar registros. Se a versão anterior não é compatível, não executar rollback cego: manter serviço em manutenção e corrigir adiante. Se o artefato anterior não estiver retido, republicar seu SHA imutável pelo procedimento controlado, sem tentar aplicar migrações antigas sobre o histórico novo.

**Corrupção de dados:** suspender escritas e novos deploys; criar cópia do estado atual para preservar escritas posteriores e evidência; restaurar backup em **outro banco/branch**, verificar schema, histórico Flyway, contagens e dados de referência. Reconciliar as alterações posteriores ao backup antes de trocar o destino do runtime. Não sobrescrever o banco original nem prometer RPO zero a partir de um snapshot antigo. Retomar escritas somente após conferência pelo titular e revisão de Arthur/Leonardo.

Comandos previstos em shell privado, com ferramentas PostgreSQL 17 e variáveis carregadas pelo titular (sem URL na linha de comando):

```bash
# Origem: PGHOST, PGDATABASE, PGUSER, PGPASSWORD, PGSSLMODE e PGSSLROOTCERT.
umask 077
pg_dump --format=custom --no-owner --no-acl --file=backup.dump
# Trocar as variáveis para OUTRO banco vazio e confirmar o destino em canal privado.
pg_restore --exit-on-error --no-owner --no-acl --dbname="$PGDATABASE" backup.dump
# Verificar dados e reaplicar grants do runtime antes de qualquer troca de destino.
```

O titular guarda o backup em armazenamento privado, com acesso restrito e prazo de retenção registrado antes do deploy. `pg_dump` não guarda papéis/senhas globais: recriar papéis e grants no destino. Não contar com janela de restauração do plano gratuito sem conferir a disponibilidade efetiva. A restauração temporal Neon pode auxiliar, mas não substitui backup/ensaio nem autoriza perda de escritas. [Rollback Render](https://render.com/docs/rollbacks), [restauração Neon](https://neon.com/docs/postgres/backup-restore/branch-restore).

## Alternativas consideradas

| Alternativa | Motivo para não adotar agora |
|---|---|
| PostgreSQL no próprio container/disco da API | Filesystem efêmero perde dados em reinício/redeploy. |
| Banco gratuito Render | Neon separa o ciclo do banco do serviço e evita depender da expiração do banco gratuito Render; reavaliar planos antes de provisionar. |
| VM própria ou Kubernetes | Acrescenta operação, atualização e custos sem necessidade para uma réplica acadêmica. |
| Publicar Java e Go juntos ou exigir Go público | Torna a saúde/publicação dependente de outro serviço e amplia o escopo/custo. |
| Migrar no startup do Render ou em pre-deploy pago | Compete com o CI, acopla retorno do código a DDL e depende de recurso fora do plano escolhido. |
| Migrar automaticamente para baixo no rollback | Pode destruir dados ou deixar a versão em execução incompatível. |

## Consequências

A avaliação pública cobre a API e persistência; a avaliação térmica integrada acontece no Compose. Há operação manual inicial para contas, segredos, backup e conferência. Uma réplica e planos gratuitos impõem indisponibilidade, cotas e partidas a frio; o nome GitHub `producao` identifica o ambiente acadêmico, sem promessa de SLA. Pool, uso de memória da JVM e limites efetivos serão medidos na #88; ultrapassar cota não autoriza contratar recurso pago.

Migração compatível, SHA imutável e confirmação do deploy reduzem o risco de código e esquema divergirem. Recuperação de dados exige preservar e reconciliar escritas, e não pode ser confundida com rollback de container. Futuras alterações de telemetria, segurança e migração devem manter esse contrato e repetir a simulação.

## Validação e aceite

### Simulação local

O procedimento para conferir esta decisão usa somente banco e containers descartáveis, sem credenciais ou connection string de produção:

1. Criar PostgreSQL 17 isolado e aplicar as migrações do repositório com Flyway 12.0.0, usando a sequência de validação/migração desta ADR.
2. Construir `api/Dockerfile` e iniciar a API apontando para esse banco, com `QUARKUS_FLYWAY_MIGRATE_AT_START=false`, sem iniciar Go e com telemetria apontando para uma porta local sem serviço. Conferir `/api/status` e `/q/health/ready` retornando `UP`.
3. Criar uma câmara fictícia pela API, guardar seu ID/representação persistida e gerar backup com `pg_dump`.
4. Somente numa cópia temporária das migrações, acrescentar uma migração com `CREATE TABLE ensaio_parcial(id int); SELECT 1/0;`. Executar Flyway e exigir retorno diferente de zero, tabela parcial ausente, histórico anterior preservado e registro HTTP inalterado. O erro deve interromper o procedimento antes do deploy.
5. Na cópia temporária, substituir a migração não aplicada por uma aditiva válida. Aplicá-la, recriar o container da imagem anterior e conferir o mesmo registro e histórico novo. Não alterar migrações oficiais nem SQL já aplicado.
6. Restaurar o backup em outro banco vazio com `pg_restore --exit-on-error`, comparar o registro e o histórico do backup, preservando o banco de origem. Remover apenas os recursos descartáveis criados pelo ensaio.

**Evidência de 04/11/2026:** as cinco verificações operacionais passaram em Windows/Docker Desktop, com base `f43749b`: migração externa V1/V2, saúde/prontidão sem Go, falha transacional sem DDL parcial nem perda de registro, retorno da imagem anterior sobre esquema aditivo e restauração em outro banco com igualdade do registro/histórico. A execução usou um [script auxiliar preservado no histórico do PR](https://github.com/AndreMattheus/FrioVax/blob/6893583239ebc7589cde8c2cd0113032232f2dbb/docs/scripts/simular-implantacao.py). Os containers, volumes anônimos, rede e tag de imagem do ensaio foram removidos ao terminar.

`mise run ci` passou nessa base: 472 testes Java, zero falhas/erros/ignorados, além de formatação, `go vet`, testes e build Go. As 11 verificações Python e a integridade das evidências existentes também passaram. Esse resultado é da simulação local, sem acesso a Render/Neon; não comprova TLS remoto, quotas ou configuração das contas/painéis. A #88 deve conferir esses pontos no ambiente provisionado, incluindo SHA efetivamente publicado, preservação de dados no redeploy e status final do GitHub Deployment.

### Aceite e conclusão

Arthur (@arthurFeijo), titular e responsável pela plataforma, deve validar o procedimento, e @leonardonadson revisa a ADR. As contas, as configurações relatadas e o cadastro dos secrets/variável no GitHub estão registrados; as credenciais e chaves no Render e os demais preparativos de publicação ainda precisam de confirmação para a #88. Para encerrar a #87, o PR deve conter `Closes #87`, CI verde no commit final, aprovação do revisor, comentários resolvidos, integração em `main` e atualização do cartão conforme [Definição de Pronto](../processo.md#definição-de-pronto). O apoio material de IA está registrado no arquivo existente `docs/uso-de-ia.md`.

Na conferência do Project em **06/11/2026**, a #87 estava em **Em Progresso**, com **Start date = 04/11/2026** e Finished date vazio. O início real já está registrado; preencher o término somente após a conclusão real, sem copiar datas planejadas. O PR #104 permanecia em rascunho, sem aprovação e sem integração, com CI verde no commit publicado `4d66b49`; atualizações locais posteriores precisam ser enviadas e validadas pelo CI antes de integrar. Execução de ferramenta não substitui o aceite do responsável pela plataforma ou a aprovação do revisor.

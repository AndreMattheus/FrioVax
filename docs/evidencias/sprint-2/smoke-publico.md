# Smoke da API pública e degradação sem Go — #89

**Estado: smoke funcional aprovado no SHA abaixo, com execução autenticada
por Arthur e conferência dos arquivos pelo agente. Revisão do PR pendente.**
Responsável: Arthur (`arthurFeijo`). Revisor: Leonardo (`leonardonadson`).
Referência acadêmica: W-T10. Dependências #83 e #88 concluídas; este relatório
não substitui a prova de gRPC completo do [fluxo real da #83](validacao/fluxo-real-83.md).

## Ambiente, versão e fonte

- API: <https://friovax.onrender.com>.
- Swagger UI: <https://friovax.onrender.com/q/swagger-ui/>.
- OpenAPI JSON: <https://friovax.onrender.com/q/openapi?format=json>.
- SHA público observado: **`262c97d4282324774239ad5170a5276cc85aa9ea`**.
- Publicação: [run 45823840534](https://github.com/AndreMattheus/FrioVax/actions/runs/45823840534), [job 139922740845](https://github.com/AndreMattheus/FrioVax/actions/runs/45823840534/job/139922740845), concluído com sucesso em 26/11/2026 às 12:57:03 UTC (09:57:03 Fortaleza).
- Entrada preservada: [resultado JSON](smoke-publico/20261126T130409809684Z/resultado.json) e [SHA-256](smoke-publico/20261126T130409809684Z/resultado.sha256).
- Ensaio autenticado aprovado: [JSON de Arthur](smoke-publico/20261126T133357359660Z/resultado.json) e [hash](smoke-publico/20261126T133357359660Z/resultado.sha256), de **10:33:57 a 10:38:16 de Fortaleza**, em 26/11/2026.
- Redeploy do mesmo SHA: [run 45830378267](https://github.com/AndreMattheus/FrioVax/actions/runs/45830378267), [job 139945274762](https://github.com/AndreMattheus/FrioVax/actions/runs/45830378267/job/139945274762), deployment **8086685428**, status success em **13:37:08 UTC (10:37:08 Fortaleza)**. Antes: deployment **8085739634**.

A evidência registra o deployment explícito, SHA, status success, etapas de
migração/Render/smoke e comandos paginados do GitHub realmente executados.
A publicação mais recente coincide com o SHA de `main` consultado na execução.
Os passos `Migrar banco (sem saída sensível)`, `Publicar SHA e esperar Render`
e `Saúde e smoke autenticado` estão aprovados. O script de publicação faz
Flyway validate/migrate/validate; isso comprova a execução da migração daquele
SHA, sem exportar conexão, saída bruta ou consulta SQL de produção.
O histórico SQL não foi consultado independentemente por esta execução.

A URL HTTP não expõe um SHA próprio: sua associação à versão se apoia no
workflow que confere Render live no SHA solicitado e no smoke público daquele
job. O hash do OpenAPI observado e as rotas esperadas ficam no JSON. Não se
deduz versão apenas do campo estático de versão do OpenAPI.

## Verificações executadas pela internet

Em **26/11/2026, aproximadamente 10:04 de Fortaleza**, foi executado:

```powershell
python docs/scripts/smoke-publico.py --somente-publico
```

| Requisição | Resultado | Ida e volta medida |
|---|---|---:|
| `GET /api/status` | 200, UP | 0,388 s |
| `GET /q/health/live` | 200, UP | 0,302 s |
| `GET /q/health/ready` | 200, UP | 0,636 s |
| `GET /q/openapi?format=json` | 200; rotas de sessão, câmaras, leituras e ocorrências presentes | 0,469 s |
| `GET /q/swagger-ui/` | 200; página Swagger reconhecida | 1,090 s |
| `GET /api/camaras`, sem token | 401 | 0,286 s |
| `GET /api/ocorrencias`, sem token | 401 | 0,296 s |

São tempos de parede medidos por relógio monotônico no cliente, incluindo
rede/TLS/serviço. Não são tempo interno de processamento nem garantia de SLA.
O script tolera até cinco minutos para partida a frio; esta execução não
precisou de retentativas. Readiness do banco não depende do Go.

## Execução autenticada por Arthur

Arthur optou por digitar as credenciais no próprio terminal. Elas não estão
disponíveis nesta sessão do agente. **Não publicar senha ou tokens no chat,
issue, PR ou arquivo de evidência.** Não usar os exemplos locais de senha.

Pré-requisitos: Python 3.11+, GitHub CLI autenticado (`gh auth status`) com
permissão de disparar Actions e uma conta **ADMINISTRADOR de teste da API
pública**. O login do GitHub é separado do login da API. A conta AUDITOR do
smoke automático da #88 não tem permissão para este CRUD.

Na raiz do repositório, no PowerShell/Terminal interativo:

```powershell
python docs/scripts/smoke-publico.py --redeploy --limite-503 10
```

O script pergunta login e senha da API, com senha oculta. Não recebe senha
na linha de comando, não lê arquivos privados e não salva credenciais/tokens.
Se o terminal não suportar entrada oculta, interrompe antes de ler a senha.
O JSON da execução é escrito em uma **pasta nova** de
`docs/evidencias/sprint-2/smoke-publico/<instante-UTC>/`, com hash separado.
`--saida CAMINHO_NOVO` permite escolher outra pasta sem sobrescrever evidência.

Esse comando realiza operações reais sobre **uma fixture fictícia própria**
e dispara um **redeploy real de main** pelo workflow existente. Não publica
a branch de teste. Se main mudou ou a versão atual ainda não foi publicada,
o script interrompe para evitar misturar versões. O redeploy pode levar
alguns minutos; o limite de espera é 40 minutos e o script mostra progresso.

Sequência verificada:

1. Repetir saúde e documentação, conferir a publicação de main e autenticar.
2. Exigir perfil ADMINISTRADOR, consultar inventário e criar uma câmara com
   código aleatório `E89-...`, nome fictício e faixa 2–8 °C. Atualizar sua
   capacidade para 120, sem mudar política térmica.
3. Consultar a câmara e seus históricos de leituras/ocorrências pela API.
   A fixture nova deve ter ambos vazios; não examinar/exportar dados alheios.
4. Enviar uma leitura nova de 9 °C com chave própria, instante atual com fuso
   e origem `smoke-publico-89`. Exigir **503** com tipo
   `/problemas/analise-indisponivel`. O operador informa antes da execução
   o orçamento público de ida e volta em `--limite-503`; não há limite implícito
   herdado do ensaio local. O comando acima propõe **10 segundos** para este
   roteiro público, incluindo rede/TLS. Isso não é SLA nem prova do prazo
   interno da RPC de 800 ms. O limite de 2 segundos da #83 permanece no teste
   local, sem ser afrouxado. Exceder o orçamento público reprova a execução,
   mas o script agora consulta e preserva o estado após o 503 antes de parar.
5. Consultar novamente câmara, leituras e ocorrências. Exigir igualdade do
   estado e históricos ainda vazios: sem leitura aceita ou ocorrência fictícia.
6. Disparar `deploy.yml` em main e aguardar novo run aprovado do **mesmo SHA**.
   Conferir migração, Render, smoke e um novo deployment distinto do anterior.
7. Renovar a sessão com o refresh emitido **antes** do redeploy. Ele deve
   sobreviver no banco; o refresh consumido deve retornar 401. Consultar
   identidade e comparar a fixture e os históricos após o redeploy.
8. Inativar somente a fixture criada, consultar `ativo=false`, efetuar logout
   e exigir 401 ao reutilizar o refresh revogado. O JWT de acesso recém-emitido
   permanece válido até expirar, conforme o desenho da autenticação; consultar
   identidade depois do logout deve continuar retornando 200 nesse intervalo.

Refresh dura 24 horas e é persistido; access JWT dura 15 minutos. O script
renova depois da espera, sem confundir expiração normal de access durante um
redeploy demorado com perda da sessão persistida. Tokens só existem na memória.
O encerramento tenta revogar o refresh mesmo se uma etapa anterior falhar.
Se houver falha após criar a câmara, `fixture_pendente` informa somente seu
ID para conferir e inativar; não há limpeza genérica do inventário.

Sem `--redeploy`, os testes autenticados podem ser executados, mas o resultado
fica `parcial_sem_redeploy`, sem alegar persistência após reinício.
`--somente-publico` nunca autentica, cria dados ou dispara publicação.

## Resultado autenticado e aceite

Arthur executou o primeiro ensaio autenticado em **26/11, de 10:22:35 a
10:26:47 de Fortaleza**: [JSON preservado](smoke-publico/20261126T132235626835Z/resultado.json)
e [hash](smoke-publico/20261126T132235626835Z/resultado.sha256).
Login e identidade retornaram 200; inventário 200; cadastro 201; atualização
200; consultas anteriores de câmara, leituras e ocorrências 200.
A leitura sem Go retornou **503 em 2,746 s**, mas excedeu o limite de 2 s
indevidamente transportado do teste local para a medição pela internet.
Outras chamadas desse ensaio levaram 4,696 s (login) e 4,897 s (inventário),
portanto a medida externa não permite isolar nem concluir violação do prazo
interno da RPC. A execução original permanece **incompleta**, sem mudar
tempos ou reclassificá-la como aprovada.

A versão inicial abortou antes de consultar o estado após o 503; assim,
**não há comprovação de ausência de gravação após a falha neste ensaio**.
Também não houve redeploy. O refresh foi encerrado com 204 e a fixture de
ID **2** ficou pendente de inativação naquele momento. Na execução seguinte,
ela foi conferida e inativada com origem/hash/identidade validados pelo comando:

```powershell
python docs/scripts/smoke-publico.py --redeploy --limite-503 10 --limpar-fixture-de docs/evidencias/sprint-2/smoke-publico/20261126T132235626835Z
```

`--limpar-fixture-de` exige a evidência íntegra da mesma origem, a identificação
de uma fixture `E89-...` com o nome fictício esperado e históricos vazios;
caso contrário interrompe sem inativar. O script cria uma nova fixture para
o ensaio seguinte e grava o orçamento e a duração reais na nova evidência.
O sucesso do smoke antigo da #88 não foi usado como substituto do ensaio abaixo.

### Segunda execução autenticada: aprovada

Arthur informou o caminho da execução real; o agente conferiu o hash, os
resultados e os metadados de publicação preservados. O SHA antes/depois é
`262c97d4282324774239ad5170a5276cc85aa9ea`, com deployments distintos.
A câmara fictícia **3**, código **`E89-796920439D7A`**, foi criada, teve
capacidade alterada para **120** e foi consultada sem Go acessível para a análise.

| Verificação real | Resultado | Ida e volta |
|---|---|---:|
| Login / identidade | 200 / 200 | 1,305 / 0,293 s |
| Inventário autenticado | 200 | 1,124 s |
| Criar / atualizar câmara fictícia | 201 / 200 | 1,214 / 1,259 s |
| Consultar câmara / leituras / ocorrências antes da falha | 200 / 200 / 200 | 0,748 / 1,106 / 0,891 s |
| Enviar leitura nova de 9 °C sem Go | **503 controlado** | **1,097 s**, abaixo do orçamento público de 10 s |
| Consultar câmara / leituras / ocorrências após a falha | 200 / 200 / 200 | 1,758 / 0,886 / 0,630 s |
| Renovar refresh criado antes do redeploy | 200 | 1,358 s |
| Reutilizar refresh consumido | 401 | 0,754 s |
| Identidade após redeploy | 200 | 0,560 s |
| Consultar câmara / leituras / ocorrências após redeploy | 200 / 200 / 200 | 1,494 / 1,488 / 1,210 s |
| Inativar / consultar fixture inativa | 204 / 200 | 1,445 / 1,001 s |
| Logout / reutilizar refresh revogado | 204 / 401 | 0,750 / 0,520 s |
| Access JWT após logout, ainda não expirado | 200, conforme desenho | 0,288 s |

Os objetos preservados em `fixture.antes`, `fixture.depois_falha` e
`fixture.depois_redeploy` são **iguais**: zero leituras, zero ocorrências,
estado `OPERACIONAL`, estado térmico `SEM_LEITURAS`, ocupação zero e mesmos
dados/datas da câmara. A leitura recusada tem chave
`E89-796920439D7A-L1`. Não foi criada leitura aceita ou ocorrência fictícia.
A resposta 503 foi conferida como `/problemas/analise-indisponivel` pelo script;
nenhum corpo de erro arbitrário foi exportado.

As fixtures **2 e 3** foram inativadas logicamente, com confirmação `ativo=false`;
nenhum registro alheio foi removido. O refresh persistiu no banco durante o
reinício e continuou renovável; sua rotação e revogação foram verificadas,
sem gravar tokens. O script não confunde esse teste com a validade apenas
criptográfica do JWT. O histórico SQL continua sem consulta independente:
a migração é comprovada pelo workflow e a preservação pelo estado via API.

### Conferência pública após o redeploy

O agente repetiu `python docs/scripts/smoke-publico.py --somente-publico`
às **10:40 de Fortaleza**: [JSON](smoke-publico/20261126T134018149599Z/resultado.json)
e [hash](smoke-publico/20261126T134018149599Z/resultado.sha256).
Status, liveness, readiness, OpenAPI e Swagger retornaram 200; inventário e
histórico anônimos, 401. A referência é o deployment **8086685428**.
O OpenAPI antes/depois tem o mesmo SHA-256
`1163ed69f128e081057dbb1c5bea937fee1fae7b7d4ea628294f3504e63f3d7d`.
Isso complementa o vínculo entre documentação, SHA e publicação observado
nos metadados do job, sem alegar que a API expõe seu próprio hash de commit.

Todos os tempos são medidos pelo cliente; timestamps de criação do banco
e timestamps do cliente usam relógios distintos. Não se subtraíram relógios
distintos para inferir latência. As entradas originais não foram corrigidas.

`resultado=aprovado` só é emitido pelo script depois do fluxo completo com
redeploy. Falhas ficam como `incompleto`, sem transformar ausência de observação
em sucesso. Após a atualização final do ambiente, repetir o mesmo comando e
preservar a nova execução antes da submissão, conforme #89/#97.

O ambiente Render publica somente Java; Go não participa do workflow da #88.
O ensaio comprovou sua indisponibilidade **para a API** pelo 503 controlado e
pela ausência de efeitos. Não demonstra gRPC completo no provedor gratuito:
o fluxo com Go real pertence ao Compose/#83.

## Verificação do script e revisão

```powershell
python -m unittest discover -s docs/scripts -p test_smoke_publico.py -v
python -m unittest discover -s docs/scripts -p test_deploy.py -v
```

Resultado local: **14 testes do smoke e 19 da publicação passaram**. Cobrem
falha sem vazar corpos de autenticação, redirecionamentos, gravação fantasma,
503 fora do prazo, sessão após redeploy, limpeza restrita à fixture e a
distinção entre verificação pública e ensaio autenticado, preservação da
primeira falha temporal e limpeza mediante evidência da fixture. As simulações só
existem nos testes, não como evidências de chamadas públicas autenticadas.
O CI executa esses testes sem acessar produção nem usar suas credenciais.

A evidência funcional da #89 está executada para o SHA indicado. Revisão de
Leonardo, CI verde, PR com `Closes #89`, integração e datas reais do Project
continuam condições da DoD; este relatório não declara aprovação ou integração
antecipadas. A repetição após eventual atualização final pertence à conferência
da versão efetivamente entregue, preservando este ensaio como histórico.

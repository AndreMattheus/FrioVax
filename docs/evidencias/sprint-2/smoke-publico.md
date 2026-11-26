# Smoke público e degradação sem Go — #89

**Ensaio funcional aprovado em 26/11/2026.** Responsável: Arthur;
revisor: Leonardo. Referência acadêmica: W-T10.

## Ambiente e versão

- API: <https://friovax.onrender.com>.
- [Swagger UI](https://friovax.onrender.com/q/swagger-ui/) e [OpenAPI JSON](https://friovax.onrender.com/q/openapi?format=json), também referenciados no README.
- SHA testado: **`262c97d4282324774239ad5170a5276cc85aa9ea`**.
- Publicação anterior: deployment **8085739634**, [run 45823840534](https://github.com/AndreMattheus/FrioVax/actions/runs/45823840534).
- Redeploy do mesmo SHA: deployment **8086685428**, [run 45830378267](https://github.com/AndreMattheus/FrioVax/actions/runs/45830378267), success em **26/11 às 10:37:08 de Fortaleza** (13:37:08 UTC).

## Evidências preservadas

| Execução, em 26/11 (Fortaleza) | Resultado | Arquivos |
|---|---|---|
| 10:22:35–10:26:47 | Incompleta: 503 em 2,746 s excedeu o limite inicial de 2 s | [JSON](smoke-publico/20261126T132235626835Z/resultado.json), [hash](smoke-publico/20261126T132235626835Z/resultado.sha256) |
| 10:33:57–10:38:16 | Aprovada: CRUD, falha sem Go, estado persistido e sessão após redeploy | [JSON](smoke-publico/20261126T133357359660Z/resultado.json), [hash](smoke-publico/20261126T133357359660Z/resultado.sha256) |
| 10:40:18–10:40:25 | Saúde/documentação conferidas após redeploy; acesso anônimo bloqueado | [JSON](smoke-publico/20261126T134018149599Z/resultado.json), [hash](smoke-publico/20261126T134018149599Z/resultado.sha256) |

A primeira execução permanece incompleta: interrompeu antes de verificar o
estado após o 503, portanto não prova ausência de gravação. O limite local da
#83 foi indevidamente usado como limite de ida e volta pela internet.
O roteiro passou a exigir orçamento público explícito antes da execução;
o ensaio aprovado usou **10 s**, sem alterar o prazo interno da RPC nem o
limite local de 2 s. O resultado anterior não foi reclassificado.

Os JSONs registram chamadas, tempos, comandos e metadados selecionados dos
jobs. Na evidência aprovada, os 29 comandos são representados por 13 comandos
distintos e `sequencia_comandos_github` (índices em ordem, começando em zero).
Essa compactação não altera medições; `fonte_original` identifica o commit e
hash da representação anterior. O arquivo `.sha256` verifica a versão atual.

## Resultados do ensaio aprovado

Arthur executou a parte autenticada no próprio terminal. A câmara fictícia
**3**, código **`E89-796920439D7A`**, foi criada com faixa 2–8 °C e teve
capacidade alterada para 120.

| Verificação | HTTP observado | Ida e volta |
|---|---|---:|
| Login / identidade | 200 / 200 | 1,305 / 0,293 s |
| Inventário autenticado | 200 | 1,124 s |
| Criar / atualizar câmara | 201 / 200 | 1,214 / 1,259 s |
| Nova leitura de 9 °C sem Go | **503**, `/problemas/analise-indisponivel` | **1,097 s** |
| Câmara / leituras / ocorrências após a falha | 200 / 200 / 200 | 1,758 / 0,886 / 0,630 s |
| Renovar refresh emitido antes do redeploy | 200 | 1,358 s |
| Reutilizar refresh consumido | 401 | 0,754 s |
| Câmara / leituras / ocorrências após redeploy | 200 / 200 / 200 | 1,494 / 1,488 / 1,210 s |
| Inativar / consultar fixture inativa | 204 / 200 | 1,445 / 1,001 s |
| Logout / reutilizar refresh revogado | 204 / 401 | 0,750 / 0,520 s |
| JWT após logout, ainda não expirado | 200, conforme desenho | 0,288 s |

`fixture.antes`, `fixture.depois_falha` e `fixture.depois_redeploy` são iguais:
**zero leituras e ocorrências**, câmara `OPERACIONAL`, estado térmico
`SEM_LEITURAS`, ocupação zero e mesmos dados/datas. A leitura recusada usa
`E89-796920439D7A-L1`; não gerou leitura aceita ou ocorrência fictícia.
As fixtures **2** (tentativa anterior) e **3** foram inativadas logicamente,
sem remover registros alheios.

O refresh persistido no banco sobreviveu ao reinício e foi renovado; o refresh
consumido/revogado recebeu 401. O JWT continua válido até expirar, conforme
o desenho de access de 15 minutos e refresh de 24 horas.

Na conferência pública posterior, status, liveness, readiness, OpenAPI e
Swagger retornaram **200**; inventário e histórico anônimos, **401**.
O OpenAPI antes/depois manteve SHA-256
`1163ed69f128e081057dbb1c5bea937fee1fae7b7d4ea628294f3504e63f3d7d`.

## Reprodução e limites

Requer Python 3.11+, `gh` autenticado com permissão de executar Actions e
conta ADMINISTRADOR de teste da API pública. Na raiz, em terminal interativo:

```powershell
python docs/scripts/smoke-publico.py --redeploy --limite-503 10
```

O comando pede senha oculta, cria apenas uma fixture própria e dispara um
redeploy real de **main**, não da branch de teste. A capacidade 120 deve ser
confirmada pela consulta após o PUT. A URL deve coincidir com `API_BASE_URL`
do ambiente `producao` e com a URL do deployment; a conferência ocorre antes
do login, antes do disparo e após a publicação. Credenciais/tokens ficam
em memória; cada execução gera pasta nova com JSON e hash. O ensaio aprovado
também usou `--limpar-fixture-de docs/evidencias/sprint-2/smoke-publico/20261126T132235626835Z`
para conferir e inativar a fixture anterior, mediante hash, origem, identidade
e histórico vazio. Sem `--redeploy`, a persistência após reinício não é aprovada.
`--somente-publico` verifica saúde, documentação e bloqueio anônimo sem mutações.

Tempos usam relógio monotônico do cliente e incluem rede/TLS; não são SLA
nem duração interna da RPC de 800 ms. Não se subtraem timestamps de relógios
distintos para inferir latência. A URL não expõe SHA próprio: a associação
usa o job que confere Render live no SHA solicitado e o OpenAPI observado.
Migrações são comprovadas pelas etapas aprovadas de validate/migrate/validate;
o histórico SQL de produção não foi consultado diretamente.

Render publica somente Java. O 503 comprova indisponibilidade da análise para
a API; gRPC completo é demonstrado no [Compose/#83](validacao/fluxo-real-83.md).
Após eventual atualização final do ambiente, repetir o ensaio na versão
efetivamente entregue, conforme #89/#97.

## Verificação local

```powershell
python -m unittest discover -s docs/scripts -p test_smoke_publico.py -v
python -m unittest discover -s docs/scripts -p test_deploy.py -v
```

**18 testes do smoke e 19 da publicação passaram**, sem acessar produção
ou usar credenciais nos testes. Cobrem preservação das evidências, falha sem
efeitos, tempo excedido, sessão, limpeza restrita e ausência de segredos.
Revisão de Leonardo, CI verde, integração do PR com `Closes #89` e datas reais
no Project continuam requisitos da DoD, sem aprovação antecipada neste relato.

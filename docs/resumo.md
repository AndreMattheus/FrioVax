# Resumo operacional — S2-15 / #79

`GET /api/resumo` requer Bearer válido e a permissão `consultar-operacao`:
ADMINISTRADOR, OPERADOR e AUDITOR recebem os mesmos dados. Sem autenticação a
resposta é 401; perfil sem permissão recebe 403 antes de consultar ou tocar o cache.
O contrato é gerado em `/q/openapi` e pode ser consultado em `/q/swagger-ui/`.

Exemplo de resposta para uma câmara ativa e um lote disponível com 100 doses:

```json
{
  "totais": {
    "camarasPorEstadoOperacional": {"OPERACIONAL": 1, "MANUTENCAO": 0, "DESATIVADA": 0},
    "camarasPorEstadoTermico": {"SEM_LEITURAS": 1, "NORMAL": 0, "ATENCAO": 0, "EM_ALERTA": 0},
    "lotesPorEstado": {
      "DISPONIVEL": {"lotes": 1, "doses": 100},
      "ESGOTADO": {"lotes": 0, "doses": 0},
      "DESCARTADO": {"lotes": 0, "doses": 0}
    },
    "episodiosAbertos": 0,
    "lotesDistintosAfetados": 0
  },
  "geradoEm": "2026-11-15T12:00:00Z",
  "validade": "2026-11-15T12:00:30Z"
}
```

Os totais por estado incluem apenas câmaras e lotes **ativos**. Um lote
descartado ainda ativo continua contando suas doses, conforme a regra de ocupação;
inativar remove o lote destes totais. Estados sem registros aparecem com zero.
O estado operacional é manual e o térmico é o estado oficial persistido: a consulta
não reanalisa leituras nem chama Go e funciona quando esse serviço está desligado.

`episodiosAbertos` conta ocorrências com `resolvida_em IS NULL`.
`lotesDistintosAfetados` conta `DISTINCT lote_id` nas fotografias desses episódios,
incluindo lotes posteriormente movidos ou inativados. Um mesmo lote em dois episódios
abertos conta uma vez; um episódio sem lotes conta apenas como episódio.
As doses em `lotesPorEstado` são quantidades atuais do inventário, e não a soma
das fotografias históricas. Uma única instrução SQL agrega todos os totais no mesmo
snapshot do PostgreSQL, evitando multiplicação por junções com evidências.

Há **um cache local por processo**, compartilhado entre os três perfis. O TTL
padrão é 30 segundos, configurável por `RESUMO_CACHE_TTL` ou `friovax.resumo.ttl`
(por exemplo `10s`); valores não positivos impedem inicialização. `geradoEm`
marca o início da consulta ao banco; `validade` é esse instante mais TTL, não a
validade de vacinas nem a data da última leitura. Na fronteira de validade a
próxima consulta é miss. O relógio `Clock` é injetado e substituído nos testes;
não há espera real para testar expiração.

Uma seção sincronizada serializa carga e invalidação, evitando cargas simultâneas
e reposição de um resultado antigo depois de invalidá-lo. A consulta ao banco usa
transação própria, e a consulta ao cache suspende eventual transação do chamador:
dados pendentes de commit não entram no cache. Falhas não preenchem o cache.

Cadastro, atualização e inativação de câmara; cadastro, atualização, baixa,
descarte e inativação de lote publicam `MudancaConfirmada`. A persistência de
ocorrências também publica o evento por uma porta de domínio, na abertura e na
resolução, mesmo antes da entrega dos endpoints de telemetria. O publicador
entrega o evento somente em `afterCompletion(COMMITTED)`: rollback descarta o
aviso. O resumo observa recursos `camara`, `lote`, `leitura` e `ocorrencia`.
Futuras mutações de leituras/estado oficial devem publicar o mesmo evento ao
final da sua transação. Alterações SQL externas à aplicação só serão percebidas
na expiração do TTL.

Esta implementação exige **uma única réplica da API**. Não há Redis nem
invalidação entre réplicas; aumentar o número de instâncias exige substituir o
cache/publicação locais. Cache e contadores são reiniciados com o processo.

## Métricas e política de acesso

A extensão oficial [Micrometer Prometheus do Quarkus](https://quarkus.io/guides/telemetry-micrometer/)
expõe `/q/metrics` no mesmo servidor HTTP. A política HTTP `metrics` exige JWT com
perfil ADMINISTRADOR ou AUDITOR; OPERADOR recebe 403 e anônimo recebe 401.
O endpoint fornece também métricas operacionais padrão do Quarkus; não recebe
liberação pública e usa as mesmas regras de validade de access tokens da API.

- `friovax_resumo_cache_hits_total`: consultas que reutilizaram o resultado válido.
- `friovax_resumo_cache_misses_total`: tentativas que precisaram consultar o banco,
  inclusive tentativas que falharam.

Consultar métricas não carrega o resumo nem incrementa esses dois contadores.
Os contadores não têm labels de usuário, perfil ou recurso.

## Verificação reproduzível

Resultados locais e cenários: [evidência da #79](evidencias/sprint-2/validacao/resumo-79.md).

Com Java 25, Maven e Docker disponíveis:

```sh
mvn --batch-mode -f api/pom.xml "-Dtest=ConsultarResumoTest,ResumoPostgresTest,PublicadorDeMudancasCdiTest,ArquiteturaTest,AutorizacaoRotasTest" test
```

`ConsultarResumoTest` verifica TTL com clock controlado, hits/misses,
concorrência, falha de carga e invalidação. `ResumoPostgresTest` verifica totais
no PostgreSQL, o mesmo lote em dois episódios, doses versus lotes, cache entre
perfis, abertura de ocorrência, commit, rollback, baixa e métricas reais por HTTP.
O teste usa PostgreSQL temporário e não precisa do serviço Go.

Para demonstrar manualmente, exporte `TOKEN` com access token de ADMINISTRADOR
obtido pelo login local (não registre tokens nas evidências):

```sh
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/resumo
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/resumo
curl -H "Authorization: Bearer $TOKEN" -H "Accept: text/plain" http://localhost:8080/q/metrics
```

A primeira consulta após iniciar/invalidação deve aumentar misses; a segunda,
feita antes de 30 s, deve aumentar hits e preservar timestamps. Execute os
testes PostgreSQL para demonstrar abertura de ocorrência e rollback: a abertura
usa os repositórios existentes, pois os endpoints de ingestão/resolução ainda
não estão implementados nesta base. Não há uma rota administrativa de testes
publicada para forçar rollback. Quando esses endpoints forem integrados, repita
a demonstração abrindo um episódio pela ingestão e conferindo o novo resumo.

O PR deve conter `Closes #79`, solicitar revisão de @leonardonadson e anexar a
saída dos testes/CI e a demonstração. Aprovação, datas reais no Project e aceite
da equipe permanecem passos humanos; esta documentação não os declara concluídos.

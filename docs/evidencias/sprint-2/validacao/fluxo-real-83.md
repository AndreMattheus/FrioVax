# Validação do fluxo real Java–Go–banco — #83

Execução local em 25/11/2026, macOS e Docker Engine 29.6.2, com as ferramentas
de `mise.toml` (Temurin 25, Maven 3.9.16). Base: `995dec13b819f678507ea3de1ab482ce4d93fed5`
mais as alterações da #83.

Comando: `python docs/scripts/verificar-java.py`, o mesmo do job `Java / Quarkus`.
O script subiu um PostgreSQL exclusivo (`friovax-java-ci-34ed181a3d17`) e executou
`mvn verify`; a imagem Go foi construída pelo Dockerfile de `services/telemetry`.

## Resultados observados

| Ensaio | Resultado |
|---|---|
| Suíte Java completa | 1083 testes, 0 falhas, 0 erros, `BUILD SUCCESS` |
| `FluxoJavaGoPostgresTest` | 5 testes, 0 falhas, 10,48 s (sem contar build da imagem e boot do perfil) |
| Três inseguras + reenvio da terceira | 201, 201, 201 com ocorrência; reenvio 200 com o mesmo id; 3 RPCs no log do Go; 1 abertura auditada com o operador e a correlação da terceira |
| Lote com duas câmaras | 1 RPC; câmara com 9, 9, 9, 4 abriu na terceira e zerou na quarta, mantendo `EM_ALERTA`; outra câmara ficou em `ATENCAO`; reenvio todo 200, sem nova RPC nem auditoria |
| Go parado | Leitura 503 em 18 ms e lote 503 em 11 ms; nada gravado; inventário e histórico 200 |
| Go congelado | Leitura 503 em 816 ms e lote 503 em 821 ms (prazo de 800 ms); nada gravado; inventário e histórico 200 |
| Resolução | 409 antes da segura; depois, `RESOLVIDA` pelo administrador autenticado; nova sequência abriu outra ocorrência |

Os tempos vêm de `api/target/fluxo-real/relatorio.json` dessa execução. No CI,
o mesmo arquivo é publicado no artifact `java-test-reports`. O limite verificado
pelo teste é 2 s para cada resposta 503.

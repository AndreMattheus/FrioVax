# Exercício real do protocolo — 03/11/2026

Item: [#66](https://github.com/AndreMattheus/FrioVax/issues/66), em execução hoje. A coleta começou depois da entrada real em `Em Progresso`; os commits desta implementação conservam seus horários reais. O recorte planejado começou em outubro, mas não há reconstrução de horas ativas anteriores a este registro.

## Entrada e fonte

O [snapshot](../exportacoes/20261103T192538818629Z/manifesto.json) preserva os comandos realmente executados e os metadados originais selecionados. Em [issue-66-timeline.json](../exportacoes/20261103T192538818629Z/issue-66-timeline.json):

- índice `0`: chegada ao Project em `2026-10-24T02:56:32Z` (23/10 às 23h56min32s em Fortaleza);
- índice `3`: entrada em `Em Progresso` em `2026-11-03T19:19:22Z` (03/11 às 16h19min22s), por `leonardonadson`.

Esses dois marcos estão em [eventos.csv](../eventos.csv), com ponteiros JSON exatos. Start date da #66 foi preenchida como `2026-11-03` no Project. Finished date continua vazia até revisão, integração e DoD; não copiamos Target date vencida para término real.

## Intervalo real observado

A linha `S2-66-coleta-01` de [intervalos.csv](../intervalos.csv) tem tipo `automacao`, autor `coletor-github`, precisão `observado` e fonte no manifesto:

| Marco | UTC | Fortaleza |
|---|---|---|
| Início da execução | 03/11 19:24:39.352963Z | 03/11 16:24:39.352963−03:00 |
| Fim da execução | 03/11 19:25:38.818629Z | 03/11 16:25:38.818629−03:00 |

Conferência: `38.818629 − 39.352963 + 60 = 59.465666 segundos`, ou `59.465666 / 3600 = 0.01651824 horas`. Esse tempo mede a execução da coleta, incluindo latência de API; não comprova trabalho humano contínuo, espera por revisão, conclusão do cartão ou implantação. Não é resultado DORA nem baseline das três tarefas da API.

## Reexecução e limites

```bash
python3 docs/scripts/coletar-medicao-sprint-2.py --verificar-paginacao docs/evidencias/sprint-2/exportacoes/20261103T192538818629Z
python3 -m unittest discover -s docs/scripts -p 'test_medicao_sprint_2.py' -v
python3 docs/scripts/validar-medicao-sprint-2.py
git diff --check
mise run ci
```

- Coleta: **134 consultas paginadas** (REST e GraphQL). Cada consulta tem horário, número de páginas, contagem e SHA-256 no manifesto.
- Conferência independente com páginas de um PR: **30 páginas, 30 IDs iguais** aos do snapshot. Comando, instantes e conjuntos de IDs estão em [paginacao-20261103T192538818629Z.json](paginacao-20261103T192538818629Z.json).
- O validador confere seis registros CSV iniciais (três seleções, dois eventos e uma execução), fontes locais/ponteiros JSON, timestamps, integridade e contagens das exportações; também procura padrões de tokens e cabeçalhos de autenticação. A exportação usa projeção de campos, sem corpos, e-mails, patches, payloads ou logs.
- Os nove testes cobrem ausência de fuso, ordem temporal, fontes com credenciais/query string, ponteiros locais, agregação de páginas, exclusão de PRs do endpoint de issues, erro de API, GraphQL truncado, alteração de hash e detecção de padrão de token. Dados sintéticos desses testes só existem em diretórios temporários, fora das evidências reais.
- `mise run ci` passou: 472 testes Java sem falhas/erros/ignorados; formatação, vet, testes e build Go concluídos.
- A API retornou zero deployments de `producao` e zero issues com label `incidente` neste corte. Os CSVs correspondentes e o de monitoramento continuam sem ocorrências. Não há cobertura suficiente para afirmar taxa de falha zero ou tempo de recuperação zero.

Arthur deve reexecutar a validação e conferir as fontes durante a revisão. A jornada até publicação da amostra #69/#70/#80 permanece para seus autores e para as tarefas #88/#91; lacunas anteriores ao protocolo continuam explícitas.

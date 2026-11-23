# Evidências da Sprint 2

Seguir o [plano de medição](../../medicao-sprint-2.md). Começar por `intervalos.csv`; deixar o fim vazio enquanto a atividade acontece. Uma fonte pode ser uma URL pública do GitHub ou um arquivo versionado com ponteiro JSON (`arquivo.json#/data/0`). Nunca usar credenciais, URL assinada de provedor ou logs com segredos como fonte.

## Exemplo de preenchimento (somente modelo)

```csv
id,issue,etapa,tipo,inicio,fim,autor,fonte,precisao,observacao
SEU-ID,NUMERO,codificacao,trabalho,INICIO_ISO_COM_FUSO,,LOGIN,URL_DO_RELATO,relatado,Descrever a atividade
```

Substituir todos os campos do modelo; não copiá-lo como observação real. Ao terminar, informar o horário real em `fim`; ao pausar, fechar a linha e criar outra quando retomar. Relato pessoal pode ser um comentário na própria issue com início/fim e atividade; o PR do CSV preserva sua autoria. O tempo de uma ferramenta não comprova trabalho humano contínuo.

## Arquivos e manutenção

A apuração preliminar da #90 está em [metricas-02.md](../../metricas-02.md).
O snapshot `exportacoes/20261123T220209564272Z` inclui passos dos jobs,
ancestralidade por SHA publicado, vínculos de fechamento de PRs e statuses
dos commits. `metricas-02/` conserva os cálculos dos cortes de 20 e 23/11,
CSVs locais congelados com hashes e metadados complementares de ambientes.
`resultado.json` inclui a auditoria dos 40 registros, 16 publicações reais
(incluindo um redeploy), PRs/commits incorporados e exclusões. O corte de
20/11 foi reconstruído em 23/11, não medido no prazo. Reprodução por Bianca
e atualizações de 26/11 e da entrega continuam pendentes.

- `eventos.csv`: marcos com fonte e timestamp; registro não substitui o dado original exportado.
- `amostra.csv`: seleção #69/#70/#80; o estado da seleção não significa conclusão ou publicação.
- `implantacoes.csv`: publicações com deployment, SHA, smoke e PRs incorporados. A #91 registrou as duas usadas pela amostra; as demais e as falhas ficam para a #90.
- `incidentes.csv` e `monitoramento.csv`: começam só com cabeçalho. Ausência de linha não é ocorrência fictícia nem medição de zero incidentes.
- `exportacoes/`: um diretório novo por execução; `manifesto.json` descreve consultas, tempo de extração, páginas e hashes. Arquivos JSON guardam metadados originais selecionados, sem texto livre ou credenciais. Não regravar um corte existente.
- `validacao/`: resultados da conferência paginada e do primeiro exercício do protocolo.
- `validacao/fluxo-91-jobs.json`: metadados selecionados e passos dos dois jobs de publicação usados pela #91, com comandos e instante da consulta. Complementa os cortes sem regravá-los; preserva o sucesso e os horários de Render e smoke.

Comandos, definições, fórmulas, prazos e tratamento de lacunas estão no plano. Atualizar os CSVs pelo PR da tarefa correspondente; executar `python3 docs/scripts/validar-medicao-sprint-2.py` antes de publicar. Usar instantes com fuso inclusive para esperas e bloqueios. Campos de data do Project não guardam hora.

## Reprodução no Windows

O coletor lê a saída do GitHub CLI explicitamente em UTF-8 e grava JSON em bytes UTF-8 com quebras LF. O SHA-256 usa os mesmos bytes gravados. O `.gitattributes` mantém LF nas evidências mesmo com `core.autocrlf=true`, preservando os hashes dos cortes existentes durante o checkout.

No Windows, executar `python -m unittest discover -s docs/scripts -p 'test_medicao_sprint_2.py' -v` e `python docs/scripts/validar-medicao-sprint-2.py`. Para uma nova coleta, usar `python docs/scripts/coletar-medicao-sprint-2.py`. O CI executa os testes e valida o corte preservado em Linux e Windows, com conversão automática de quebras de linha habilitada no Windows. Os testes de UTF-8 usam subprocessos reais e dados temporários, sem consultar a API.

Em um clone anterior a essa correção, se houver divergência causada por CRLF e nenhuma alteração local nas evidências, executar `git restore --source=HEAD --worktree -- docs/evidencias/sprint-2` após atualizar a branch para reaplicar os atributos e validar novamente. Não recalcular os hashes nem regravar cortes históricos para compensar a conversão.

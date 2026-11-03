# Evidências da Sprint 2

Seguir o [plano de medição](../../medicao-sprint-2.md). Começar por `intervalos.csv`; deixar o fim vazio enquanto a atividade acontece. Uma fonte pode ser uma URL pública do GitHub ou um arquivo versionado com ponteiro JSON (`arquivo.json#/data/0`). Nunca usar credenciais, URL assinada de provedor ou logs com segredos como fonte.

## Exemplo de preenchimento (somente modelo)

```csv
id,issue,etapa,tipo,inicio,fim,autor,fonte,precisao,observacao
SEU-ID,NUMERO,codificacao,trabalho,INICIO_ISO_COM_FUSO,,LOGIN,URL_DO_RELATO,relatado,Descrever a atividade
```

Substituir todos os campos do modelo; não copiá-lo como observação real. Ao terminar, informar o horário real em `fim`; ao pausar, fechar a linha e criar outra quando retomar. Relato pessoal pode ser um comentário na própria issue com início/fim e atividade; o PR do CSV preserva sua autoria. O tempo de uma ferramenta não comprova trabalho humano contínuo.

## Arquivos e manutenção

- `eventos.csv`: marcos com fonte e timestamp; registro não substitui o dado original exportado.
- `amostra.csv`: seleção #69/#70/#80; o estado da seleção não significa conclusão ou publicação.
- `implantacoes.csv`, `incidentes.csv` e `monitoramento.csv`: começam só com cabeçalho. Ausência de linha não é ocorrência fictícia nem medição de zero incidentes.
- `exportacoes/`: um diretório novo por execução; `manifesto.json` descreve consultas, tempo de extração, páginas e hashes. Arquivos JSON guardam metadados originais selecionados, sem texto livre ou credenciais. Não regravar um corte existente.
- `validacao/`: resultados da conferência paginada e do primeiro exercício do protocolo.

Comandos, definições, fórmulas, prazos e tratamento de lacunas estão no plano. Atualizar os CSVs pelo PR da tarefa correspondente; executar `python3 docs/scripts/validar-medicao-sprint-2.py` antes de publicar. Usar instantes com fuso inclusive para esperas e bloqueios. Campos de data do Project não guardam hora.

# Validação externa do README — #94

Sessão coordenada e registrada por Bianca em 28/11/2026, às 14h57 (horário de Fortaleza),
com uma pessoa de fora da equipe executando o projeto apenas com o
[README](../../../../README.md), sem orientação verbal.

Commit usado: `1b97637`, a `main` no horário do clone (último merge às 09h09 de
28/11, PR #134; nenhum outro merge até o fim da sessão).

## Participante

| Item | Registro |
|---|---|
| Identificação | AR, estudante do BIA |
| Fora da equipe | Sim; não participou do desenvolvimento nem das revisões do FrioVax |
| Já tinha executado o FrioVax | Não |
| Consentimento | Autorizou o registro com iniciais, horários e mensagens de erro, sem dados pessoais |

## Ambiente de partida

Conferido **antes** do início da contagem.

| Item | Registro |
|---|---|
| Sistema operacional | Linux Fedora |
| Docker | 29.10.1 |
| Compose | 5.8.0 |
| Terminal padrão | ZSH; o roteiro foi executado no Bash (ver Obstáculos) |
| Volumes do FrioVax antes da sessão | Nenhum (`docker volume ls --filter name=friovax` vazio) |
| Imagens do FrioVax antes da sessão | Nenhuma (`docker image ls --filter reference='friovax-*'` vazio) |
| Internet | Wi-Fi doméstico |

## Critérios de início e fim

- **Início:** execução do `git clone` indicado no README.
- **Fim:** conclusão do bloco 13 da "Demonstração completa" do README, com
  `telemetry` novamente `healthy`.
- **Limite:** menos de 10 minutos entre início e fim.
- **Aprovação:** a pessoa autentica, percorre o fluxo e identifica os resultados
  esperados usando apenas o README.
## Linha do tempo

Os horários foram extraídos, ao fim da sessão, dos registros que o próprio ambiente
gravou: datas de criação da pasta do clone, dos volumes e dos containers no Docker, e
os instantes de sessões, leituras e ocorrências no PostgreSQL, convertidos para o
horário de Fortaleza. A anotação manual feita durante a sessão teve horários
arredondados e foi substituída por esses registros.

| Marco | Horário | Tempo desde o início |
|---|---|---|
| Início do `git clone` | 15:01:51 | 0:00 |
| `docker compose up --build` iniciado | 15:02:33 | 0:42 |
| Build concluído; volumes e containers criados | 15:07:16 | 5:25 |
| Bloco 2: login do administrador com 200 | 15:07:40 | 5:49 |
| Bloco 5: terceira leitura abre a ocorrência | 15:08:28 | 6:37 |
| Bloco 8: resolução com 200 e câmara `NORMAL` | 15:09:22 | 7:31 |
| Bloco 13: Go novamente `healthy` (fim) | 15:11:30 | **9:39** |

O build das imagens ocupou 4:43 dos 9:39 (de 0:42 a 5:25).

## Resultados observados

Durante a execução, AR comparou cada saída do terminal com o resultado
**Esperado** descrito em cada bloco do README. A coluna **Observado** registra essa
conferência.

| Bloco do README | Esperado | Observado |
|---|---|---|
| 1. Auxiliar | Nenhuma chamada | OK |
| 2. Login | 200 | OK |
| 3. Câmara | 201 | OK |
| 4. Lote | 201, ocupação 20 de 100 | OK |
| 5. Três inseguras | 201; sequências 1, 2 e 3; ocorrência aberta na terceira | OK |
| 6. Reenvio | 200 com o mesmo ID; câmara `EM_ALERTA`; ocorrência `ABERTA` com o lote | OK |
| 7. Segura | 201; câmara ainda `EM_ALERTA` | OK |
| 8. Resolução | 200; ocorrência `RESOLVIDA`; câmara `NORMAL` | OK |
| 9. Histórico e resumo | 200; quatro leituras | OK |
| 10. Sem token | 401 | OK |
| 11. Auditor | Login 200; envio 403 | OK |
| 12. Sem Go | 503 `/problemas/analise-indisponivel`; quatro leituras; consultas 200 | OK |
| 13. Go de volta | `telemetry` `healthy` | OK |

## Volume de dados novo

Conferido depois do fim, fora da contagem:

```text
friovax_db-data 2026-11-28T15:07:16-03:00
friovax_auth-data 2026-11-28T15:07:16-03:00
```

Os dois volumes foram criados durante a sessão, logo após o build, e não existiam
antes dela. Portanto, o banco e as credenciais não vieram da validação da Sprint 1.

## Obstáculos

| Horário | O que aconteceu | Mensagem ou status | Como a pessoa seguiu |
|---|---|---|---|
| ~15:07 | Os blocos do roteiro Bash não funcionaram no ZSH, terminal padrão da máquina | Não registrada | Abriu o `bash` e repetiu o bloco, seguindo a indicação "Bash 4.2+" do README |

O README pede Bash 4.2+, mas não avisa que quem usa ZSH precisa abrir o `bash`
antes de colar os blocos. Sugestão para o README: "No zsh ou fish, rode `bash` antes
de colar os blocos."

## Correções e repetições

Nenhuma correção foi necessária para concluir o fluxo; a primeira execução foi aprovada.
O aviso sobre o ZSH fica como melhoria sugerida.

## Resultado

**Aprovada** em **9:39**, com 1 obstáculo e nenhuma correção.

## Limites

- Uma única pessoa e um único ambiente (Fedora, Docker 29.10.1). O resultado não mede
  todas as combinações de sistema operacional, rede e versão de Docker.
- O tempo inclui o primeiro build e o download das imagens, que dependem da rede. A
  folga em relação ao limite foi de 21 segundos, e o build respondeu por quase metade
  do tempo.
- Os pré-requisitos (Git e Docker com Compose em execução) foram instalados antes
  da sessão e não entram na contagem.

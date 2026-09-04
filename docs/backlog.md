# Backlog inicial

Os itens estão escritos como resultados para o usuário, priorizados e estimados na escala 1, 2, 3, 5, 8 e 13. P1 é essencial ao MVP, P2 é importante e P3 é desejável.

| ID | Prio | Pontos | Sprint | História de usuário |
|---|---|---:|---|---|
| US01 | P1 | 5 | 1 | Como administrador, quero cadastrar e manter câmaras para controlar a infraestrutura de conservação |
| US02 | P1 | 8 | 1 | Como administrador, quero cadastrar e alocar lotes respeitando a capacidade da câmara para evitar armazenamento incompatível |
| US03 | P1 | 5 | 1 | Como operador, quero consultar câmaras e lotes com paginação e filtros para localizar rapidamente o inventário |
| US04 | P1 | 8 | 2 | Como operador, quero enviar leituras de temperatura para que o sistema monitore as condições de conservação |
| US05 | P1 | 8 | 2 | Como gestor, quero que sequências térmicas inseguras gerem ocorrências para investigar os lotes afetados |
| US06 | P1 | 5 | 2 | Como operador, quero consultar o histórico de leituras e ocorrências de uma câmara para rastrear desvios |
| US07 | P2 | 5 | 3 | Como gestor, quero um resumo rápido do estado das câmaras e lotes para priorizar minha atenção |
| US08 | P2 | 3 | 3 | Como administrador, quero configurar faixas e limiares térmicos por câmara para refletir diferentes produtos |
| US09 | P1 | 8 | Final | Como administrador, quero controlar acesso por perfil para proteger operações críticas |
| US10 | P2 | 5 | Final | Como auditor, quero identificar quem alterou câmaras, lotes e ocorrências para responsabilização e conformidade |

## Critérios de aceitação

### US01 — Gerenciar câmaras

- [ ] Permite criar, consultar, alterar e inativar uma câmara.
- [ ] Exige código único, unidade, capacidade positiva, temperatura mínima menor que a máxima e estado válido.
- [ ] Responde com semântica HTTP coerente e erros no formato Problem Details.

### US02 — Gerenciar e alocar lotes

- [ ] Permite criar, consultar, alterar e inativar um lote associado a uma câmara.
- [ ] Exige identificador único, imunobiológico, fabricante, validade futura e quantidade positiva.
- [ ] Rejeita alocação cuja soma ultrapasse a capacidade da câmara, sem persistência parcial.

### US03 — Consultar inventário

- [ ] Lista câmaras e lotes de forma paginada.
- [ ] Filtra câmaras por unidade e estado e lotes por imunobiológico, validade e câmara.
- [ ] Mantém ordenação determinística e informa os metadados da página.

### US04 — Processar leituras

- [ ] A API recebe uma leitura válida com câmara, temperatura e instante.
- [ ] A API chama o microsserviço Go via gRPC com deadline.
- [ ] O serviço Go processa requisições concorrentes sem corrida de dados e devolve resultado explícito.
- [ ] Falha ou timeout do Go produz erro controlado e observável, sem decisão oficial incorreta.

### US05 — Detectar excursão térmica

- [ ] Leituras fora da faixa incrementam a sequência da câmara; leitura segura a reinicia.
- [ ] Ao atingir o limiar, Go sinaliza anomalia e Java registra a ocorrência uma única vez.
- [ ] Java altera o estado oficial da câmara e identifica os lotes potencialmente afetados de forma transacional.

### US06 — Consultar histórico

- [ ] Exibe leituras paginadas por período e câmara.
- [ ] Exibe ocorrência, evidências, estado e responsável pelo tratamento.
- [ ] Datas são retornadas com fuso/offset explícito.

### US07 — Consultar resumo gerencial

- [ ] Resume câmaras por estado, lotes afetados e ocorrências abertas.
- [ ] Cache possui TTL e política de invalidação documentados.
- [ ] Métricas reais de hit e miss ficam disponíveis para demonstração.

### US08 — Configurar política térmica

- [ ] Permite alterar faixa segura e quantidade de leituras consecutivas.
- [ ] Rejeita limites incoerentes e mantém histórico da mudança.
- [ ] A política atualizada é aplicada a novas leituras e invalida o cache correspondente.

### US09 — Controlar acesso

- [ ] Usuário autentica com token de expiração definida e renovação/revogação tratadas.
- [ ] Administrador altera cadastros e política; operador registra e consulta dados operacionais.
- [ ] Teste automatizado prova que usuário sem perfil não executa operação administrativa.

### US10 — Auditar alterações

- [ ] Registra usuário, instante, recurso, operação e identificador de correlação.
- [ ] O histórico não pode ser alterado pelas rotas comuns.
- [ ] A consulta permite filtrar por período, usuário e recurso.

## Configuração do GitHub Projects

Quadro publicado: [FrioVax Project — Board Principal](https://github.com/users/AndreMattheus/projects/1). As dez histórias estão disponíveis nas [issues do repositório](https://github.com/AndreMattheus/FrioVax/issues).

Colunas: **Backlog · Sprint Backlog · Em progresso · Em revisão · Pronto**.

Campos: Prioridade (P1/P2/P3), Pontos (1/2/3/5/8/13), Sprint, Responsável e ID da história.

WIP: Backlog sem limite; Sprint Backlog 8; Em progresso 4; Em revisão 2; Pronto sem limite.

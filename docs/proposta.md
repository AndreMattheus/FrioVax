# Proposta do projeto — FrioVax

## 1. Visão do produto

> Para profissionais e gestores de salas de vacinação e centrais da rede de frio  
> Que precisam controlar lotes e reagir a condições térmicas que podem comprometer imunobiológicos  
> O **FrioVax** é uma plataforma backend de gestão e monitoramento da cadeia de frio  
> Que centraliza inventário, telemetria e ocorrências térmicas rastreáveis  
> Diferente de planilhas e conferências manuais isoladas  
> Nosso produto processa leituras concorrentemente e mantém a decisão oficial sobre câmaras e lotes em um núcleo transacional.

### Problema e evidência

A cadeia de frio existe para preservar a qualidade dos imunobiológicos durante armazenamento, transporte e manuseio. O Ministério da Saúde mantém uma Rede de Frio nos níveis nacional, estadual e municipal e publica um manual específico de boas práticas. A 6ª edição do manual foi publicada em 2025. Como referência operacional inicial, vacinas refrigeradas podem exigir conservação entre +2 °C e +8 °C; o limite real deverá ser configurável conforme o produto.

Fontes: [Rede de Frio — Ministério da Saúde](https://www.gov.br/saude/pt-br/vacinacao/rede-de-frio) · [Manual da Rede de Frio do PNI, 6ª edição](https://bvsms.saude.gov.br/bvs/publicacoes/manual_rede_frio_pni_6ed.pdf) · [exemplo oficial da faixa de +2 °C a +8 °C](https://www.gov.br/saude/pt-br/assuntos/saude-de-a-a-z/v/vsr/faq/aspectos-tecnicos-e-operacionais-vsr/qual-a-temperatura-ideal-de)

## 2. MVP

| No MVP | Fora do MVP |
|---|---|
| CRUD paginado e filtrável de câmaras e lotes alocados | Aplicativo móvel ou frontend completo |
| Validação da capacidade da câmara ao alocar um lote | Integração com hardware ou protocolo proprietário de sensor |
| Recepção de leituras simuladas e processamento concorrente em Go | Agendamento de vacinação, reserva com TTL e fila de espera |
| Detecção de sequência de leituras fora da faixa configurada | FEFO automático e logística de distribuição entre unidades |
| Estado oficial da câmara e histórico persistidos pelo serviço Java | Diagnóstico clínico ou decisão automática de descarte |
| Consulta de inventário, leituras e ocorrências; dashboard com cache | IA para previsão de falhas e notificações por SMS/e-mail |
| Autenticação e autorização de administrador e operador | Integração com sistemas oficiais do SUS |

**Hipótese de valor:** acreditamos que profissionais responsáveis pela rede de frio vão registrar lotes e acompanhar ocorrências no FrioVax porque uma visão centralizada e rastreável reduz o tempo para identificar material potencialmente comprometido.

**MVP pronto quando:** (1) `docker compose up` sobe API, telemetria e banco sem passos manuais; (2) é possível cadastrar uma câmara e um lote, respeitando sua capacidade; (3) uma sequência simulada de leituras inseguras é analisada em Go e faz a API Java registrar uma ocorrência e alterar o estado oficial da câmara; (4) as consultas exibem inventário e histórico; (5) operações administrativas exigem autorização; e (6) build, testes e verificações de arquitetura permanecem verdes no CI.

## 3. Backlog inicial

O backlog priorizado, estimado e com critérios de aceitação está em [backlog.md](backlog.md). Quando o GitHub Project for criado, seus itens serão publicados no quadro e o link público será registrado aqui e no README.

## 4. Entidades principais

| Entidade | Responsabilidade | Relações principais |
|---|---|---|
| Unidade | Identifica a instalação responsável | Possui várias câmaras |
| Câmara | Capacidade, faixa térmica e estado oficial | Pertence à unidade; contém lotes; recebe leituras |
| Lote | Imunobiológico, fabricante, validade e quantidade | Está alocado em uma câmara |
| Leitura | Temperatura, instante e origem da medição | Pertence a uma câmara |
| Ocorrência térmica | Intervalo, evidências e tratamento da anomalia | Agrupa leituras e afeta uma câmara |
| Usuário | Identidade e perfil de acesso | Executa operações auditáveis |

As entidades e regras oficiais ficam no serviço principal. O estado transitório usado para reconhecer sequências de leitura pertence ao microsserviço Go.

## 5. Decisão tecnológica: Java 25 com Quarkus

Escolhemos **Java 25 com Quarkus**. A equipe quer consolidar Java e aproveitar seu ecossistema maduro para transações, persistência relacional, Flyway, validação, OpenAPI, segurança e Testcontainers. Quarkus oferece inicialização rápida e baixo consumo em containers, além de integração direta com essas ferramentas. O domínio exige consistência ao alterar capacidade, lotes e o estado oficial de uma câmara, cenário bem atendido pelo modelo transacional da JVM.

Kotlin/Ktor foi considerado por sua concisão e corrotinas, mas não foi escolhido porque acrescentaria simultaneamente uma nova linguagem de domínio enquanto a disciplina já exige Go e gRPC. A decisão reduz risco de aprendizagem sem eliminar o componente poliglota.

Stack: Java 25, Quarkus LTS, Maven, Go, gRPC, Protocol Buffers/Buf, PostgreSQL/Neon, Flyway, Testcontainers, Docker Compose, mise e GitHub Actions.

## 6. Divisão entre Java e Go

| Serviço principal Java/Quarkus | Microsserviço Go de telemetria |
|---|---|
| Mantém entidades e regras oficiais | Recebe lotes de leituras para análise |
| Persiste câmaras, lotes, leituras e ocorrências | Processa I/O concorrentemente com goroutines |
| Valida capacidade e consistência transacional | Mantém contadores e janelas transitórias por câmara |
| Decide a transição do estado da câmara | Detecta padrões e devolve um sinal de anomalia |
| Aplica autenticação, autorização e auditoria | Não altera lotes nem toma decisões de domínio |
| Expõe a API REST e orquestra casos de uso | Expõe contrato gRPC interno com deadline/contexto |

A API chama o serviço Go via gRPC. Go devolve fatos derivados das leituras; Java decide e persiste seus efeitos. O serviço Go não é API Gateway e não autentica usuários finais.

## 7. Acordo de processo

A equipe usa Scrum para cadência e planejamento, Kanban para controlar fluxo e práticas XP para qualidade técnica. O acordo completo está em [processo.md](processo.md).

- **Cadência:** segue os blocos acadêmicos; planejamento no início e revisão/retrospectiva antes de cada entrega.
- **Quadro:** Backlog · Sprint Backlog · Em progresso · Em revisão · Pronto.
- **WIP:** Sprint Backlog 8; Em progresso 4; Em revisão 2; Backlog e Pronto sem limite.
- **Definição de Pronto:** aceite atendido, testes e documentação atualizados, `mise run ci` verde, PR ligado à issue e aprovado por outro integrante.
- **Colaboração:** branches curtas, PR obrigatório, revisão cruzada e decisões arquiteturais registradas como ADR.

## 8. Equipe

| Integrante | Matrícula | Papel de coordenação | Revisão principal |
|---|---:|---|---|
| Arthur Feijo de Medeiros | 20240012792 | Qualidade e DevOps | revisa Leonardo |
| Bianca Maciel Medeiros | 20230044970 | Product Owner e domínio | revisa Arthur |
| Leonardo Nadson Oliveira de Medeiros | 20240028925 | Scrum Master e processo | revisa Matheus |
| Matheus Andre Souza Cirilo | 20240030941 | Arquitetura e Go | revisa Bianca |

Todos são desenvolvedores e devem criar commits, abrir PR, revisar código e apresentar suas contribuições.

## 9. Coorte e integração

- **Equipe/produto/repositório:** FrioVax / `friovax`.
- **Coorte B:** apresentações online durante todo o semestre.
- **Integração declarada:** mesmo produto e monorepo para DIM0547 e DIM0510.
- **Contribuição distinguível:** artefatos técnicos são avaliados em Web II; processo, métricas e retrospectivas são avaliados em Processos de Software.
- **Vídeos da Sprint 0:** dois vídeos de aproximadamente cinco minutos, com roteiros específicos por disciplina e participação dos quatro integrantes.

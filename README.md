# FrioVax

Plataforma para gestão de câmaras de conservação, lotes de imunobiológicos e monitoramento de condições térmicas.

## Equipe

**Nome do grupo:** FrioVax  
**Coorte:** B — apresentações online

| Integrante | Matrícula | GitHub | Papel principal |
|---|---:|---|---|
| Arthur Feijo de Medeiros             | 20240012792 | [`@arthurFeijo`](https://github.com/arthurFeijo)          | Qualidade e DevOps       |
| Bianca Maciel Medeiros | 20230044970 | [`@biancammedeiros`](https://github.com/biancammedeiros) | Product Owner e domínio |
| Leonardo Nadson Oliveira de Medeiros | 20240028925 | [`@leonardonadson`](https://github.com/leonardonadson) | Scrum Master e processo |
| Matheus Andre Souza Cirilo | 20240030941 | [`@AndreMattheus`](https://github.com/AndreMattheus) | Arquitetura e serviço Go |

Todos atuam como desenvolvedores. Os papéis indicam responsabilidades de coordenação e não propriedade exclusiva de código.

## Integração entre disciplinas

Este repositório contém o produto comum de:

- **DIM0547 — Desenvolvimento de Sistemas Web II:** backend Java/Quarkus, serviço Go e infraestrutura;
- **DIM0510 — Processos de Software:** acordo de trabalho, Kanban, métricas, retrospectivas e melhoria do processo.

## Entregas da Sprint 0

- [Proposta integrada](docs/proposta.md)
- [Backlog inicial](docs/backlog.md)
- [Acordo de processo](docs/processo.md)
- [Checklist da Sprint 0](docs/checklist-sprint-0.md)
- [Roteiro do vídeo de Web II](docs/roteiro-video-web2.md)
- [Roteiro do vídeo de Processos](docs/roteiro-video-processos.md)
- [Registro de uso de IA](docs/uso-de-ia.md)
- **GitHub Project:** [FrioVax Project — Board Principal](https://github.com/users/AndreMattheus/projects/1)
- **Issues do backlog:** [histórias US01 a US10](https://github.com/AndreMattheus/FrioVax/issues)
- **Vídeos:** adicionar os links não listados do YouTube após as gravações

## Estrutura exigida em Web II

```text
.
├── api/                    # Esqueleto Java 25/Quarkus
├── services/telemetry/     # Esqueleto do serviço Go
├── protos/                 # Reservado aos contratos da Sprint 2
├── docs/                   # Documentos da proposta e do processo
├── .github/workflows/      # CI dos dois stacks
├── docker-compose.yml
└── mise.toml
```

Na Sprint 0 há somente código mínimo para comprovar build e teste dos dois stacks. CRUD e persistência pertencem à Sprint 1; Protobuf, gRPC e a integração funcional com Go pertencem à Sprint 2.

## Como rodar

Pré-requisitos: [mise](https://mise.jdx.dev/) e Docker Desktop.

```bash
mise trust
mise install
mise run build
mise run test
```

Para subir os esqueletos dos serviços:

```bash
mise run up
curl http://localhost:8080/api/status
curl http://localhost:8081/health
```

| Comando | Resultado |
|---|---|
| `mise run build` | Compila Java e Go |
| `mise run test` | Executa os testes dos dois stacks |
| `mise run lint` | Verifica Maven, `gofmt` e `go vet` |
| `mise run ci` | Reproduz o pipeline localmente |
| `mise run up` | Sobe os serviços com Docker Compose |

## Licença

[MIT](LICENSE).

# Roteiro do vídeo — DIM0547 Web II

Duração-alvo: 5 minutos. Mostrar o repositório, terminal e CI; não usar somente slides.

| Tempo | Pessoa | Fala e demonstração |
|---|---|---|
| 0:00–0:30 | Leonardo | Apresentar equipe FrioVax, quatro integrantes, Coorte B e integração com Processos de Software. Mostrar o README. |
| 0:30–2:00 | Bianca | Explicar profissionais-alvo, risco da cadeia de frio, proposta de valor e evidência do Ministério da Saúde. Mostrar visão e entidades na proposta. |
| 2:00–3:00 | Arthur | Justificar Java 25/Quarkus por transações, ecossistema e containers; mencionar Kotlin/Ktor como alternativa descartada para reduzir risco de aprendizagem. Mostrar `api/pom.xml`. |
| 3:00–4:00 | Matheus | Explicar que Go é microsserviço, não gateway: processa telemetria concorrente e detecta padrões; Java decide e persiste. Mostrar diagrama, teste concorrente e os dois jobs verdes do CI. |
| 4:00–4:30 | Bianca | Delimitar MVP: câmaras, lotes, leituras e ocorrências. Destacar que agendamento, sensores reais e frontend estão fora. |
| 4:30–5:00 | Leonardo | Mostrar backlog priorizado/estimado e GitHub Project. Fechar com riscos: integração gRPC, idempotência e ambiente poliglota. |

## Demonstração antes da gravação

```bash
mise run ci
mise run up
curl http://localhost:8080/api/status
curl http://localhost:8081/health
```

O vídeo deve mostrar o CI verde em `main` e ser publicado como não listado ou público no YouTube.


# Roteiro sugerido — vídeo da Sprint 1 de Web II

**Duração-alvo:** 5 minutos. Distribuição de falas sugerida a partir dos PRs da Sprint 1; cada integrante confirma sua parte antes da gravação. Mostrar a aplicação e os artefatos funcionando, não somente slides.

| Tempo | Pessoa sugerida | Fala e tela |
|---|---|---|
| 0:00–0:30 | Leonardo | Apresentar a Sprint 1 e as duas entidades: câmara e lote. Mostrar a relação 1:N no [contrato](contrato-api.md). |
| 0:30–2:00 | Matheus | Pela [Swagger UI](http://localhost:8080/q/swagger-ui), criar câmara e lote, consultar, editar, filtrar, paginar e inativar. Mostrar `camaraId` no lote. |
| 2:00–3:00 | Arthur | Mostrar [Compose](../docker-compose.yml), PostgreSQL e migrações Flyway. Reiniciar sem remover o volume e consultar o registro persistido. |
| 3:00–4:00 | Leonardo | Mostrar `dominio`, `aplicacao`, `api` e `persistencia`, as interfaces de repositório e o domínio sem dependência de framework. |
| 4:00–5:00 | Bianca | Mostrar uma alocação acima da capacidade e uma entrada inválida, com Problem Details e sem gravação parcial. Abrir OpenAPI, mostrar o [CI verde](https://github.com/AndreMattheus/FrioVax/actions) e explicar o que fica para a Sprint 2. |

## Preparação da demonstração

1. Subir o ambiente com `mise run up`, a partir do [README](../README.md#como-rodar). Deixar Swagger UI, Project, migrações e CI abertos antes de gravar.
2. Usar os JSONs de [criação de câmara e lote](contrato-api.md#6-exemplos) com códigos novos. Anotar os IDs retornados para as consultas e edições. Manter um lote válido visível antes de tentar a alocação que excede a capacidade.
3. Para provar persistência, reiniciar com `docker compose down` e `mise run up`, sem `-v`, e consultar novamente a câmara e o lote. Não apagar o volume antes da comparação.
4. Ensaiar a sequência com cronômetro e confirmar que todos conseguem explicar a própria contribuição. Ao publicar, adicionar o link do vídeo ao README.

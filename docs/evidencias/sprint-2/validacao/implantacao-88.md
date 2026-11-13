# Publicacao e persistencia — #88 — 13/11/2026

API publica: https://friovax.onrender.com

SHA nas duas publicacoes: `c51e2981773fed0a40fb85264c1bc5201b63269b`.
Preparacao revisada e integrada no [PR #109](https://github.com/AndreMattheus/FrioVax/pull/109).

## Fontes publicas

| Evidencia | Fonte | Resultado |
|---|---|---|
| Primeira publicacao aprovada | [Run 43476705265](https://github.com/AndreMattheus/FrioVax/actions/runs/43476705265) | success; Render `dep-dbrks18n74is7395b9v0` |
| Redeploy do mesmo SHA | [Run 43484767266](https://github.com/AndreMattheus/FrioVax/actions/runs/43484767266) | success; Render `dep-dbrlj8u7bikc73at7eo0` |
| Status do deployment do job | [Statuses 7773456323](https://api.github.com/repos/AndreMattheus/FrioVax/deployments/7773456323/statuses) | success em 18:42:18Z, depois inactive |
| Status do environment GitHub | [Statuses 7773453502](https://api.github.com/repos/AndreMattheus/FrioVax/deployments/7773453502/statuses) | success em 18:42:21Z |

Os horarios sao UTC de 13/11/2026. O redeploy iniciou em 18:39:35Z e terminou
em 18:42:21Z. Os logs confirmam Render live no SHA esperado em 18:42:07Z e
smoke aprovado em 18:42:17Z: processo, banco, bloqueio anonimo e consulta autenticada.

O workflow cria um deployment explicito e o GitHub cria outro pelo environment.
Ao concluir, o segundo tornou o primeiro inactive. Isso nao apaga seu status
success anterior. Para DORA, contar a publicacao logica por run/SHA/ID Render,
sem contar esses dois registros como duas entregas.

## Dado de referencia e comparacao

Arthur relatou cadastro HTTP 201 e consulta HTTP 200 antes do redeploy:

```json
{
  "id": 1,
  "codigo": "EVID-88-01",
  "nome": "Camara ficticia de validacao",
  "unidade": "Laboratorio academico",
  "capacidade": 100,
  "temperaturaMinima": 2,
  "temperaturaMaxima": 8,
  "estado": "OPERACIONAL",
  "estadoTermico": "SEM_LEITURAS",
  "ocupacao": 0,
  "ativo": true,
  "criadoEm": "2026-11-13T18:26:49.852559Z",
  "atualizadoEm": "2026-11-13T18:26:49.852559Z"
}
```

Apos o redeploy, Arthur confirmou GET `/api/camaras/1` HTTP 200, com todos os
dados e datas iguais. Essa verificacao autenticada foi realizada pelo titular;
nao houve leitura independente do registro nesta coleta de metadados.

## Recuperacao com dados

Backup privado: `production-20261113T182914205Z.dump`.
SHA256: `0788C3BAAD2A0A3AD49FA1AA5EAE11F38520E6696C8A0406ED86E28491C4931E`.
Restauracao isolada: `restore_88_20261113183103416`, branch de teste.
Arthur confirmou restauracao sem erro, consulta da camara de referencia e
historico V1–V4 com sucesso. Confirmou tambem copia privada adicional. Nenhum
dump, senha, token ou chave privada e incluido neste relatorio.

## Falhas anteriores e limites

- [Run 43473041460](https://github.com/AndreMattheus/FrioVax/actions/runs/43473041460): SHA da declaracao de backup incorreto; interrompeu antes de migrar.
- [Run 43473388886](https://github.com/AndreMattheus/FrioVax/actions/runs/43473388886): migracao/grants passaram; build Render falhou por Root Directory `api/`. Arthur removeu a raiz, manteve Dockerfile `api/Dockerfile` e contexto `.`.
- Ensaio de SQL invalido na branch descartavel foi relatado anteriormente por Arthur: V999 revertida e V1–V3 preservadas. Nao foi injetada migracao invalida em production.
- Grants do CI verificaram ausencia de privilegios administrativos e de escrita no historico Flyway para `friovax_runtime`. Arthur confirmou em 13/11/2026 que `DB_USER=friovax_runtime` esta configurado no Render. Essa confirmacao do titular, junto do deploy e smoke aprovados, compoe a evidencia do runtime limitado; nao houve leitura independente da configuracao secreta do provedor.
- Retencao proposta: sete dias e pelo menos os tres ultimos backups aprovados, acesso privado pelo titular; confirmar adesao operacional no encerramento.
- Falta integrar este relatorio e a atualizacao local do script de restauracao em PR revisado, conferir datas reais/estado do Project e obter aceite final de Leonardo antes de fechar #88. Nao houve fechamento automatico da issue.

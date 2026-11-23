# Contrato da API — Câmaras, Lotes e Histórico

Este documento registra campos, regras, rotas, filtros e respostas usados na implementação de US01, US02 e US03 (Sprint 1). A organização interna do serviço está no [ADR 0002](decisoes/0002-dominio-independente-e-aplicacao-por-interfaces.md), que substitui a proposta do [ADR 0001](decisoes/0001-arquitetura-em-camadas.md).

A especificação OpenAPI gerada a partir do código fica em `/q/openapi`, e o Swagger UI em `/q/swagger-ui` (endereços no [README](../README.md#documentação-openapi-e-swagger-ui)). Este documento continua sendo a referência das decisões; a especificação descreve as rotas já implementadas.

Itens marcados com **⚠️ Pendente** dependem de validação externa e não devem ser tratados como aceitos.

## 1. Decisões

| # | Assunto | Decisão                                                                                                                                                                                                                                                                                                 |
|---|---|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| D1 | Entidades | `Camara` e `Lote`. Uma câmara contém vários lotes; cada lote pertence a exatamente uma câmara.                                                                                                                                                                                                          |
| D2 | Unidade da câmara | Representada pelo campo texto obrigatório `unidade` na câmara. A entidade Unidade prevista na [proposta](proposta.md#4-entidades-principais) fica **adiada**; não haverá um terceiro CRUD nesta sprint.                                                                                                 |
| D3 | Identificadores | `id` é o identificador técnico (inteiro gerado pelo banco) usado nas rotas. `codigo` da câmara e `codigo` do lote são identificadores de negócio únicos, normalizados (ver [§2.3](#23-normalização)).                                                                                                   |
| D4 | Unidade de contagem | `capacidade` da câmara e `quantidade` do lote são contadas em **doses**, sempre inteiros positivos.                                                                                                                                                                                                     |
| D5 | Estado da câmara | `estado` (operacional) é independente de `ativo` (cadastro) e de `estadoTermico` (situação térmica). Valores manuais: `OPERACIONAL`, `MANUTENCAO` e `DESATIVADA`. A situação térmica é atribuída só pelo sistema (ver [§3.1](#31-estado-da-câmara) e [§3.5](#35-política-térmica)). |
| D6 | Ocupação | `ocupacao` = soma de `quantidade` dos lotes **ativos** da câmara. Inativar um lote libera capacidade.                                                                                                                                                                                                   |
| D7 | Alteração de câmara | Rejeitada com `409` se reduzir `capacidade` abaixo da `ocupacao`. Inativação de câmara com lotes ativos também retorna `409`.                                                                                                                                                                           |
| D8 | Alteração de lote | Editáveis: `imunobiologico`, `fabricante`, `validade`, `quantidade` e `camaraId` (troca de câmara permitida). A quantidade pode aumentar, com revalidação da capacidade, mas não diminuir por PUT: toda redução passa pela baixa com motivo (ver §3.4). `codigo` é imutável. |
| D9 | Validade | Data de referência: dia corrente no fuso `America/Fortaleza`. Validade futura significa `validade > hoje`. A regra vale na criação e quando `validade` é alterada; um lote que venceu depois do cadastro continua editável se a `validade` enviada for igual à armazenada.                              |
| D10 | Filtro de validade | Intervalo **inclusivo** `validadeDe`/`validadeAte`. Com apenas um limite, o intervalo fica aberto do outro lado. `validadeDe > validadeAte` retorna `400`.                                                                                                                                              |
| D11 | Inativos | Ficam fora das listagens por padrão. `ativo=false` lista só inativos; `ativo=true` (padrão) lista só ativos. `GET /{id}` retorna o registro mesmo inativo. Códigos de registros inativos continuam reservados.                                                                                          |
| D12 | Remoção × inativação | `DELETE` faz **remoção lógica** (`ativo=false`), preserva o registro e responde `204`. Repetir `DELETE` num registro já inativo também responde `204`. **⚠️ Pendente:** confirmar com o professor se a remoção lógica atende à rubrica.                                                                 |
| D13 | Paginação | `page` começa em `0` (padrão `0`); `size` padrão `20`, mínimo `1`, máximo `100`. Ordenação fixa por `id ASC`. Valores fora do intervalo retornam `400`.                                                                                                                                                 |
| D14 | Idioma | Rotas, campos, filtros e mensagens em português, sem acentos nos identificadores (`camaraId`, `imunobiologico`).                                                                                                                                                                                        |
| D15 | Erros | Formato Problem Details ([RFC 9457](https://www.rfc-editor.org/rfc/rfc9457)) com `Content-Type: application/problem+json`.                                                                                                                                                                              |

## 2. Recursos

Datas usam ISO 8601: `validade` é data sem hora (`2027-03-31`); instantes de auditoria têm offset explícito (`2026-09-23T14:05:00-03:00`).

### 2.1 Câmara

| Campo | Tipo | Entrada | Regras                                                                               |
|---|---|---|--------------------------------------------------------------------------------------|
| `id` | integer (int64) | — | Somente leitura.                                                                     |
| `codigo` | string | Obrigatório na criação; imutável | 3 a 20 caracteres; `A-Z`, `0-9` e `-` após normalização; único entre todas as câmaras, inclusive inativas. |
| `nome` | string | Obrigatório | 1 a 100 caracteres após `trim`.                                                      |
| `unidade` | string | Obrigatório | 1 a 100 caracteres após `trim`.                                                      |
| `capacidade` | integer (int32) | Obrigatório | `> 0`, em doses; não pode ficar abaixo de `ocupacao`.                                |
| `temperaturaMinima` | number (decimal, 1 casa) | Obrigatório | Em °C; de `-999.9` a `999.9`; `temperaturaMinima < temperaturaMaxima`. Pertence à política térmica vigente (ver [§3.5](#35-política-térmica)). |
| `temperaturaMaxima` | number (decimal, 1 casa) | Obrigatório | Em °C; de `-999.9` a `999.9`. Pertence à política térmica vigente. |
| `estado` | string (enum) | Obrigatório | `OPERACIONAL`,`MANUTENCAO` ou `DESATIVADA`.                                          |
| `estadoTermico` | string (enum) | — | Somente leitura; atribuído só pelo sistema. `SEM_LEITURAS` (inicial), `NORMAL`, `ATENCAO` ou `EM_ALERTA` (ADR 0003). Independente de `estado`. |
| `ocupacao` | integer (int32) | — | Somente leitura; soma dos lotes ativos.                                              |
| `ativo` | boolean | — | Somente leitura; alterado apenas por `DELETE`.                                       |
| `criadoEm` | string (date-time) | — | Somente leitura.                                                                     |
| `atualizadoEm` | string (date-time) | — | Somente leitura.                                                                     |

### 2.2 Lote

| Campo | Tipo | Entrada | Regras |
|---|---|---|---|
| `id` | integer (int64) | — | Somente leitura. |
| `codigo` | string | Obrigatório na criação; imutável | 1 a 40 caracteres; `A-Z`, `0-9`, `-` e `/` após normalização; único entre todos os lotes, inclusive inativos. |
| `imunobiologico` | string | Obrigatório | 1 a 100 caracteres após `trim`. |
| `fabricante` | string | Obrigatório | 1 a 100 caracteres após `trim`. |
| `validade` | string (date) | Obrigatório | `> hoje` em `America/Fortaleza` (ver D9). |
| `quantidade` | integer (int32) | Obrigatório | `> 0` na criação, em doses. No PUT, pode aumentar ou permanecer igual; zero só para um lote já `ESGOTADO`. |
| `camaraId` | integer (int64) | Obrigatório | Câmara existente, ativa e `OPERACIONAL` (ver [§3.2](#32-alocação-de-lotes)). |
| `ativo` | boolean | — | Somente leitura; alterado apenas por `DELETE`. |
| `estado` | string (enum) | — | Somente leitura. `DISPONIVEL` por padrão; `ESGOTADO` quando `quantidade` chega a `0` (automático); `DESCARTADO` via ação dedicada (ver §3.4). |
| `criadoEm` | string (date-time) | — | Somente leitura. |
| `atualizadoEm` | string (date-time) | — | Somente leitura. |

### 2.3 Normalização

- Todos os textos recebem `trim`; texto vazio após `trim` é tratado como ausente.
- `codigo` (câmara e lote) é convertido para maiúsculas antes de validar e comparar. `cam-01` e `CAM-01` são o mesmo código.
- Campos desconhecidos no JSON são ignorados; campos somente leitura enviados no body são ignorados.

### 2.4 Tipos e validação de entrada

- `capacidade` deve ser um inteiro representável em `int32`. Um número enviado com notação decimal no JSON, como `10.5` ou `10.0`, retorna `400`. Um inteiro válido, mas menor ou igual a zero, retorna `422`.
- As temperaturas aceitam decimais e desconsideram zeros à direita ao validar a precisão: `2`, `2.0` e `2.00` são equivalentes; `2.05` retorna `422`, sem arredondamento.
- `estado` deve usar um dos nomes previstos no enum. Nome desconhecido, como `"INVALIDO"`, ou enum enviado como número, como `0`, retorna `400`. Campo ausente ou `null` retorna `422`.
- Body ausente ou `null`, campos obrigatórios ausentes ou em branco e violações das regras de entrada retornam `422`. Quando falta o body inteiro, a lista `erros` identifica o campo `body`. JSON com sintaxe inválida retorna `400`.
- O tamanho de `nome` e `unidade` é verificado depois do `trim`, conforme §2.1. Espaços nas extremidades não contam para o limite.
- Falhas de validação e duplicidade não podem criar registros. As rotas devem reutilizar os mapeadores de erros existentes. O domínio continua responsável por suas regras.

## 3. Regras de negócio

### 3.1 Estado da câmara

| Estado | Significado | Aceita novos lotes? |
|---|---|---|
| `OPERACIONAL` | Câmara em uso normal | Sim |
| `MANUTENCAO` | Retirada de uso temporária por decisão manual | Não |
| `DESATIVADA` | Retirada de uso definitiva por decisão manual | Não |

- O cadastro aceita qualquer um desses três estados e cria a câmara com `ativo=true` e `ocupacao=0`.
- Transições manuais permitidas via PUT: `OPERACIONAL` ↔ `MANUTENCAO` ↔ `DESATIVADA`.
- Colocar em `MANUTENCAO` ou `DESATIVADA` uma câmara com lotes ativos alocados retorna 409 — os lotes precisam ser movidos para outra câmara antes da transição, pelo mesmo motivo já aplicado à inativação (D7).
- A situação térmica (inclusive `EM_ALERTA`) não usa este campo: fica em `estadoTermico`, atribuído só pelo sistema (ver [§3.5](#35-política-térmica)).
- Uma câmara inativa não pode ser alterada (`PUT` → `409`).

### 3.2 Alocação de lotes

- Um lote só pode ser criado ou movido para uma câmara **ativa** e **`OPERACIONAL`**; caso contrário, `409`.
- `camaraId` inexistente → `404`.
- Condição de capacidade: `ocupacao_atual_da_câmara_destino - quantidade_anterior_se_mesma_câmara + quantidade_nova <= capacidade`. Vale tanto para aumento da quantidade quanto para troca de câmara. Violação → `409`.
- O PUT não reduz a quantidade (`422`); um lote `ESGOTADO` pode manter quantidade zero ao editar outros campos, mas não receber novas doses (`409`).
- Na troca de câmara, a quantidade sai da origem e entra no destino; só o destino precisa ser revalidado.
- A verificação e a gravação ocorrem na mesma transação, com bloqueio da linha da câmara de destino (`SELECT ... FOR UPDATE`), para que alocações concorrentes não ultrapassem a capacidade e não haja persistência parcial.

### 3.3 Inativação

- `DELETE /api/camaras/{id}` com lotes ativos → `409`. Os lotes precisam ser inativados ou movidos antes.
- A inativação da câmara bloqueia a mesma linha usada na alocação (§3.2): um `DELETE` concorrente com a criação ou movimentação de um lote espera a outra transação terminar e, se o lote foi gravado, responde `409`.
- `DELETE /api/lotes/{id}` libera a quantidade na ocupação da câmara.
- Reativação não faz parte desta sprint.
- Registros inativos não podem ser alterados (`PUT` → `409`).

### 3.4 Estado do lote
| Estado | Significado |
|---|---|
| `DISPONIVEL` | Lote com `quantidade &gt; 0` |
| `ESGOTADO` | Transição automática quando `quantidade` chega a `0` após uso |
| `DESCARTADO` | Transição manual, via `POST /api/lotes/{id}/descarte`, para lotes retirados de circulação antes de esgotar (ex.: violação de temperatura, dano) |

Um lote `DESCARTADO` não aceita mais uso (`POST /api/lotes/{id}/baixas` → `409`).

### 3.5 Política térmica

A faixa térmica da câmara pertence a uma política versionada, com faixa e limiar N de leituras inseguras consecutivas ([ADR 0003](decisoes/0003-servico-go-telemetria.md), seção 2). A política é a única fonte oficial da faixa.

- A câmara nova, ou já existente antes da migração V4, começa na versão 1, com a faixa informada e limiar `3` (valor de demonstração), e com `estadoTermico` = `SEM_LEITURAS`. Nenhuma leitura é inventada.
- No `PUT /api/camaras/{id}`, uma faixa diferente da vigente cria nova versão, preservando o limiar. A mesma faixa, inclusive com outra escala (`8` e `8.00`), não cria versão. A regra é a mesma da rota de política (§12), inclusive o autor. Mudar a faixa exige `versaoEsperada` (§12.1).
- Versões anteriores nunca são alteradas nem apagadas. Uma nova versão reinicia a sequência: sem ocorrência aberta, `estadoTermico` volta a `SEM_LEITURAS`; em `EM_ALERTA`, continua em alerta.
- O limiar vai de `1` a `100` e só muda pela rota de política (§12), que também consulta o histórico de versões.
- Uma edição confirmada da câmara gera um aviso interno de mudança, entregue somente depois do commit, para a auditoria (US10) e para invalidar o resumo (US07).

## 4. Rotas

| Operação | Câmara | Lote | Sucesso |
|---|---|---|---|
| Criar | `POST /api/camaras` | `POST /api/lotes` | `201` + `Location` + corpo |
| Consultar por ID | `GET /api/camaras/{id}` | `GET /api/lotes/{id}` | `200` |
| Atualizar campos editáveis | `PUT /api/camaras/{id}` | `PUT /api/lotes/{id}` | `200` com representação atualizada |
| Inativar | `DELETE /api/camaras/{id}` | `DELETE /api/lotes/{id}` | `204`, sem corpo |
| Listar | `GET /api/camaras` | `GET /api/lotes` | `200` com página |

`PUT` substitui todos os campos editáveis: todos os campos obrigatórios da tabela, exceto `codigo`, devem ser enviados.

### 4.1 Filtros

| Recurso | Parâmetro | Tipo | Comparação                                                    |
|---|---|---|---------------------------------------------------------------|
| Câmaras | `unidade` | string | Igualdade, sem diferenciar maiúsculas/minúsculas              |
| Câmaras | `estado` | enum | Igualdade (`OPERACIONAL`, `MANUTENCAO`,  `DESATIVADA`)                     |
| Lotes | `imunobiologico` | string | Busca parcial (contém), sem diferenciar maiúsculas/minúsculas |
| Lotes | `validadeDe` | date | `validade >= validadeDe`                                      |
| Lotes | `validadeAte` | date | `validade <= validadeAte`                                     |
| Lotes | `camaraId` | int64 | Igualdade                                                     |
| Lotes | `estado` | enum | Igualdade (`DISPONIVEL`, `ESGOTADO`, `DESCARTADO`) |
| Ambos | `ativo` | boolean | Igualdade; padrão `true`                                      |
| Ambos | `page`, `size` | integer | Ver D13                                                       |

Filtros combinados usam **E** lógico. `camaraId` inexistente num filtro não é erro: a página volta vazia.


### 4.2 Baixa e descarte de lote

**Baixa parcial** — `POST /api/lotes/{id}/baixas`, body:

{
"quantidade": <integer>,
"motivo": "ADMINISTRADA" | "PERDA"
}

- `quantidade` deve ser `> 0` e `<= quantidade` atual do lote, senão `422`.
- `motivo` é obrigatório: `ADMINISTRADA` (aplicada em alguém) ou `PERDA` (quebra, contaminação, descarte de uma ou mais unidades sem aplicação).
- Reduz `quantidade` do lote pelo valor informado.
- Se o resultado for `0`, o servidor muda `estado` do lote para `ESGOTADO` na mesma transação.
- Lote `DESCARTADO` ou inativo (`ativo = false`) → `409`.
- Sucesso: `200` com o lote atualizado.

**Descarte total** — `POST /api/lotes/{id}/descarte`, sem body.

- Muda `estado` do lote para `DESCARTADO`, independente de quanto ainda resta em `quantidade`.
- Idempotente: chamar de novo num lote já `DESCARTADO` retorna `200` sem alterar nada.
- Lote inativo (`ativo = false`) → `409`.
- Sucesso: `200` com o lote atualizado.

### 4.3 Página

`totalElements` e `totalPages` contam apenas registros que atendem aos filtros. Uma página além da última retorna `200` com `items` vazio.

| Campo | Tipo |
|---|---|
| `items` | array do recurso |
| `page` | integer |
| `size` | integer |
| `totalElements` | integer (int64) |
| `totalPages` | integer |

## 5. Erros

| Status | Quando                                                                                                                                                                                                                                                                 | `type` |
|---|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---|
| `400` | JSON malformado; falha de desserialização do body; parâmetro de consulta com tipo inválido; `page`/`size` fora do intervalo; `validadeDe > validadeAte`                                                                                                               | `/problemas/requisicao-invalida` |
| `404` | Recurso da rota inexistente; `camaraId` do body inexistente                                                                                                                                                                                                           | `/problemas/recurso-nao-encontrado` |
| `409` | Código duplicado                                                                                                                                                                                                                                                       | `/problemas/codigo-duplicado` |
| `409` | Capacidade excedida ou redução abaixo da ocupação                                                                                                                                                                                                                      | `/problemas/capacidade-excedida` |
| `409` | Operação incompatível com o estado atual (registro inativo, câmara em manutenção, câmara com lotes ativos)                                                                                                                                                             | `/problemas/estado-incompativel` |
| `409` | `versaoEsperada` diferente da versão vigente da política térmica; consulte e reenvie (§12) | `/problemas/versao-desatualizada` |
| `422` | Body ou campo obrigatório ausente; campo em branco; formato ou regra de entrada violada após desserialização (inclusive validade não futura, temperatura fora do intervalo, precisão térmica excessiva e `temperaturaMinima >= temperaturaMaxima`) | `/problemas/validacao` |
| `409` | Leitura com chave `(camaraId, idExterno)` já aceita com outro conteúdo (§10) | `/problemas/leitura-conflitante` |
| `409` | Câmara alterada durante as duas análises de uma leitura; pode reenviar (§10) | `/problemas/alteracao-concorrente` |
| `502` | Serviço de análise térmica respondeu fora do contrato ou recusou a janela; nada é gravado (§10) | `/problemas/falha-de-integracao` |
| `503` | Serviço de análise térmica ausente ou sem resposta no prazo; nada é gravado (§10) | `/problemas/analise-indisponivel` |
| `500` | Falha interna inesperada, com mensagem genérica e detalhes técnicos apenas no log do servidor                                                                                                                                                                          | `/problemas/erro-interno` |

Todo Problem Details contém `type`, `title`, `status`, `detail` e `instance`. O campo `instance` identifica o caminho da requisição com `/` inicial, por exemplo `/api/camaras`. Erros `422` incluem `erros`, uma lista de `{ "campo": string, "mensagem": string }`.

Problem Details padroniza a resposta HTTP. As regras de negócio e suas exceções permanecem no domínio, conforme o [ADR 0002](decisoes/0002-dominio-independente-e-aplicacao-por-interfaces.md).

## 6. Exemplos

Obtenha `accessToken` via `POST /api/auth/login` e envie Bearer em todas as requisições de câmaras/lotes. Os exemplos de mutação usam administrador; consultas aceitam os três perfis e baixa aceita também operador. A [matriz de acesso](autorizacao.md) é obrigatória para rotas novas.

### 6.1 Criar câmara

```http
POST /api/camaras
Authorization: Bearer <accessToken>
Content-Type: application/json

{
  "codigo": "cam-01",
  "nome": "Câmara fria principal",
  "unidade": "UBS Centro",
  "capacidade": 5000,
  "temperaturaMinima": 2.0,
  "temperaturaMaxima": 8.0,
  "estado": "OPERACIONAL"
}
```

```http
HTTP/1.1 201 Created
Location: /api/camaras/1
Content-Type: application/json

{
  "id": 1,
  "codigo": "CAM-01",
  "nome": "Câmara fria principal",
  "unidade": "UBS Centro",
  "capacidade": 5000,
  "temperaturaMinima": 2.0,
  "temperaturaMaxima": 8.0,
  "estado": "OPERACIONAL",
  "estadoTermico": "SEM_LEITURAS",
  "ocupacao": 0,
  "ativo": true,
  "criadoEm": "2026-09-23T14:05:00-03:00",
  "atualizadoEm": "2026-09-23T14:05:00-03:00"
}
```

### 6.2 Criar lote

```http
POST /api/lotes
Authorization: Bearer <accessToken>
Content-Type: application/json

{
  "codigo": "fx2027a",
  "imunobiologico": "Febre amarela",
  "fabricante": "Bio-Manguinhos",
  "validade": "2027-03-31",
  "quantidade": 1200,
  "camaraId": 1
}
```

```http
HTTP/1.1 201 Created
Location: /api/lotes/10
Content-Type: application/json

{
  "id": 10,
  "codigo": "FX2027A",
  "imunobiologico": "Febre amarela",
  "fabricante": "Bio-Manguinhos",
  "validade": "2027-03-31",
  "quantidade": 1200,
  "camaraId": 1,
  "estado": "DISPONIVEL",
  "ativo": true,
  "criadoEm": "2026-09-23T14:10:00-03:00",
  "atualizadoEm": "2026-09-23T14:10:00-03:00"
}
```

### 6.3 Atualizar lote (troca de câmara)

```http
PUT /api/lotes/10
Authorization: Bearer <accessToken>
Content-Type: application/json

{
  "imunobiologico": "Febre amarela",
  "fabricante": "Bio-Manguinhos",
  "validade": "2027-03-31",
  "quantidade": 1200,
  "camaraId": 2
}
```

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "id": 10,
  "codigo": "FX2027A",
  "imunobiologico": "Febre amarela",
  "fabricante": "Bio-Manguinhos",
  "validade": "2027-03-31",
  "quantidade": 1200,
  "camaraId": 2,
  "estado": "DISPONIVEL",
  "ativo": true,
  "criadoEm": "2026-09-23T14:10:00-03:00",
  "atualizadoEm": "2026-09-23T15:30:00-03:00"
}
```

### 6.4 Listar lotes com filtros

```http
GET /api/lotes?imunobiologico=febre&camaraId=2&validadeDe=2027-01-01&validadeAte=2027-12-31&page=0&size=20
Authorization: Bearer <accessToken>
```

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "items": [
    {
      "id": 10,
      "codigo": "FX2027A",
      "imunobiologico": "Febre amarela",
      "fabricante": "Bio-Manguinhos",
      "validade": "2027-03-31",
      "quantidade": 1200,
      "camaraId": 2,
      "estado": "DISPONIVEL",
      "ativo": true,
      "criadoEm": "2026-09-23T14:10:00-03:00",
      "atualizadoEm": "2026-09-23T15:30:00-03:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

### 6.5 Erro de validação

```http
HTTP/1.1 422 Unprocessable Content
Content-Type: application/problem+json

{
  "type": "/problemas/validacao",
  "title": "Dados inválidos",
  "status": 422,
  "detail": "A requisição contém 2 campos inválidos.",
  "instance": "/api/camaras",
  "erros": [
    { "campo": "capacidade", "mensagem": "deve ser maior que zero" },
    { "campo": "temperaturaMinima", "mensagem": "deve ser menor que temperaturaMaxima" }
  ]
}
```

### 6.6 Capacidade excedida

```http
HTTP/1.1 409 Conflict
Content-Type: application/problem+json

{
  "type": "/problemas/capacidade-excedida",
  "title": "Capacidade da câmara excedida",
  "status": 409,
  "detail": "A câmara CAM-01 comporta 5000 doses e já possui 4500; não é possível alocar mais 1200.",
  "instance": "/api/lotes"
}
```

### 6.7 JSON malformado

Uma requisição com sintaxe JSON inválida, por exemplo `{"codigo":`, retorna:

```http
HTTP/1.1 400 Bad Request
Content-Type: application/problem+json

{
  "type": "/problemas/requisicao-invalida",
  "title": "Requisição inválida",
  "status": 400,
  "detail": "O body da requisição não é um JSON válido.",
  "instance": "/api/camaras"
}
```

### 6.8 Código duplicado

Repetir a criação com um código já cadastrado, inclusive com diferenças de maiúsculas ou espaços nas extremidades, retorna:

```http
HTTP/1.1 409 Conflict
Content-Type: application/problem+json

{
  "type": "/problemas/codigo-duplicado",
  "title": "Código duplicado",
  "status": 409,
  "detail": "Já existe câmara com o código CAM-01.",
  "instance": "/api/camaras"
}
```

## 7. Fora do escopo desta sprint

- Entidade e CRUD de Unidade (D2).
- Reativação de registros inativos.
- Estados térmicos da câmara (US05).
- Auditoria de ocorrências: o tipo `OCORRENCIA` já existe na trilha (§9), mas os registros chegam com a abertura e a resolução de ocorrências.

## 7. Autenticação e sessões (Sprint 2 — #80)

As rotas de autenticação e a configuração local estão descritas no [README](../README.md#autenticação-e-sessões) e na OpenAPI. As convenções de normalização e validação dos CRUDs (§2.3–2.4) não se aplicam às credenciais: login e senha são comparados exatamente; credenciais ausentes ou inválidas retornam 401 genérico, sem revelar a existência da conta. JSON malformado continua retornando 400.

- `POST /api/auth/login`: recebe `{ "login": "operador", "senha": "<senha local>" }`.
- `POST /api/auth/renovar`: recebe `{ "refreshToken": "<refresh mais recente>" }`, consome o refresh e retorna novo par de tokens.
- `POST /api/auth/logout`: recebe o mesmo formato, revoga o refresh e retorna 204. Refresh inválido/expirado/reutilizado recebe 401.
- `GET /api/auth/identidade`: exige Bearer JWT e retorna UUID interno, login e perfil. O UUID corresponde a `sub`, o login a `upn` e o perfil a `groups`; não consulta o banco.

JWT de acesso RS256: 15 minutos, validação local da assinatura, emissor e expiração pelo Quarkus, sem consulta ao banco e sem tolerância adicional. Refresh: opaco, aleatório, 24 horas, persistido apenas como hash SHA-256. Rotação e revogação são transacionais e serializadas por sessão. O logout não invalida o access já emitido, que continua válido até expirar (no máximo 15 minutos).

Login e renovação retornam 200 com `accessToken`, `refreshToken`, `tokenType`, `expiresIn` (900 segundos), `expiraEm`, `refreshExpiraEm` e `identidade`. Respostas com tokens não podem ser armazenadas em cache. Credenciais e sessões inválidas retornam Problem Details com `type=/problemas/autenticacao-invalida`, `title=Autenticação inválida`, `status=401` e `detail=Credenciais ou sessão inválidas.`. A rejeição de Bearer é realizada pelo mecanismo de segurança do Quarkus, antes do resource.

Os perfis disponíveis são `ADMINISTRADOR`, `OPERADOR` e `AUDITOR`. A autorização de câmaras e lotes segue a matriz da §8.

## 8. Autorização por operação (Sprint 2 — #81)

Todas as rotas de câmaras, lotes e leituras exigem Bearer JWT validado. Administrador executa todas as operações; operador consulta, registra baixas e envia leituras (§10); auditor consulta. Cadastro, edição, inativação e descarte são exclusivos de administrador. Ausência de token, token expirado ou inválido retorna 401; identidade válida sem perfil permitido retorna 403 antes da execução, sem gravação ou chamada Go. A rejeição de segurança é feita pelo Quarkus e pode retornar corpo vazio; não se exige Problem Details nesses 401/403.

Saúde, status, OpenAPI e Swagger UI permanecem públicos; isso não permite executar operações protegidas sem Bearer. Login, renovação e logout validam credenciais ou refresh, conforme §7. Perfil e ator nunca são aceitos do body, query ou cabeçalhos auxiliares para autorizar ou atribuir ações.

A [diretriz de autorização](autorizacao.md) define a matriz completa, as guardas reutilizáveis para próximas funcionalidades e o suporte de testes. `quarkus.security.jaxrs.deny-unannotated-endpoints=true` bloqueia toda nova rota REST sem anotação de segurança. Cada operação deve declarar `@PermissionsAllowed` no método e incluir testes de permissão; a regra também integra a Definição de Pronto e o checklist do PR.

## 9. Trilha de auditoria (Sprint 2 — #82)

Toda alteração confirmada de câmara ou lote gera um evento na tabela `eventos_auditoria`, gravado **na mesma transação** da alteração: se a alteração falha ou é desfeita, o evento também some; se o evento não pode ser gravado, a alteração é desfeita. Requisições recusadas (401, 403, 404, 409, 422) não geram evento. Repetições sem efeito também não: um `PUT` com os mesmos valores e um segundo descarte do mesmo lote respondem com sucesso, mas não entram na trilha.

| Recurso | Operação | Quando |
|---|---|---|
| `CAMARA` | `CADASTRO`, `ATUALIZACAO`, `INATIVACAO` | `POST`, `PUT` com alguma mudança, `DELETE` de câmara ativa |
| `LOTE` | `CADASTRO`, `ATUALIZACAO`, `INATIVACAO` | `POST`, `PUT` com alguma mudança, `DELETE` de lote ativo |
| `LOTE` | `BAIXA`, `DESCARTE` | `POST /baixas`; `POST /descarte` de lote ainda não descartado |
| `POLITICA_TERMICA` | `ALTERACAO_DE_POLITICA` | Nova versão, pela rota de política ou pelo `PUT` da câmara (§12); `recursoId` é o `id` da câmara |

Cada evento guarda:

| Campo | Conteúdo |
|---|---|
| `id` | Sequência do banco; define a ordem da trilha |
| `instante` | Momento oficial da alteração, em UTC (`Z`), com microssegundos |
| `ator.id`, `ator.login`, `ator.perfil` | `sub`, `upn` e perfil do JWT validado. Alterações sem usuário (tarefas internas) usam `ator.id = null` e `perfil = SISTEMA` |
| `recurso`, `recursoId` | Tipo e `id` do registro alterado |
| `operacao` | Uma das operações da tabela acima |
| `descricao` | Resumo legível, por exemplo `Lote FX2027A atualizado: quantidade 100 → 120` |
| `correlacaoId` | Correlação da requisição (abaixo) |

O ator **nunca** vem do body, da query ou de cabeçalhos auxiliares (`X-Usuario-Id`, `X-Perfil`); campos assim são ignorados. A trilha não guarda o token nem qualquer parte dele. Os eventos não são alterados nem apagados pela API, e o usuário de execução tem só `SELECT` e `INSERT` na tabela ([`runtime-grants.sql`](scripts/runtime-grants.sql)).

### 9.1 Correlação

Toda resposta traz o cabeçalho `X-Correlation-Id`. Se a requisição envia esse cabeçalho com 1 a 128 caracteres entre `A-Z`, `a-z`, `0-9`, `.`, `_`, `:` e `-`, o mesmo valor é usado; senão o servidor gera um UUID (o valor recebido é descartado, nunca vai para log nem para o banco). A correlação aparece no evento e nas linhas de log da requisição (`[correlacao=...]`).

### 9.2 Consulta

`GET /api/auditoria` — **ADMINISTRADOR** ou **AUDITOR** (`consultar-trilha`); operador recebe 403. Retorna a página da §4.3 com os eventos em ordem de `id` crescente. Consultar não gera evento. Não existe `POST`, `PUT`, `PATCH` nem `DELETE` na trilha (405).

| Parâmetro | Tipo | Comparação |
|---|---|---|
| `de` | date-time com offset | `instante >= de` |
| `ate` | date-time com offset | `instante <= ate`; não pode ser anterior a `de` |
| `usuarioId` | UUID | Igualdade com `ator.id` |
| `recurso` | enum (`CAMARA`, `LOTE`, `POLITICA_TERMICA`, `OCORRENCIA`) | Igualdade, em maiúsculas |
| `recursoId` | int64 > 0 | Igualdade; exige `recurso`, porque câmaras e lotes têm numerações próprias |
| `page`, `size` | integer | Ver D13 |

Filtros combinados usam **E** lógico e filtro vazio equivale a ausente. Data sem offset, UUID fora do formato canônico, enum desconhecido, `recursoId` sem `recurso` ou `de` depois de `ate` retornam 400 (`/problemas/requisicao-invalida`). Na URL, o `+` do offset deve ir como `%2B`; um `+` sem codificação chega como espaço e também é aceito.

```http
GET /api/auditoria?recurso=LOTE&recursoId=7&size=2
Authorization: Bearer <accessToken de AUDITOR>
```

```json
{
  "items": [
    {
      "id": 41,
      "instante": "2026-11-19T17:05:00.123456Z",
      "ator": { "id": "6f1c0a4e-2b7d-4c55-9a43-0f2d8e1b7c90", "login": "administrador", "perfil": "ADMINISTRADOR" },
      "recurso": "LOTE",
      "recursoId": 7,
      "operacao": "CADASTRO",
      "descricao": "Lote FX2027A cadastrado na câmara 3 com 100 doses",
      "correlacaoId": "8c6f2b1e-4a7d-4f3c-9b0e-1d2c3b4a5f60"
    },
    {
      "id": 44,
      "instante": "2026-11-19T17:20:11.004512Z",
      "ator": { "id": "0b9e7d52-61c3-4f1a-8e2d-5a7c9b3e1f04", "login": "operador", "perfil": "OPERADOR" },
      "recurso": "LOTE",
      "recursoId": 7,
      "operacao": "BAIXA",
      "descricao": "Baixa de 5 doses do lote FX2027A (ADMINISTRADA); restam 95",
      "correlacaoId": "baixa-ubs-centro-0001"
    }
  ],
  "page": 0,
  "size": 2,
  "totalElements": 2,
  "totalPages": 1
}
```

## 10. Recepção de leituras (Sprint 2 — #74)

`POST /api/leituras` recebe uma leitura individual, analisa-a no serviço Go e grava a leitura com a versão da política e o resultado térmico confirmado ([ADR 0003](decisoes/0003-servico-go-telemetria.md), seções 3, 5 e 7). Administrador e operador podem enviar; auditor recebe `403` e ausência de autenticação, `401`. `GET /api/leituras/{id}` consulta a leitura aceita (administrador, operador ou auditor).

| Campo | Tipo | Obrigatório | Regra |
|---|---|---|---|
| `idExterno` | string | sim | 1 a 100 caracteres, sem caractere nulo. O valor é preservado como enviado. Com `camaraId`, forma a chave de idempotência. |
| `camaraId` | integer (int64) | sim | ID positivo de câmara existente (`404`), ativa e `OPERACIONAL` (`409`). `EM_ALERTA` não impede o envio. |
| `temperatura` | number | sim | °C, de `-999.9` a `999.9`, no máximo 1 casa decimal; `8.50` equivale a `8.5`. |
| `medidaEm` | string (date-time) | sim | ISO 8601 com offset obrigatório (`Z` ou `±hh:mm`) e até 6 casas nos segundos. Em UTC, deve estar entre os anos 0001 e 9999. Deve ser posterior à última leitura aceita da câmara (`409`) e no máximo 5 minutos à frente do relógio da API (`422`). Devolvido em UTC. |
| `origem` | string | sim | 1 a 100 caracteres após o `trim`, sem caractere nulo. |

**Fluxo.** A câmara é bloqueada apenas em duas transações curtas, sem lock nem transação durante a chamada ao Go:

1. Com a câmara bloqueada, confere a chave, a elegibilidade e a ordem e lê o snapshot: política vigente, última leitura aceita, início do episódio (última resolução) e até N−1 leituras de contexto da política vigente. Encerra sem gravar.
2. Chama o Go fora de transação, com prazo de 800 ms (`TELEMETRY_DEADLINE`). Alocações e movimentações de lotes na mesma câmara não esperam por essa chamada.
3. Bloqueia a câmara de novo e revalida: se a chave já foi aceita, devolve o original (`200`) ou `409`; se a câmara saiu de operação, `409`; se a última leitura, a versão da política ou o início do episódio mudaram, refaz os passos 1 a 3 uma única vez e, persistindo a mudança, responde `409` (`/problemas/alteracao-concorrente`). Só grava o resultado calculado para o snapshot confirmado.

Com a nova tentativa, uma requisição pode esperar até duas vezes o prazo no Go, além do trabalho local.

**Respostas.**

- Primeira aceitação: `201`, `Location: /api/leituras/{id}` e a leitura.
- Reenvio com conteúdo equivalente (temperatura em escala 1, instante em UTC, origem após `trim`): `200` com o registro original, sem nova chamada ao Go. É seguro reenviar depois de perder a resposta HTTP.
- Mesma chave com outra temperatura, instante ou origem: `409` (`/problemas/leitura-conflitante`). `medidaEm` igual ou anterior à última leitura aceita, câmara inativa ou fora de `OPERACIONAL`: `409` (`/problemas/estado-incompativel`). Nenhum caso grava algo.
- Go ausente ou lento: `503` no prazo de cada chamada; resposta fora do contrato ou janela recusada: `502`. Nenhuma leitura é confirmada, não há fila nem `202`.

A abertura de ocorrência ao atingir o limiar e a atualização do estado térmico são da S2-11 (#75); a recepção em lote, da S2-36 (#99).

```http
POST /api/leituras
Authorization: Bearer <token de OPERADOR>
Content-Type: application/json

{
  "idExterno": "sensor-7:000123",
  "camaraId": 1,
  "temperatura": 9.4,
  "medidaEm": "2026-11-20T10:15:00-03:00",
  "origem": "sensor-7"
}
```

```http
HTTP/1.1 201 Created
Location: /api/leituras/42
Content-Type: application/json

{
  "id": 42,
  "camaraId": 1,
  "idExterno": "sensor-7:000123",
  "temperatura": 9.4,
  "medidaEm": "2026-11-20T13:15:00Z",
  "origem": "sensor-7",
  "politicaVersao": 1,
  "classificacao": "INSEGURA",
  "sequencia": 1,
  "limiarAtingido": false,
  "aceitaEm": "2026-11-20T13:15:02.120Z"
}
```

Reenvio da mesma chave com `"temperatura": 9.5`:

```json
{
  "type": "/problemas/leitura-conflitante",
  "title": "Leitura já registrada com outro conteúdo",
  "status": 409,
  "detail": "A leitura sensor-7:000123 da câmara 1 já foi registrada com outra temperatura, instante ou origem.",
  "instance": "/api/leituras"
}
```

## 11. Histórico de leituras e ocorrências

As consultas leem somente resultados confirmados no PostgreSQL, sem chamar Go ou
reclassificar leituras. Permanecem disponíveis após inativação de câmaras e lotes,
movimentação de lotes e alteração de política. Seguem a ADR 0003.

| Rota GET | Filtros | Ordenação fixa |
|---|---|---|
| `/api/camaras/{id}/leituras` | `de`, `ate`, `page`, `size` | instante DESC, id DESC |
| `/api/ocorrencias` | `camaraId`, `estado`, `de`, `ate`, `page`, `size` | abertaEm DESC, id DESC |
| `/api/ocorrencias/{id}` | — | Evidências na ordem registrada na abertura |

Todas exigem Bearer com permissão `consultar-operacao`: ADMINISTRADOR, OPERADOR
ou AUDITOR. Token ausente/inválido/expirado retorna 401; identidade autenticada
sem perfil permitido retorna 403. Erros de consulta usam `application/problem+json`;
401/403 podem retornar corpo vazio, conforme a política de segurança da §8.

### 11.1 Período, filtros e páginas

- Filtros combinados usam E lógico; omitidos não restringem o resultado.
- `de` e `ate` são opcionais individualmente e exigem ISO 8601 com offset explícito.
  O intervalo `[de, ate)` inclui `de` e exclui `ate`. Para leituras, considera
  `instante`; para ocorrências, `abertaEm`, não sobreposição do episódio.
- `2026-11-19T10:00:00-03:00` e `2026-11-19T13:00:00Z` são o mesmo instante.
  As respostas devolvem todos os instantes em UTC com `Z`.
- `estado` aceita exatamente `ABERTA` ou `RESOLVIDA`; é o estado da ocorrência,
  independente dos estados operacional e térmico da câmara.
- `camaraId` deve ser positivo. Câmara inexistente como filtro retorna página
  vazia (200); câmara inexistente na rota de leituras retorna 404.
- `page` padrão 0, mínimo 0; `size` padrão 20, entre 1 e 100. Valores inválidos,
  parâmetros presentes vazios, datas sem offset e `de >= ate` retornam 400.
- Envelope: `items`, `page`, `size`, `totalElements`, `totalPages`. Os totais
  consideram os mesmos filtros dos itens. Página além do fim retorna 200 e
  `items: []`, mantendo os totais. A ordenação é específica da rota, diferentemente
  da ordem por ID crescente do inventário.
- Não há filtro implícito por cadastro ativo. Ocorrência inexistente no detalhe
  retorna 404. Não há exportação, retenção ou consulta global de leituras.

### 11.2 Representações

**Leitura:** `id`, `camaraId`, `idExterno`, `origem`, `temperatura`, `instante`,
`aceitaEm`, `politica`, `classificacao` (SEGURA/INSEGURA), `sequencia` e
`limiarAtingido`. `politica` contém `versao`, `temperaturaMinima`,
`temperaturaMaxima` e `limiar` da versão original, não da vigente.

**Resumo de ocorrência:** `id`, `camaraId`, `estado`, `abertaEm`, `politica`,
`resolvidaEm`, `resolvidaPor` e `justificativa`. Não carrega evidências nem lotes.

**Detalhe:** os campos do resumo, mais `leituraAberturaId`, `evidencias` (leituras
completas na ordem persistida), `lotesAfetados` e `leituraResolucaoId`.
Cada lote afetado traz `loteId`, `codigo`, `imunobiologico` e `quantidade`, copiados
na abertura. Alterar o cadastro do lote não atualiza essa fotografia.

Exemplo: uma leitura de 9 °C na versão com faixa 2–8 °C permanece INSEGURA
mesmo após mudar a faixa vigente para 2–10 °C. Um lote com 100 doses na abertura
continua com 100 doses no snapshot mesmo após movimentação ou baixa.

Ocorrência aberta tem os dados de resolução nulos. `resolvidaPor` é o UUID salvo,
sem expor login ou credenciais. Registros de resolução sem autor permanecem com
esse campo nulo; a consulta não infere o responsável a partir do usuário que
está consultando.

### 11.3 Execução das consultas

Filtros, contagem e paginação são executados no banco. As políticas da página
são buscadas em conjunto; o detalhe busca as leituras de evidência em conjunto,
reordenando pelos IDs registrados. Isso evita uma consulta por item (N+1).
A migração V7 acrescenta índices globais de abertura/ID e estado/abertura/ID,
sem modificar migrações já aplicadas. A página e a contagem usam o isolamento
transacional padrão: novas gravações concorrentes podem mudar páginas entre
requisições; não se promete um snapshot único entre páginas.

## 12. Política térmica (Sprint 2 — #78)

A política térmica de cada câmara tem faixa segura e limiar N, em versões imutáveis (§3.5). As rotas abaixo consultam a versão vigente, alteram faixa e limiar e listam o histórico.

| Rota | Permissão | Sucesso |
|---|---|---|
| `GET /api/camaras/{id}/politica-termica` | `consultar-operacao` (ADMINISTRADOR, OPERADOR, AUDITOR) | `200` com a versão vigente |
| `PUT /api/camaras/{id}/politica-termica` | `alterar-politica` (ADMINISTRADOR) | `200` com a versão vigente depois da chamada |
| `GET /api/camaras/{id}/politica-termica/versoes` | `consultar-operacao` | `200` com página, da versão mais recente para a mais antiga |

Representação de uma versão: `camaraId`, `versao`, `temperaturaMinima`, `temperaturaMaxima`, `limiar`, `autorId` (UUID do administrador; nulo quando não há autor conhecido, como na versão 1 do cadastro e nas versões criadas pela migração V4) e `criadaEm`. As consultas funcionam também para câmara inativa; câmara inexistente retorna `404`.

### 12.1 Alteração com versão esperada

```http
PUT /api/camaras/3/politica-termica
Authorization: Bearer <accessToken de ADMINISTRADOR>
Content-Type: application/json

{ "versaoEsperada": 1, "temperaturaMinima": 2.0, "temperaturaMaxima": 8.0, "limiar": 4 }
```

- `versaoEsperada` é a `versao` que o cliente consultou. Se não for a vigente, a resposta é `409` (`/problemas/versao-desatualizada`) e nada é gravado: dois administradores que partem da mesma versão não se sobrescrevem. A câmara fica bloqueada durante a alteração, então o segundo envio sempre vê a versão nova.
- Todos os campos são obrigatórios. Faixa e precisão seguem a câmara (§2.4); o limiar vai de `1` a `100`. Valores inválidos retornam `422` sem criar versão.
- Valores iguais aos vigentes (inclusive `8` e `8.00`) respondem `200` com a mesma versão, sem nova versão nem evento.
- Uma mudança real cria a versão seguinte e mantém as anteriores. O autor é o `sub` do JWT validado; `autorId`, `versao` ou outro campo de autoria no body são ignorados.
- Câmara inativa retorna `409` (`/problemas/estado-incompativel`); operador e auditor recebem `403`.

O `PUT /api/camaras/{id}` segue a mesma regra para a faixa: mudar a faixa exige `versaoEsperada` (sem ela, `422` com o campo `versaoEsperada`; desatualizada, `409`), e nada é gravado em nenhum dos dois casos, nem os outros campos do mesmo PUT. Se a faixa enviada é a vigente, o campo pode ser omitido; informado, é conferido mesmo assim. Ele só altera a faixa e preserva o limiar.

### 12.2 Efeitos de uma nova versão

- **Leituras:** a próxima leitura é analisada na versão nova, com contexto só dessa versão, então a sequência recomeça (a primeira insegura tem `sequencia = 1`). Leituras e ocorrências antigas continuam com a versão em que foram analisadas (§11).
- **Estado térmico:** volta a `SEM_LEITURAS`; com ocorrência aberta, a câmara continua `EM_ALERTA` e a ocorrência continua aberta.
- **Trilha (§9):** um evento `POLITICA_TERMICA` / `ALTERACAO_DE_POLITICA`, gravado na mesma transação, por exemplo `Política térmica da câmara CAM-01 passou da versão 1 para a 2: faixa 2.0 a 8.0 °C → 3.0 a 7.0 °C; limiar 3 → 4`. Um `PUT` da câmara que muda nome e faixa gera dois eventos: `CAMARA` / `ATUALIZACAO` para os outros campos e `POLITICA_TERMICA` para a faixa.
- **Resumo (US07):** o aviso de mudança, entregue depois do commit, invalida o cache do resumo. Não há cache distribuído.

Se a gravação da versão ou do evento falha, a transação inteira é desfeita: não sobra versão sem evento nem evento sem versão. Excluir versões em uso e reclassificar o histórico estão fora do escopo.

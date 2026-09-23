# Contrato da API — Câmaras e Lotes

Este documento registra campos, regras, rotas, filtros e respostas usados na implementação de US01, US02 e US03 (Sprint 1). Os caminhos descritos aqui ainda não existem no código. A organização interna do serviço está em [ADR 0001](adr/0001-arquitetura-em-camadas.md).

Itens marcados com **⚠️ Pendente** dependem de validação externa e não devem ser tratados como aceitos.

## 1. Decisões

| # | Assunto | Decisão |
|---|---|---|
| D1 | Entidades | `Camara` e `Lote`. Uma câmara contém vários lotes; cada lote pertence a exatamente uma câmara. |
| D2 | Unidade da câmara | Representada pelo campo texto obrigatório `unidade` na câmara. A entidade Unidade prevista na [proposta](proposta.md#4-entidades-principais) fica **adiada**; não haverá um terceiro CRUD nesta sprint. |
| D3 | Identificadores | `id` é o identificador técnico (inteiro gerado pelo banco) usado nas rotas. `codigo` da câmara e `codigo` do lote são identificadores de negócio únicos, normalizados (ver [§2.3](#23-normalização)). |
| D4 | Unidade de contagem | `capacidade` da câmara e `quantidade` do lote são contadas em **doses**, sempre inteiros positivos. |
| D5 | Estado da câmara | `estado` (operacional) é independente de `ativo` (cadastro). Valores manuais: `OPERACIONAL` e `MANUTENCAO`. Detecção térmica não é antecipada (ver [§3.1](#31-estado-da-câmara)). |
| D6 | Ocupação | `ocupacao` = soma de `quantidade` dos lotes **ativos** da câmara. Inativar um lote libera capacidade. |
| D7 | Alteração de câmara | Rejeitada com `409` se reduzir `capacidade` abaixo da `ocupacao`. Inativação de câmara com lotes ativos também retorna `409`. |
| D8 | Alteração de lote | Editáveis: `imunobiologico`, `fabricante`, `validade`, `quantidade` e `camaraId` (troca de câmara permitida). Capacidade é revalidada quando `quantidade` ou `camaraId` mudam. `codigo` é imutável. |
| D9 | Validade | Data de referência: dia corrente no fuso `America/Fortaleza`. Validade futura significa `validade > hoje`. A regra vale na criação e quando `validade` é alterada; um lote que venceu depois do cadastro continua editável se a `validade` enviada for igual à armazenada. |
| D10 | Filtro de validade | Intervalo **inclusivo** `validadeDe`/`validadeAte`. Com apenas um limite, o intervalo fica aberto do outro lado. `validadeDe > validadeAte` retorna `400`. |
| D11 | Inativos | Ficam fora das listagens por padrão. `ativo=false` lista só inativos; `ativo=true` (padrão) lista só ativos. `GET /{id}` retorna o registro mesmo inativo. Códigos de registros inativos continuam reservados. |
| D12 | Remoção × inativação | `DELETE` faz **remoção lógica** (`ativo=false`), preserva o registro e responde `204`. Repetir `DELETE` num registro já inativo também responde `204`. **⚠️ Pendente:** confirmar com o professor se a remoção lógica atende à rubrica. |
| D13 | Paginação | `page` começa em `0` (padrão `0`); `size` padrão `20`, mínimo `1`, máximo `100`. Ordenação fixa por `id ASC`. Valores fora do intervalo retornam `400`. |
| D14 | Idioma | Rotas, campos, filtros e mensagens em português, sem acentos nos identificadores (`camaraId`, `imunobiologico`). |
| D15 | Erros | Formato Problem Details ([RFC 9457](https://www.rfc-editor.org/rfc/rfc9457)) com `Content-Type: application/problem+json`. |

## 2. Recursos

Datas usam ISO 8601: `validade` é data sem hora (`2027-03-31`); instantes de auditoria têm offset explícito (`2026-09-23T14:05:00-03:00`).

### 2.1 Câmara

| Campo | Tipo | Entrada | Regras |
|---|---|---|---|
| `id` | integer (int64) | — | Somente leitura. |
| `codigo` | string | Obrigatório na criação; imutável | 3 a 20 caracteres; `A-Z`, `0-9` e `-` após normalização; único entre todas as câmaras, inclusive inativas. |
| `nome` | string | Obrigatório | 1 a 100 caracteres após `trim`. |
| `unidade` | string | Obrigatório | 1 a 100 caracteres após `trim`. |
| `capacidade` | integer (int32) | Obrigatório | `> 0`, em doses; não pode ficar abaixo de `ocupacao`. |
| `temperaturaMinima` | number (decimal, 1 casa) | Obrigatório | Em °C; `temperaturaMinima < temperaturaMaxima`. |
| `temperaturaMaxima` | number (decimal, 1 casa) | Obrigatório | Em °C. |
| `estado` | string (enum) | Obrigatório | `OPERACIONAL` ou `MANUTENCAO`. |
| `ocupacao` | integer (int32) | — | Somente leitura; soma dos lotes ativos. |
| `ativo` | boolean | — | Somente leitura; alterado apenas por `DELETE`. |
| `criadoEm` | string (date-time) | — | Somente leitura. |
| `atualizadoEm` | string (date-time) | — | Somente leitura. |

### 2.2 Lote

| Campo | Tipo | Entrada | Regras |
|---|---|---|---|
| `id` | integer (int64) | — | Somente leitura. |
| `codigo` | string | Obrigatório na criação; imutável | 1 a 40 caracteres; `A-Z`, `0-9`, `-` e `/` após normalização; único entre todos os lotes, inclusive inativos. |
| `imunobiologico` | string | Obrigatório | 1 a 100 caracteres após `trim`. |
| `fabricante` | string | Obrigatório | 1 a 100 caracteres após `trim`. |
| `validade` | string (date) | Obrigatório | `> hoje` em `America/Fortaleza` (ver D9). |
| `quantidade` | integer (int32) | Obrigatório | `> 0`, em doses. |
| `camaraId` | integer (int64) | Obrigatório | Câmara existente, ativa e `OPERACIONAL` (ver [§3.2](#32-alocação-de-lotes)). |
| `ativo` | boolean | — | Somente leitura; alterado apenas por `DELETE`. |
| `criadoEm` | string (date-time) | — | Somente leitura. |
| `atualizadoEm` | string (date-time) | — | Somente leitura. |

### 2.3 Normalização

- Todos os textos recebem `trim`; texto vazio após `trim` é tratado como ausente.
- `codigo` (câmara e lote) é convertido para maiúsculas antes de validar e comparar. `cam-01` e `CAM-01` são o mesmo código.
- Campos desconhecidos no JSON são ignorados; campos somente leitura enviados no corpo são ignorados.

## 3. Regras de negócio

### 3.1 Estado da câmara

| Estado | Significado | Aceita novos lotes? |
|---|---|---|
| `OPERACIONAL` | Câmara em uso normal | Sim |
| `MANUTENCAO` | Retirada de uso por decisão manual | Não |

- Transições manuais permitidas via `PUT`: `OPERACIONAL ↔ MANUTENCAO`.
- Colocar em `MANUTENCAO` uma câmara com lotes ativos é permitido; os lotes permanecem alocados.
- Estados ligados à excursão térmica (por exemplo `EM_ALERTA`) serão definidos na Sprint 2 (US05) e só poderão ser atribuídos pelo sistema.
- Uma câmara inativa não pode ser alterada (`PUT` → `409`).

### 3.2 Alocação de lotes

- Um lote só pode ser criado ou movido para uma câmara **ativa** e **`OPERACIONAL`**; caso contrário, `409`.
- `camaraId` inexistente → `404`.
- Condição de capacidade: `ocupacao_atual_da_câmara_destino - quantidade_anterior_se_mesma_câmara + quantidade_nova <= capacidade`. Violação → `409`.
- Na troca de câmara, a quantidade sai da origem e entra no destino; só o destino precisa ser revalidado.
- A verificação e a gravação ocorrem na mesma transação, com bloqueio da linha da câmara de destino (`SELECT ... FOR UPDATE`), para que alocações concorrentes não ultrapassem a capacidade e não haja persistência parcial.

### 3.3 Inativação

- `DELETE /api/camaras/{id}` com lotes ativos → `409`. Os lotes precisam ser inativados ou movidos antes.
- `DELETE /api/lotes/{id}` libera a quantidade na ocupação da câmara.
- Reativação não faz parte desta sprint.
- Registros inativos não podem ser alterados (`PUT` → `409`).

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

| Recurso | Parâmetro | Tipo | Comparação |
|---|---|---|---|
| Câmaras | `unidade` | string | Igualdade, sem diferenciar maiúsculas/minúsculas |
| Câmaras | `estado` | enum | Igualdade (`OPERACIONAL`, `MANUTENCAO`) |
| Lotes | `imunobiologico` | string | Busca parcial (contém), sem diferenciar maiúsculas/minúsculas |
| Lotes | `validadeDe` | date | `validade >= validadeDe` |
| Lotes | `validadeAte` | date | `validade <= validadeAte` |
| Lotes | `camaraId` | int64 | Igualdade |
| Ambos | `ativo` | boolean | Igualdade; padrão `true` |
| Ambos | `page`, `size` | integer | Ver D13 |

Filtros combinados usam **E** lógico. `camaraId` inexistente num filtro não é erro: a página volta vazia.

### 4.2 Página

`totalElements` e `totalPages` contam apenas registros que atendem aos filtros. Uma página além da última retorna `200` com `items` vazio.

| Campo | Tipo |
|---|---|
| `items` | array do recurso |
| `page` | integer |
| `size` | integer |
| `totalElements` | integer (int64) |
| `totalPages` | integer |

## 5. Erros

| Status | Quando | `type` |
|---|---|---|
| `400` | JSON malformado; parâmetro de consulta com tipo inválido; `page`/`size` fora do intervalo; `validadeDe > validadeAte` | `/problemas/requisicao-invalida` |
| `404` | Recurso da rota inexistente; `camaraId` do corpo inexistente | `/problemas/recurso-nao-encontrado` |
| `409` | Código duplicado | `/problemas/codigo-duplicado` |
| `409` | Capacidade excedida ou redução abaixo da ocupação | `/problemas/capacidade-excedida` |
| `409` | Operação incompatível com o estado atual (registro inativo, câmara em manutenção, câmara com lotes ativos) | `/problemas/estado-incompativel` |
| `422` | Campo ausente, fora do formato ou regra de entrada violada (inclusive validade não futura e `temperaturaMinima >= temperaturaMaxima`) | `/problemas/validacao` |

Todo Problem Details contém `type`, `title`, `status`, `detail` e `instance`. Erros `422` incluem `erros`, uma lista de `{ "campo": string, "mensagem": string }`.

## 6. Exemplos

### 6.1 Criar câmara

```http
POST /api/camaras
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
  "ocupacao": 0,
  "ativo": true,
  "criadoEm": "2026-09-23T14:05:00-03:00",
  "atualizadoEm": "2026-09-23T14:05:00-03:00"
}
```

### 6.2 Criar lote

```http
POST /api/lotes
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
  "ativo": true,
  "criadoEm": "2026-09-23T14:10:00-03:00",
  "atualizadoEm": "2026-09-23T14:10:00-03:00"
}
```

### 6.3 Atualizar lote (troca de câmara)

```http
PUT /api/lotes/10
Content-Type: application/json

{
  "imunobiologico": "Febre amarela",
  "fabricante": "Bio-Manguinhos",
  "validade": "2027-03-31",
  "quantidade": 1000,
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
  "quantidade": 1000,
  "camaraId": 2,
  "ativo": true,
  "criadoEm": "2026-09-23T14:10:00-03:00",
  "atualizadoEm": "2026-09-23T15:30:00-03:00"
}
```

### 6.4 Listar lotes com filtros

```http
GET /api/lotes?imunobiologico=febre&validadeAte=2027-12-31&page=0&size=20
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
      "quantidade": 1000,
      "camaraId": 2,
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

## 7. Fora do escopo desta sprint

- Entidade e CRUD de Unidade (D2).
- Reativação de registros inativos.
- Estados térmicos da câmara e política térmica (US05, US08).
- Autenticação, perfis e auditoria (US09, US10).

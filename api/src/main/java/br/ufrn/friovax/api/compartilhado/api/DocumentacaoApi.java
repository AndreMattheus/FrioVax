package br.ufrn.friovax.api.compartilhado.api;

import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.Components;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.info.License;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Metadados e respostas de erro compartilhadas da especificação OpenAPI (contrato, §5).
 * As rotas e os esquemas são descritos nas próprias classes da camada API.
 */
@OpenAPIDefinition(
    info = @Info(
        title = "FrioVax API",
        version = "0.1.0",
        description = DocumentacaoApi.DESCRICAO,
        license = @License(name = "MIT", url = "https://opensource.org/licenses/MIT")
    ),
    tags = {
        @Tag(name = DocumentacaoApi.TAG_CAMARAS, description = "Câmaras de conservação, sua capacidade em doses e "
            + "seu estado operacional."),
        @Tag(name = DocumentacaoApi.TAG_LOTES, description = "Lotes de imunobiológicos alocados em câmaras."),
        @Tag(name = DocumentacaoApi.TAG_STATUS, description = "Verificação simples de disponibilidade da API.")
    },
    components = @Components(responses = {
        @APIResponse(name = DocumentacaoApi.REQUISICAO_INVALIDA, responseCode = "400",
            description = "JSON malformado, valor de tipo incompatível no body (por exemplo `10.5` em campo inteiro "
                + "ou enum desconhecido) ou parâmetro de consulta inválido.",
            content = @Content(mediaType = DocumentacaoApi.PROBLEM_JSON,
                schema = @Schema(implementation = ProblemDetails.class),
                examples = @ExampleObject(name = "jsonMalformado", value = """
                    {
                      "type": "/problemas/requisicao-invalida",
                      "title": "Requisição inválida",
                      "status": 400,
                      "detail": "O body da requisição não é um JSON válido.",
                      "instance": "/api/camaras"
                    }"""))),
        @APIResponse(name = DocumentacaoApi.NAO_ENCONTRADO, responseCode = "404",
            description = "Recurso da rota inexistente.",
            content = @Content(mediaType = DocumentacaoApi.PROBLEM_JSON,
                schema = @Schema(implementation = ProblemDetails.class),
                examples = @ExampleObject(name = "naoEncontrado", value = """
                    {
                      "type": "/problemas/recurso-nao-encontrado",
                      "title": "Recurso não encontrado",
                      "status": 404,
                      "detail": "Câmara 99 não encontrado(a).",
                      "instance": "/api/camaras/99"
                    }"""))),
        @APIResponse(name = DocumentacaoApi.DADOS_INVALIDOS, responseCode = "422",
            description = "Body ausente, campo obrigatório ausente ou em branco, ou regra de entrada violada. "
                + "A lista `erros` identifica cada campo; sem body, o campo informado é `body`.",
            content = @Content(mediaType = DocumentacaoApi.PROBLEM_JSON,
                schema = @Schema(implementation = ProblemDetails.class),
                examples = @ExampleObject(name = "camposInvalidos", value = """
                    {
                      "type": "/problemas/validacao",
                      "title": "Dados inválidos",
                      "status": 422,
                      "detail": "A requisição contém 2 campo(s) inválido(s).",
                      "instance": "/api/camaras",
                      "erros": [
                        { "campo": "capacidade", "mensagem": "deve ser maior que zero" },
                        { "campo": "temperaturaMinima", "mensagem": "deve ser menor que temperaturaMaxima" }
                      ]
                    }"""))),
        @APIResponse(name = DocumentacaoApi.ERRO_INTERNO, responseCode = "500",
            description = "Falha inesperada. A mensagem é genérica; os detalhes técnicos ficam apenas no log.",
            content = @Content(mediaType = DocumentacaoApi.PROBLEM_JSON,
                schema = @Schema(implementation = ProblemDetails.class),
                examples = @ExampleObject(name = "erroInterno", value = """
                    {
                      "type": "/problemas/erro-interno",
                      "title": "Erro interno",
                      "status": 500,
                      "detail": "Ocorreu um erro inesperado. Tente novamente ou contate o suporte.",
                      "instance": "/api/lotes"
                    }""")))
    })
)
public class DocumentacaoApi extends Application {

    public static final String TAG_CAMARAS = "Câmaras";
    public static final String TAG_LOTES = "Lotes";
    public static final String TAG_STATUS = "Status";

    public static final String PROBLEM_JSON = "application/problem+json";

    // Nomes das respostas reutilizáveis, referenciadas nas rotas por @APIResponse(ref = ...).
    public static final String REQUISICAO_INVALIDA = "RequisicaoInvalida";
    public static final String NAO_ENCONTRADO = "NaoEncontrado";
    public static final String DADOS_INVALIDOS = "DadosInvalidos";
    public static final String ERRO_INTERNO = "ErroInterno";

    static final String DESCRICAO = """
        API de gestão de câmaras de conservação e lotes de imunobiológicos. As regras completas estão em \
        [docs/contrato-api.md](https://github.com/AndreMattheus/FrioVax/blob/main/docs/contrato-api.md).

        **Convenções**

        - Rotas, campos, filtros e mensagens em português, sem acentos nos identificadores.
        - `id` é o identificador técnico usado nas rotas; `codigo` é o identificador de negócio, imutável e único \
        entre todos os registros, inclusive inativos.
        - Textos recebem `trim`; texto vazio após o `trim` é tratado como ausente. `codigo` é convertido para \
        maiúsculas antes de validar e comparar (`cam-01` e `CAM-01` são o mesmo código).
        - Campos desconhecidos e campos somente leitura enviados no body são ignorados.
        - Datas seguem ISO 8601: `validade` é data sem hora (`2027-03-31`); `criadoEm` e `atualizadoEm` são \
        instantes com offset (`2026-09-23T14:05:00-03:00`). "Hoje" é o dia corrente em `America/Fortaleza`.
        - `capacidade`, `ocupacao` e `quantidade` são contadas em doses.

        **Inativação**

        `DELETE` faz remoção lógica: o registro passa a `ativo=false`, continua consultável por `GET /{id}` e seu \
        código continua reservado. Repetir o `DELETE` responde `204`. Registros inativos não podem ser alterados \
        (`409`) e não há reativação nesta versão.

        **Capacidade**

        A `ocupacao` de uma câmara é a soma da `quantidade` dos seus lotes ativos. Um lote só entra numa câmara \
        ativa e `OPERACIONAL`, desde que a ocupação resultante não ultrapasse a `capacidade`; a câmara não pode \
        reduzir a capacidade abaixo da ocupação. Inativar um lote libera a quantidade dele na câmara.

        **Listagens**

        Filtros combinados usam E lógico. Por padrão só aparecem registros ativos (`ativo=true`). A paginação \
        começa em `page=0`, com `size` de 1 a 100 (padrão 20) e ordenação fixa por `id` crescente.

        **Erros**

        Respostas de erro seguem Problem Details (RFC 9457) com `Content-Type: application/problem+json`. O campo \
        `type` identifica a categoria: `/problemas/requisicao-invalida` (400), `/problemas/recurso-nao-encontrado` \
        (404), `/problemas/codigo-duplicado`, `/problemas/capacidade-excedida` e `/problemas/estado-incompativel` \
        (409), `/problemas/validacao` (422) e `/problemas/erro-interno` (500).
        """;
}

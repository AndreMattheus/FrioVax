package br.ufrn.friovax.api.camara.api;

import br.ufrn.friovax.api.camara.aplicacao.AtualizarCamara;
import br.ufrn.friovax.api.camara.aplicacao.AtualizarCamaraDTO;
import br.ufrn.friovax.api.camara.aplicacao.CadastrarCamaraDTO;
import br.ufrn.friovax.api.camara.aplicacao.CamaraService;
import br.ufrn.friovax.api.camara.aplicacao.ConsultarCamara;
import br.ufrn.friovax.api.camara.aplicacao.InativarCamara;
import br.ufrn.friovax.api.camara.dominio.CamaraFiltro;
import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import br.ufrn.friovax.api.compartilhado.api.DocumentacaoApi;
import br.ufrn.friovax.api.compartilhado.api.ProblemDetails;
import br.ufrn.friovax.api.compartilhado.dominio.Paginacao;
import br.ufrn.friovax.api.compartilhado.api.PaginaResponse;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.QueryParam;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.headers.Header;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/camaras")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = DocumentacaoApi.TAG_CAMARAS)
@APIResponse(responseCode = "500", ref = DocumentacaoApi.ERRO_INTERNO)
public class CamaraResource {

    @Context
    UriInfo uriInfo;

    private final CamaraService service;
    private final ConsultarCamara consultarCamara;
    private final AtualizarCamara atualizarCamara;
    private final InativarCamara inativarCamara;

    @Inject
    public CamaraResource(CamaraService service, ConsultarCamara consultarCamara,
                          AtualizarCamara atualizarCamara, InativarCamara inativarCamara) {
        this.service = service;
        this.consultarCamara = consultarCamara;
        this.atualizarCamara = atualizarCamara;
        this.inativarCamara = inativarCamara;
    }

    @POST
    @Operation(summary = "Cadastrar câmara",
        description = "Cria uma câmara ativa, com ocupação zero, em qualquer um dos estados manuais.")
    @APIResponse(responseCode = "201", description = "Câmara criada.",
        headers = @Header(name = "Location", description = "Endereço da câmara criada.",
            schema = @Schema(type = SchemaType.STRING, format = "uri")),
        content = @Content(mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = CamaraResponse.class)))
    @APIResponse(responseCode = "400", ref = DocumentacaoApi.REQUISICAO_INVALIDA)
    @APIResponse(responseCode = "409", description = "Já existe câmara com o mesmo código, inclusive inativa "
        + "(`/problemas/codigo-duplicado`).",
        content = @Content(mediaType = DocumentacaoApi.PROBLEM_JSON,
            schema = @Schema(implementation = ProblemDetails.class)))
    @APIResponse(responseCode = "422", ref = DocumentacaoApi.DADOS_INVALIDOS)
    public Response cadastrar(@NotNull(message = "deve ser informado") @Valid CamaraRequest body,
                              @Context UriInfo uriInfo) {
        var dados = new CadastrarCamaraDTO(body.codigo(), body.nome(), body.unidade(), body.capacidade(),
            body.temperaturaMinima(), body.temperaturaMaxima(), body.estado());
        var resultado = service.cadastrar(dados);
        var resposta = new CamaraResponse(resultado.id(), resultado.codigo(), resultado.nome(), resultado.unidade(),
            resultado.capacidade(), resultado.temperaturaMinima(), resultado.temperaturaMaxima(),
            resultado.estado(), resultado.ocupacao(), resultado.ativo(),
            resultado.criadoEm(), resultado.atualizadoEm());
        var location = uriInfo.getAbsolutePathBuilder().path(Long.toString(resultado.id())).build();

        return Response.created(location).entity(resposta).build();
    }

    @GET
    @Operation(summary = "Listar câmaras",
        description = "Lista câmaras paginadas por `id` crescente. Filtros combinados usam E lógico; sem `ativo`, "
            + "só aparecem câmaras ativas.")
    @APIResponse(responseCode = "200", description = "Página de câmaras; `items` vem vazio além da última página.")
    @APIResponse(responseCode = "400", ref = DocumentacaoApi.REQUISICAO_INVALIDA)
    public PaginaResponse<CamaraResponse> listar(
            @Parameter(description = "Unidade exata, sem diferenciar maiúsculas de minúsculas.",
                example = "UBS Centro")
            @QueryParam("unidade") String unidade,
            @Parameter(description = "Estado operacional exato. Vazio equivale a ausente.",
                schema = @Schema(implementation = EstadoCamara.class))
            @QueryParam("estado") String estado,
            @Parameter(description = "`true` lista só câmaras ativas; `false`, só inativas.",
                schema = @Schema(type = SchemaType.BOOLEAN, defaultValue = "true"))
            @QueryParam("ativo") String ativo,
            @Parameter(description = "Número da página, a partir de 0.",
                schema = @Schema(type = SchemaType.INTEGER, format = "int32", minimum = "0", defaultValue = "0"))
            @QueryParam("page") String page,
            @Parameter(description = "Itens por página.", schema = @Schema(type = SchemaType.INTEGER,
                format = "int32", minimum = "1", maximum = "100", defaultValue = "20"))
            @QueryParam("size") String size) {
        CamaraFiltro filtro;
        Paginacao paginacao;
        try {
            // O REST converte parâmetros vazios em null; a URI distingue vazio de ausente.
            if (uriInfo != null) {
                for (String parametro : new String[]{"page", "size", "ativo"}) {
                    var valores = uriInfo.getQueryParameters().get(parametro);
                    if (valores != null && valores.stream().anyMatch(valor -> valor == null || valor.isBlank())) {
                        throw new IllegalArgumentException(parametro + " deve ser informado");
                    }
                }
            }
            ativo = ativo == null ? "true" : ativo;
            if (!"true".equals(ativo) && !"false".equals(ativo)) {
                throw new IllegalArgumentException("ativo deve ser true ou false");
            }
            filtro = new CamaraFiltro(unidade,
                    estado == null || estado.trim().isEmpty() ? null : EstadoCamara.valueOf(estado.trim()),
                    Boolean.parseBoolean(ativo));
            paginacao = new Paginacao(page == null ? Paginacao.PAGINA_PADRAO : Integer.parseInt(page),
                    size == null ? Paginacao.TAMANHO_PADRAO : Integer.parseInt(size));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Parâmetros de consulta inválidos.", e);
        }
        return PaginaResponse.de(consultarCamara.listar(filtro, paginacao).map(CamaraResponse::de));
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Consultar câmara", description = "Retorna a câmara com sua ocupação atual, mesmo inativa.")
    @APIResponse(responseCode = "200", description = "Câmara encontrada.")
    @APIResponse(responseCode = "404", ref = DocumentacaoApi.NAO_ENCONTRADO)
    public CamaraResponse consultar(@Parameter(description = "Identificador técnico da câmara.", example = "1")
                                    @PathParam("id") long id) {
        return CamaraResponse.de(consultarCamara.consultar(id));
    }

    @PUT
    @Path("/{id}")
    @Operation(summary = "Atualizar câmara",
        description = "Substitui todos os campos editáveis; todos são obrigatórios e o `codigo` não muda. As "
            + "transições manuais entre `OPERACIONAL`, `MANUTENCAO` e `DESATIVADA` são livres, mas mudar para "
            + "`MANUTENCAO` ou `DESATIVADA` exige que a câmara não tenha lotes ativos.")
    @APIResponse(responseCode = "200", description = "Câmara atualizada.")
    @APIResponse(responseCode = "400", ref = DocumentacaoApi.REQUISICAO_INVALIDA)
    @APIResponse(responseCode = "404", ref = DocumentacaoApi.NAO_ENCONTRADO)
    @APIResponse(responseCode = "409", description = "Capacidade abaixo da ocupação atual "
        + "(`/problemas/capacidade-excedida`); câmara inativa ou mudança para `MANUTENCAO`/`DESATIVADA` com lotes "
        + "ativos (`/problemas/estado-incompativel`).",
        content = @Content(mediaType = DocumentacaoApi.PROBLEM_JSON,
            schema = @Schema(implementation = ProblemDetails.class)))
    @APIResponse(responseCode = "422", ref = DocumentacaoApi.DADOS_INVALIDOS)
    public CamaraResponse atualizar(@Parameter(description = "Identificador técnico da câmara.", example = "1")
                                    @PathParam("id") long id,
                                    @NotNull(message = "deve ser informado") @Valid AtualizarCamaraRequest body) {
        var dados = new AtualizarCamaraDTO(body.nome(), body.unidade(), body.capacidade(),
            body.temperaturaMinima(), body.temperaturaMaxima(), body.estado());
        return CamaraResponse.de(atualizarCamara.atualizar(id, dados));
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Inativar câmara",
        description = "Remoção lógica: a câmara passa a `ativo=false`, continua consultável e seu código continua "
            + "reservado. Só é permitida sem lotes ativos; repetir a chamada numa câmara já inativa também "
            + "responde 204.")
    @APIResponse(responseCode = "204", description = "Câmara inativada, ou já inativa.")
    @APIResponse(responseCode = "404", ref = DocumentacaoApi.NAO_ENCONTRADO)
    @APIResponse(responseCode = "409", description = "A câmara possui lotes ativos; inative-os ou mova-os antes "
        + "(`/problemas/estado-incompativel`).",
        content = @Content(mediaType = DocumentacaoApi.PROBLEM_JSON,
            schema = @Schema(implementation = ProblemDetails.class)))
    public Response inativar(@Parameter(description = "Identificador técnico da câmara.", example = "1")
                             @PathParam("id") long id) {
        inativarCamara.inativar(id);
        return Response.noContent().build();
    }
}

package br.ufrn.friovax.api.lote.api;

import br.ufrn.friovax.api.compartilhado.api.DocumentacaoApi;
import br.ufrn.friovax.api.compartilhado.api.PaginaResponse;
import br.ufrn.friovax.api.compartilhado.api.ProblemDetails;
import br.ufrn.friovax.api.compartilhado.dominio.Paginacao;
import br.ufrn.friovax.api.lote.aplicacao.CadastrarLote;
import br.ufrn.friovax.api.lote.aplicacao.AtualizarLote;
import br.ufrn.friovax.api.lote.aplicacao.ConsultarLote;
import br.ufrn.friovax.api.lote.aplicacao.InativarLote;
import br.ufrn.friovax.api.lote.dominio.EstadoLote;
import br.ufrn.friovax.api.lote.dominio.LoteFiltro;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
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

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Path("/api/lotes")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = DocumentacaoApi.TAG_LOTES)
@APIResponse(responseCode = "500", ref = DocumentacaoApi.ERRO_INTERNO)
public class LoteResource {

    @Context
    UriInfo uriInfo;

    private final CadastrarLote cadastrarLote;
    private final ConsultarLote consultarLote;
    private final InativarLote inativarLote;
    private final AtualizarLote atualizarLote;

    @Inject
    public LoteResource(CadastrarLote cadastrarLote, ConsultarLote consultarLote, InativarLote inativarLote,
                        AtualizarLote atualizarLote) {
        this.cadastrarLote = cadastrarLote;
        this.consultarLote = consultarLote;
        this.inativarLote = inativarLote;
        this.atualizarLote = atualizarLote;
    }

    @POST
    @Operation(summary = "Cadastrar lote",
        description = "Cria um lote `DISPONIVEL` e ativo numa câmara ativa e `OPERACIONAL`, desde que a ocupação "
            + "da câmara somada à `quantidade` não ultrapasse a capacidade.")
    @APIResponse(responseCode = "201", description = "Lote criado.",
        headers = @Header(name = "Location", description = "Endereço do lote criado.",
            schema = @Schema(type = SchemaType.STRING, format = "uri")),
        content = @Content(mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = LoteResponse.class)))
    @APIResponse(responseCode = "400", ref = DocumentacaoApi.REQUISICAO_INVALIDA)
    @APIResponse(responseCode = "404", description = "A câmara informada em `camaraId` não existe "
        + "(`/problemas/recurso-nao-encontrado`).",
        content = @Content(mediaType = DocumentacaoApi.PROBLEM_JSON,
            schema = @Schema(implementation = ProblemDetails.class)))
    @APIResponse(responseCode = "409", description = "Código já usado por outro lote, inclusive inativo "
        + "(`/problemas/codigo-duplicado`); câmara inativa ou fora de `OPERACIONAL` "
        + "(`/problemas/estado-incompativel`); ou capacidade da câmara excedida (`/problemas/capacidade-excedida`).",
        content = @Content(mediaType = DocumentacaoApi.PROBLEM_JSON,
            schema = @Schema(implementation = ProblemDetails.class)))
    @APIResponse(responseCode = "422", ref = DocumentacaoApi.DADOS_INVALIDOS)
    public Response cadastrar(@NotNull(message = "deve ser informado") @Valid LoteRequest body) {
        var lote = cadastrarLote.cadastrar(body.codigo(), body.imunobiologico(), body.fabricante(), body.validade(),
                body.quantidade(), body.camaraId());
        var location = uriInfo.getAbsolutePathBuilder().path(Long.toString(lote.getId())).build();
        return Response.created(location).entity(LoteResponse.de(lote)).build();
    }

    @GET
    @Operation(summary = "Listar lotes",
        description = "Lista lotes paginados por `id` crescente. Filtros combinados usam E lógico; sem `ativo`, só "
            + "aparecem lotes ativos. O intervalo de validade é inclusivo e pode ter só um dos limites.")
    @APIResponse(responseCode = "200", description = "Página de lotes; `items` vem vazio além da última página ou "
        + "quando `camaraId` não existe.")
    @APIResponse(responseCode = "400", ref = DocumentacaoApi.REQUISICAO_INVALIDA,
        description = "Parâmetro com tipo inválido ou vazio, `page`/`size` fora do intervalo ou `validadeDe` "
            + "posterior a `validadeAte`.")
    public PaginaResponse<LoteResponse> listar(
            @Parameter(description = "Trecho do nome do imunobiológico, sem diferenciar maiúsculas de minúsculas.",
                example = "febre")
            @QueryParam("imunobiologico") String imunobiologico,
            @Parameter(description = "Validade mínima, inclusiva (`validade >= validadeDe`).",
                schema = @Schema(type = SchemaType.STRING, format = "date"), example = "2027-01-01")
            @QueryParam("validadeDe") String validadeDe,
            @Parameter(description = "Validade máxima, inclusiva (`validade <= validadeAte`).",
                schema = @Schema(type = SchemaType.STRING, format = "date"), example = "2027-12-31")
            @QueryParam("validadeAte") String validadeAte,
            @Parameter(description = "Câmara do lote. Uma câmara inexistente não é erro: a página volta vazia.",
                schema = @Schema(type = SchemaType.INTEGER, format = "int64"))
            @QueryParam("camaraId") String camaraId,
            @Parameter(description = "Estado do lote. Vazio equivale a ausente.",
                schema = @Schema(implementation = EstadoLote.class))
            @QueryParam("estado") String estado,
            @Parameter(description = "`true` lista só lotes ativos; `false`, só inativos.",
                schema = @Schema(type = SchemaType.BOOLEAN, defaultValue = "true"))
            @QueryParam("ativo") String ativo,
            @Parameter(description = "Número da página, a partir de 0.",
                schema = @Schema(type = SchemaType.INTEGER, format = "int32", minimum = "0", defaultValue = "0"))
            @QueryParam("page") String page,
            @Parameter(description = "Itens por página.", schema = @Schema(type = SchemaType.INTEGER,
                format = "int32", minimum = "1", maximum = "100", defaultValue = "20"))
            @QueryParam("size") String size) {
        LoteFiltro filtro;
        Paginacao paginacao;
        try {
            if (uriInfo != null) {
                for (String parametro : new String[]{"validadeDe", "validadeAte", "camaraId", "ativo", "page", "size"}) {
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
            filtro = new LoteFiltro(imunobiologico,
                    validadeDe == null ? null : LocalDate.parse(validadeDe),
                    validadeAte == null ? null : LocalDate.parse(validadeAte),
                    camaraId == null ? null : Long.parseLong(camaraId),
                    estado == null || estado.trim().isEmpty() ? null : EstadoLote.valueOf(estado.trim()),
                    Boolean.parseBoolean(ativo));
            paginacao = new Paginacao(page == null ? Paginacao.PAGINA_PADRAO : Integer.parseInt(page),
                    size == null ? Paginacao.TAMANHO_PADRAO : Integer.parseInt(size));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new BadRequestException("Parâmetros de consulta inválidos.", e);
        }
        return PaginaResponse.de(consultarLote.listar(filtro, paginacao).map(LoteResponse::de));
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Consultar lote", description = "Retorna o lote, mesmo inativo.")
    @APIResponse(responseCode = "200", description = "Lote encontrado.")
    @APIResponse(responseCode = "404", ref = DocumentacaoApi.NAO_ENCONTRADO)
    public LoteResponse consultar(@Parameter(description = "Identificador técnico do lote.", example = "10")
                                  @PathParam("id") long id) {
        return LoteResponse.de(consultarLote.consultar(id));
    }

    @PUT
    @Path("/{id}")
    @Operation(summary = "Atualizar lote",
        description = "Substitui todos os campos editáveis; todos são obrigatórios e o `codigo` não muda. A "
            + "quantidade pode aumentar, nunca diminuir: reduções passam pela baixa com motivo. Trocar `camaraId` "
            + "move o lote; a câmara de destino é revalidada sem contar o próprio lote, na mesma transação. Uma "
            + "rejeição não altera o lote.")
    @APIResponse(responseCode = "200", description = "Lote atualizado.")
    @APIResponse(responseCode = "400", ref = DocumentacaoApi.REQUISICAO_INVALIDA)
    @APIResponse(responseCode = "404", description = "O lote da rota ou a câmara informada em `camaraId` não "
        + "existe (`/problemas/recurso-nao-encontrado`).",
        content = @Content(mediaType = DocumentacaoApi.PROBLEM_JSON,
            schema = @Schema(implementation = ProblemDetails.class)))
    @APIResponse(responseCode = "409", description = "Lote inativo, lote `ESGOTADO` ou `DESCARTADO` recebendo "
        + "novas doses, ou câmara de destino inativa ou fora de `OPERACIONAL` (`/problemas/estado-incompativel`); "
        + "capacidade da câmara de destino excedida (`/problemas/capacidade-excedida`).",
        content = @Content(mediaType = DocumentacaoApi.PROBLEM_JSON,
            schema = @Schema(implementation = ProblemDetails.class)))
    @APIResponse(responseCode = "422", ref = DocumentacaoApi.DADOS_INVALIDOS,
        description = "Campo ausente ou inválido, redução de quantidade ou nova validade que não seja futura.")
    public LoteResponse atualizar(@Parameter(description = "Identificador técnico do lote.", example = "10")
                                  @PathParam("id") long id,
                                  @NotNull(message = "deve ser informado") @Valid AtualizarLoteRequest body) {
        return LoteResponse.de(atualizarLote.atualizar(id, body.imunobiologico(), body.fabricante(),
                body.validade(), body.quantidade(), body.camaraId()));
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Inativar lote",
        description = "Remoção lógica: o lote passa a `ativo=false`, continua consultável e seu código continua "
            + "reservado. A quantidade deixa de contar na ocupação da câmara. Repetir a chamada também responde 204.")
    @APIResponse(responseCode = "204", description = "Lote inativado, ou já inativo.")
    @APIResponse(responseCode = "404", ref = DocumentacaoApi.NAO_ENCONTRADO)
    public Response inativar(@Parameter(description = "Identificador técnico do lote.", example = "10")
                             @PathParam("id") long id) {
        inativarLote.inativar(id);
        return Response.noContent().build();
    }
}

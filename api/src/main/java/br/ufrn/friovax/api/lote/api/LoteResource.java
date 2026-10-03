package br.ufrn.friovax.api.lote.api;

import br.ufrn.friovax.api.compartilhado.api.PaginaResponse;
import br.ufrn.friovax.api.compartilhado.dominio.Paginacao;
import br.ufrn.friovax.api.lote.aplicacao.CadastrarLote;
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
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Path("/api/lotes")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class LoteResource {

    @Context
    UriInfo uriInfo;

    private final CadastrarLote cadastrarLote;
    private final ConsultarLote consultarLote;
    private final InativarLote inativarLote;

    @Inject
    public LoteResource(CadastrarLote cadastrarLote, ConsultarLote consultarLote, InativarLote inativarLote) {
        this.cadastrarLote = cadastrarLote;
        this.consultarLote = consultarLote;
        this.inativarLote = inativarLote;
    }

    @POST
    public Response cadastrar(@NotNull(message = "deve ser informado") @Valid LoteRequest body) {
        var lote = cadastrarLote.cadastrar(body.codigo(), body.imunobiologico(), body.fabricante(), body.validade(),
                body.quantidade(), body.camaraId());
        var location = uriInfo.getAbsolutePathBuilder().path(Long.toString(lote.getId())).build();
        return Response.created(location).entity(LoteResponse.de(lote)).build();
    }

    @GET
    public PaginaResponse<LoteResponse> listar(@QueryParam("imunobiologico") String imunobiologico,
                                             @QueryParam("validadeDe") String validadeDe,
                                             @QueryParam("validadeAte") String validadeAte,
                                             @QueryParam("camaraId") String camaraId,
                                             @QueryParam("estado") String estado,
                                             @QueryParam("ativo") String ativo,
                                             @QueryParam("page") String page,
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
    public LoteResponse consultar(@PathParam("id") long id) {
        return LoteResponse.de(consultarLote.consultar(id));
    }

    @DELETE
    @Path("/{id}")
    public Response inativar(@PathParam("id") long id) {
        inativarLote.inativar(id);
        return Response.noContent().build();
    }
}

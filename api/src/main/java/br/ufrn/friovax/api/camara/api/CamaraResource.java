package br.ufrn.friovax.api.camara.api;

import br.ufrn.friovax.api.camara.aplicacao.AtualizarCamara;
import br.ufrn.friovax.api.camara.aplicacao.AtualizarCamaraDTO;
import br.ufrn.friovax.api.camara.aplicacao.CadastrarCamaraDTO;
import br.ufrn.friovax.api.camara.aplicacao.CamaraService;
import br.ufrn.friovax.api.camara.aplicacao.ConsultarCamara;
import br.ufrn.friovax.api.camara.aplicacao.InativarCamara;
import br.ufrn.friovax.api.camara.dominio.CamaraFiltro;
import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
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

@Path("/api/camaras")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
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
    public PaginaResponse<CamaraResponse> listar(@QueryParam("unidade") String unidade,
                                               @QueryParam("estado") String estado,
                                               @QueryParam("ativo") String ativo,
                                               @QueryParam("page") String page,
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
    public CamaraResponse consultar(@PathParam("id") long id) {
        return CamaraResponse.de(consultarCamara.consultar(id));
    }

    @PUT
    @Path("/{id}")
    public CamaraResponse atualizar(@PathParam("id") long id,
                                    @NotNull(message = "deve ser informado") @Valid AtualizarCamaraRequest body) {
        var dados = new AtualizarCamaraDTO(body.nome(), body.unidade(), body.capacidade(),
            body.temperaturaMinima(), body.temperaturaMaxima(), body.estado());
        return CamaraResponse.de(atualizarCamara.atualizar(id, dados));
    }

    @DELETE
    @Path("/{id}")
    public Response inativar(@PathParam("id") long id) {
        inativarCamara.inativar(id);
        return Response.noContent().build();
    }
}

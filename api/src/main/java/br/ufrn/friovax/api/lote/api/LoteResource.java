package br.ufrn.friovax.api.lote.api;

import br.ufrn.friovax.api.lote.aplicacao.ConsultarLote;
import br.ufrn.friovax.api.lote.aplicacao.InativarLote;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/lotes")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class LoteResource {

    private final ConsultarLote consultarLote;
    private final InativarLote inativarLote;

    @Inject
    public LoteResource(ConsultarLote consultarLote, InativarLote inativarLote) {
        this.consultarLote = consultarLote;
        this.inativarLote = inativarLote;
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

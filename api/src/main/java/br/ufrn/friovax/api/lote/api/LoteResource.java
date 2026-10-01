package br.ufrn.friovax.api.lote.api;

import br.ufrn.friovax.api.lote.aplicacao.ConsultarLote;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/lotes")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class LoteResource {

    private final ConsultarLote consultarLote;

    @Inject
    public LoteResource(ConsultarLote consultarLote) {
        this.consultarLote = consultarLote;
    }

    @GET
    @Path("/{id}")
    public LoteResponse consultar(@PathParam("id") long id) {
        return LoteResponse.de(consultarLote.consultar(id));
    }
}

package br.ufrn.friovax.api.lote.api;

import br.ufrn.friovax.api.lote.aplicacao.InativarLote;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;

@Path("/api/lotes")
public class LoteResource {
    private final InativarLote inativarLote;

    @Inject
    public LoteResource(InativarLote inativarLote) {
        this.inativarLote = inativarLote;
    }

    @DELETE
    @Path("/{id}")
    public Response inativar(@PathParam("id") long id) {
        inativarLote.inativar(id);
        return Response.noContent().build();
    }
}

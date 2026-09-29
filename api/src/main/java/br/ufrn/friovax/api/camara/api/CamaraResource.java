package br.ufrn.friovax.api.camara.api;

import br.ufrn.friovax.api.camara.aplicacao.ConsultarCamara;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/camaras")
@Produces(MediaType.APPLICATION_JSON)
public class CamaraResource {
    private final ConsultarCamara consultarCamara;

    @Inject
    public CamaraResource(ConsultarCamara consultarCamara) {
        this.consultarCamara = consultarCamara;
    }

    @GET
    @Path("/{id}")
    public CamaraResponse consultar(@PathParam("id") long id) {
        return CamaraResponse.de(consultarCamara.consultar(id));
    }
}

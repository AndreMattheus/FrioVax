package br.ufrn.friovax.api.camara.api;

import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/testes/camaras")
public class CamaraValidacaoTestResource {

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response criar(@Valid CamaraRequest request) {
        return Response.ok().build();
    }
}

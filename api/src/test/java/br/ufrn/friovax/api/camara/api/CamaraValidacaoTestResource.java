package br.ufrn.friovax.api.camara.api;

import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.compartilhado.dominio.CodigoDuplicado;
import br.ufrn.friovax.api.compartilhado.dominio.RecursoNaoEncontrado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.OffsetDateTime;
import java.util.Map;

@Path("/testes/camaras")
@Produces(MediaType.APPLICATION_JSON)
public class CamaraValidacaoTestResource {

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response criar(@NotNull(message = "deve ser informado") @Valid CamaraRequest corpo) {
        var camara = Camara.nova(corpo.codigo(), corpo.nome(), corpo.unidade(), corpo.capacidade(),
                corpo.temperaturaMinima(), corpo.temperaturaMaxima(), corpo.estado(),
                OffsetDateTime.parse("2026-09-29T09:00:00-03:00"));

        return Response.ok(Map.of("codigo", camara.getCodigo(), "nome", camara.getNome(),
                "unidade", camara.getUnidade())).build();
    }

    @GET
    @Path("/codigo-duplicado")
    public Response codigoDuplicado() {
        throw new CodigoDuplicado("câmara", "CAM-01");
    }

    @GET
    @Path("/nao-encontrada")
    public Response naoEncontrada() {
        throw new RecursoNaoEncontrado("Câmara", 99);
    }
}

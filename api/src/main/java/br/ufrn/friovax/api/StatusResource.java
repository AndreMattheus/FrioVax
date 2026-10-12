package br.ufrn.friovax.api;

import br.ufrn.friovax.api.compartilhado.api.DocumentacaoApi;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/status")
@Tag(name = DocumentacaoApi.TAG_STATUS)
public class StatusResource {

    @GET
    @Operation(summary = "Consultar status", description = "Indica que a API está no ar. Não consulta o banco.")
    @Produces(MediaType.APPLICATION_JSON)
    public StatusResponse status() {
        return new StatusResponse("friovax-api", "UP");
    }

    public record StatusResponse(String service, String status) {
    }
}

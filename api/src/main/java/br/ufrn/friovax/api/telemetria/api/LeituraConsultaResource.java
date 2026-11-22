package br.ufrn.friovax.api.telemetria.api;

import br.ufrn.friovax.api.compartilhado.api.PaginaResponse;
import br.ufrn.friovax.api.compartilhado.api.ProblemDetails;
import br.ufrn.friovax.api.telemetria.aplicacao.ConsultarHistorico;
import io.quarkus.security.PermissionsAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;

import static br.ufrn.friovax.api.autenticacao.dominio.MatrizAcesso.CONSULTAR_OPERACAO;

@Path("/api/camaras/{id}/leituras")
@Produces(MediaType.APPLICATION_JSON)
@SecurityRequirement(name = "bearerAuth")
@APIResponse(responseCode = "400", description = "Parâmetros de consulta inválidos.",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetails.class)))
@APIResponse(responseCode = "401", description = "Bearer ausente, inválido ou expirado.",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetails.class)))
@APIResponse(responseCode = "403", description = "Perfil sem permissão.",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetails.class)))
public class LeituraConsultaResource {
    @Inject ConsultarHistorico historico;
    @Context UriInfo uriInfo;

    @GET
    @PermissionsAllowed(CONSULTAR_OPERACAO)
    @Operation(summary = "Consultar leituras da câmara",
            description = "ADMINISTRADOR, OPERADOR ou AUDITOR. Intervalo [de, ate) sobre instante da medição. "
                    + "Ordem instante DESC, id DESC. Inclui histórico de câmaras inativas e funciona sem Go.")
    @APIResponse(responseCode = "200", description = "Página de leituras; além do fim, items vazio.")
    @APIResponse(responseCode = "404", description = "Câmara inexistente.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetails.class)))
    public PaginaResponse<LeituraHistoricaResponse> listar(@PathParam("id") long id, @BeanParam ConsultaHistoricoQuery query) {
        query.validarParametros(uriInfo);
        return PaginaResponse.de(historico.listarLeituras(id, query.periodo(), query.paginacao()).map(LeituraHistoricaResponse::de));
    }
}

package br.ufrn.friovax.api.telemetria.api;

import br.ufrn.friovax.api.compartilhado.api.PaginaResponse;
import br.ufrn.friovax.api.compartilhado.api.ProblemDetails;
import br.ufrn.friovax.api.telemetria.aplicacao.ConsultarHistorico;
import br.ufrn.friovax.api.telemetria.dominio.EstadoOcorrencia;
import br.ufrn.friovax.api.telemetria.dominio.OcorrenciaFiltro;
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

@Path("/api/ocorrencias")
@Produces(MediaType.APPLICATION_JSON)
@SecurityRequirement(name = "bearerAuth")
@APIResponse(responseCode = "400", description = "Parâmetros de consulta inválidos.",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetails.class)))
@APIResponse(responseCode = "401", description = "Bearer ausente, inválido ou expirado.",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetails.class)))
@APIResponse(responseCode = "403", description = "Perfil sem permissão.",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetails.class)))
public class OcorrenciaConsultaResource {
    @Inject ConsultarHistorico historico;
    @Context UriInfo uriInfo;

    @GET
    @PermissionsAllowed(CONSULTAR_OPERACAO)
    @Operation(summary = "Consultar ocorrências",
            description = "ADMINISTRADOR, OPERADOR ou AUDITOR. Filtros combinados por E; [de, ate) sobre abertura. "
                    + "Ordem abertaEm DESC, id DESC. Sem camaraId/estado, inclui todas as câmaras/estados. "
                    + "Câmara inexistente no filtro retorna página vazia; funciona sem Go.")
    @APIResponse(responseCode = "200", description = "Página de resumos; além do fim, items vazio.")
    public PaginaResponse<OcorrenciaResponse> listar(@BeanParam ConsultaHistoricoQuery query,
            @Parameter(description = "ID positivo da câmara; opcional.") @QueryParam("camaraId") String camaraId,
            @Parameter(description = "ABERTA ou RESOLVIDA; opcional.") @QueryParam("estado") String estado) {
        query.validarParametros(uriInfo, "camaraId", "estado");
        OcorrenciaFiltro filtro;
        try {
            filtro = new OcorrenciaFiltro(camaraId == null ? null : Long.parseLong(camaraId),
                    estado == null ? null : EstadoOcorrencia.valueOf(estado), query.periodo());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Filtros inválidos: camaraId positivo e estado ABERTA ou RESOLVIDA.", e);
        }
        return PaginaResponse.de(historico.listarOcorrencias(filtro, query.paginacao()).map(OcorrenciaResponse::de));
    }

    @GET
    @Path("/{id}")
    @PermissionsAllowed(CONSULTAR_OPERACAO)
    @Operation(summary = "Consultar detalhe da ocorrência",
            description = "ADMINISTRADOR, OPERADOR ou AUDITOR. Política original, evidências ordenadas, "
                    + "snapshot dos lotes e tratamento persistido; sem reanálise ou dependência de Go.")
    @APIResponse(responseCode = "200", description = "Detalhe da ocorrência.")
    @APIResponse(responseCode = "404", description = "Ocorrência inexistente.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetails.class)))
    public OcorrenciaDetalheResponse consultar(@PathParam("id") long id) {
        return OcorrenciaDetalheResponse.de(historico.consultarOcorrencia(id));
    }
}

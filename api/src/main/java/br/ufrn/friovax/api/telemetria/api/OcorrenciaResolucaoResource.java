package br.ufrn.friovax.api.telemetria.api;

import br.ufrn.friovax.api.compartilhado.api.DocumentacaoApi;
import br.ufrn.friovax.api.compartilhado.api.ProblemDetails;
import br.ufrn.friovax.api.telemetria.aplicacao.ConsultarHistorico;
import br.ufrn.friovax.api.telemetria.aplicacao.ResolverOcorrencia;
import io.quarkus.security.PermissionsAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;

import static br.ufrn.friovax.api.autenticacao.dominio.MatrizAcesso.RESOLVER_INCIDENTE;

@Path("/api/ocorrencias/{id}/resolucao")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@SecurityRequirement(name = "bearerAuth")
public class OcorrenciaResolucaoResource {

    private final ResolverOcorrencia resolver;
    private final ConsultarHistorico historico;

    @Inject
    public OcorrenciaResolucaoResource(ResolverOcorrencia resolver, ConsultarHistorico historico) {
        this.resolver = resolver;
        this.historico = historico;
    }

    @POST
    @PermissionsAllowed(RESOLVER_INCIDENTE)
    @Operation(summary = "Resolver ocorrência",
        description = "Somente ADMINISTRADOR. Encerra a ocorrência aberta com a justificativa do body; autor "
            + "(`resolvidaPor`) e instante (`resolvidaEm`) vêm do token e do relógio do servidor. Com a câmara "
            + "bloqueada, exige que a última leitura aceita seja segura, posterior à abertura e da política vigente: "
            + "uma segura antiga seguida de insegura não basta. Resolução, estado térmico `NORMAL` e trilha de "
            + "auditoria são gravados juntos. Reenviar a mesma justificativa responde 200 com o tratamento original, "
            + "sem novo evento. A próxima excursão começa com a sequência zerada e abre outra ocorrência.")
    @APIResponse(responseCode = "200", description = "Detalhe da ocorrência resolvida (nova resolução ou reenvio "
        + "idêntico).")
    @APIResponse(responseCode = "400", ref = DocumentacaoApi.REQUISICAO_INVALIDA)
    @APIResponse(responseCode = "401", description = "Bearer ausente, inválido ou expirado.")
    @APIResponse(responseCode = "403", description = "Perfil sem permissão para esta operação.")
    @APIResponse(responseCode = "404", ref = DocumentacaoApi.NAO_ENCONTRADO)
    @APIResponse(responseCode = "409", description = "Última leitura insegura, anterior à abertura ou de outra "
        + "política (`/problemas/resolucao-nao-permitida`), ou ocorrência já resolvida com outra justificativa "
        + "(`/problemas/resolucao-conflitante`). Nada é gravado.",
        content = @Content(mediaType = DocumentacaoApi.PROBLEM_JSON,
            schema = @Schema(implementation = ProblemDetails.class)))
    @APIResponse(responseCode = "422", ref = DocumentacaoApi.DADOS_INVALIDOS)
    @APIResponse(responseCode = "500", ref = DocumentacaoApi.ERRO_INTERNO)
    public OcorrenciaDetalheResponse resolver(
        @Parameter(description = "Identificador da ocorrência.", example = "1") @PathParam("id") long id,
        @NotNull(message = "deve ser informado") @Valid ResolucaoRequest body) {
        resolver.resolver(id, body.justificativa());
        return OcorrenciaDetalheResponse.de(historico.consultarOcorrencia(id));
    }
}

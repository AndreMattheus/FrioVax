package br.ufrn.friovax.api.resumo.api;

import br.ufrn.friovax.api.compartilhado.api.DocumentacaoApi;
import br.ufrn.friovax.api.resumo.aplicacao.ConsultarResumo;
import br.ufrn.friovax.api.resumo.aplicacao.Resumo;
import io.quarkus.security.PermissionsAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import static br.ufrn.friovax.api.autenticacao.dominio.MatrizAcesso.CONSULTAR_OPERACAO;

@Path("/api/resumo")
@Produces(MediaType.APPLICATION_JSON)
@SecurityRequirement(name = "bearerAuth")
@APIResponse(responseCode = "500", ref = DocumentacaoApi.ERRO_INTERNO)
public class ResumoResource {
    @Inject ConsultarResumo resumo;

    @GET
    @PermissionsAllowed(CONSULTAR_OPERACAO)
    @Operation(summary = "Consultar resumo operacional",
            description = "ADMINISTRADOR, OPERADOR ou AUDITOR. Cache local único; TTL padrão de 30 segundos. "
                    + "Câmaras e estoque ativos; episódios abertos e lotes distintos afetados nesses episódios. "
                    + "Estado térmico persistido, sem consultar Go. Validade é a expiração do cache.")
    @APIResponse(responseCode = "200", description = "Totais persistidos e atualidade do cache.",
            content = @Content(schema = @Schema(implementation = Resumo.class)))
    @APIResponse(responseCode = "401", description = "Bearer ausente, inválido ou expirado.")
    @APIResponse(responseCode = "403", description = "Perfil sem permissão para esta operação.")
    public Resumo consultar() {
        return resumo.consultar();
    }
}

package br.ufrn.friovax.api.autenticacao.api;

import br.ufrn.friovax.api.autenticacao.aplicacao.AutenticacaoService;
import br.ufrn.friovax.api.autenticacao.dominio.AutenticacaoInvalida;
import br.ufrn.friovax.api.autenticacao.dominio.Identidade;
import br.ufrn.friovax.api.autenticacao.dominio.Perfil;
import io.quarkus.security.Authenticated;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import java.util.UUID;

@Path("/api/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Autenticação")
@SecurityScheme(securitySchemeName = "bearerAuth", type = SecuritySchemeType.HTTP,
        scheme = "bearer", bearerFormat = "JWT")
@APIResponse(responseCode = "401", description = "Credenciais, sessão ou token inválidos/expirados. "
        + "Login/renovação/logout retornam Problem Details com mensagem genérica; a rejeição de Bearer "
        + "é realizada pelo mecanismo de segurança do Quarkus.")
public class AutenticacaoResource {
    @Inject AutenticacaoService service;
    @Inject JsonWebToken jwt;

    @POST @Path("/login") @PermitAll
    @Operation(summary = "Autenticar usuário", description = "Emite JWT RS256 de 15 minutos e refresh opaco de "
            + "24 horas. Credenciais inválidas sempre recebem a mesma resposta 401.")
    @APIResponse(responseCode = "200", description = "Sessão criada.",
            content = @Content(schema = @Schema(implementation = SessaoResponse.class)))
    public Response login(LoginRequest body) {
        if (body == null) throw new AutenticacaoInvalida();
        return sessao(SessaoResponse.de(service.login(body.login(), body.senha())));
    }

    @POST @Path("/renovar") @PermitAll
    @Operation(summary = "Renovar sessão", description = "Consome o refresh uma única vez e emite novo par. "
            + "Refresh expirado, revogado ou já usado recebe 401, inclusive em chamadas concorrentes.")
    @APIResponse(responseCode = "200", description = "Refresh anterior revogado; novo par emitido.",
            content = @Content(schema = @Schema(implementation = SessaoResponse.class)))
    public Response renovar(RefreshRequest body) {
        if (body == null) throw new AutenticacaoInvalida();
        return sessao(SessaoResponse.de(service.renovar(body.refreshToken())));
    }

    @POST @Path("/logout") @PermitAll
    @Operation(summary = "Revogar refresh", description = "Revoga somente o refresh enviado. O JWT de acesso "
            + "já emitido continua válido até expirar, por no máximo 15 minutos; a validação do access não "
            + "consulta o banco. Para sair após renovar, envie o refresh mais recente.")
    @APIResponse(responseCode = "204", description = "Refresh revogado.")
    public Response logout(RefreshRequest body) {
        if (body == null) throw new AutenticacaoInvalida();
        service.logout(body.refreshToken());
        return Response.noContent().header("Cache-Control", "no-store").build();
    }

    @GET @Path("/identidade") @Authenticated
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Consultar identidade autenticada", description = "Deriva id interno (sub), login "
            + "(upn) e perfil (groups) apenas do JWT validado pelo Quarkus, sem consulta ao banco.")
    @APIResponse(responseCode = "200", description = "Identidade interna autenticada.",
            content = @Content(schema = @Schema(implementation = Identidade.class)))
    public Response identidade() {
        return sessao(new Identidade(UUID.fromString(jwt.getSubject()), jwt.getName(),
                Perfil.valueOf(jwt.getGroups().iterator().next())));
    }

    private Response sessao(Object body) {
        return Response.ok(body).header("Cache-Control", "no-store").header("Pragma", "no-cache").build();
    }
}

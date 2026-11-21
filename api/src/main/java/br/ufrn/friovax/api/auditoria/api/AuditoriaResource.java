package br.ufrn.friovax.api.auditoria.api;

import br.ufrn.friovax.api.auditoria.aplicacao.ConsultarAuditoria;
import br.ufrn.friovax.api.auditoria.dominio.FiltroAuditoria;
import br.ufrn.friovax.api.auditoria.dominio.RecursoAuditado;
import br.ufrn.friovax.api.compartilhado.api.DocumentacaoApi;
import br.ufrn.friovax.api.compartilhado.api.PaginaResponse;
import br.ufrn.friovax.api.compartilhado.dominio.Paginacao;
import io.quarkus.security.PermissionsAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.regex.Pattern;

import static br.ufrn.friovax.api.autenticacao.dominio.MatrizAcesso.CONSULTAR_TRILHA;

@Path("/api/auditoria")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = DocumentacaoApi.TAG_AUDITORIA)
@SecurityRequirement(name = "bearerAuth")
@APIResponse(responseCode = "401", description = "Bearer ausente, inválido ou expirado.")
@APIResponse(responseCode = "403", description = "Perfil sem permissão para esta operação.")
@APIResponse(responseCode = "500", ref = DocumentacaoApi.ERRO_INTERNO)
public class AuditoriaResource {
    private static final Pattern UUID_CANONICO = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    @Context
    UriInfo uriInfo;

    private final ConsultarAuditoria consultarAuditoria;

    @Inject
    public AuditoriaResource(ConsultarAuditoria consultarAuditoria) {
        this.consultarAuditoria = consultarAuditoria;
    }

    @GET
    @PermissionsAllowed(CONSULTAR_TRILHA)
    @Operation(summary = "Consultar trilha de auditoria",
        description = "ADMINISTRADOR ou AUDITOR. "
            + "Lista as alterações confirmadas de câmaras e lotes, paginadas por `id` crescente (ordem de gravação). "
            + "Filtros combinados usam E lógico; filtro vazio equivale a ausente. Consultar a trilha não gera "
            + "evento, e a trilha não pode ser alterada pela API.")
    @APIResponse(responseCode = "200", description = "Página de eventos; `items` vem vazio além da última página.")
    @APIResponse(responseCode = "400", ref = DocumentacaoApi.REQUISICAO_INVALIDA)
    public PaginaResponse<EventoDeAuditoriaResponse> listar(
        @Parameter(description = "Início do período, inclusivo, com offset (`Z` ou `-03:00`).",
            example = "2026-11-01T00:00:00-03:00",
            schema = @Schema(type = SchemaType.STRING, format = "date-time"))
        @QueryParam("de") String de,
        @Parameter(description = "Fim do período, inclusivo, com offset. Não pode ser anterior a `de`.",
            example = "2026-11-30T23:59:59-03:00",
            schema = @Schema(type = SchemaType.STRING, format = "date-time"))
        @QueryParam("ate") String ate,
        @Parameter(description = "UUID do usuário que fez a alteração (o `id` do ator).",
            schema = @Schema(type = SchemaType.STRING, format = "uuid"))
        @QueryParam("usuarioId") String usuarioId,
        @Parameter(description = "Tipo do recurso alterado.",
            schema = @Schema(implementation = RecursoAuditado.class))
        @QueryParam("recurso") String recurso,
        @Parameter(description = "Identificador técnico do recurso. Exige `recurso`, porque câmaras e lotes têm "
            + "numerações próprias.", schema = @Schema(type = SchemaType.INTEGER, format = "int64", minimum = "1"))
        @QueryParam("recursoId") String recursoId,
        @Parameter(description = "Número da página, a partir de 0.",
            schema = @Schema(type = SchemaType.INTEGER, format = "int32", minimum = "0", defaultValue = "0"))
        @QueryParam("page") String page,
        @Parameter(description = "Itens por página.", schema = @Schema(type = SchemaType.INTEGER,
            format = "int32", minimum = "1", maximum = "100", defaultValue = "20"))
        @QueryParam("size") String size) {
        FiltroAuditoria filtro;
        Paginacao paginacao;
        try {
            // O REST converte parâmetros vazios em null; a URI distingue vazio de ausente.
            if (uriInfo != null) {
                for (String parametro : new String[]{"page", "size"}) {
                    var valores = uriInfo.getQueryParameters().get(parametro);
                    if (valores != null && valores.stream().anyMatch(valor -> valor == null || valor.isBlank())) {
                        throw new IllegalArgumentException(parametro + " deve ser informado");
                    }
                }
            }
            var idDoRecurso = vazio(recursoId) ? null : Long.valueOf(recursoId.trim());
            filtro = new FiltroAuditoria(instante(de), instante(ate), usuario(usuarioId),
                vazio(recurso) ? null : RecursoAuditado.valueOf(recurso.trim()), idDoRecurso);
            paginacao = new Paginacao(page == null ? Paginacao.PAGINA_PADRAO : Integer.parseInt(page),
                size == null ? Paginacao.TAMANHO_PADRAO : Integer.parseInt(size));
        } catch (IllegalArgumentException | DateTimeException e) {
            throw new BadRequestException("Parâmetros de consulta inválidos.", e);
        }
        return PaginaResponse.de(consultarAuditoria.listar(filtro, paginacao).map(EventoDeAuditoriaResponse::de));
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }

    private static OffsetDateTime instante(String valor) {
        // Um "+" sem codificação na URL chega como espaço: "...T10:00:00 03:00" volta a ser "+03:00".
        return vazio(valor) ? null : OffsetDateTime.parse(valor.trim().replace(' ', '+'));
    }

    private static UUID usuario(String valor) {
        if (vazio(valor)) {
            return null;
        }
        if (!UUID_CANONICO.matcher(valor.trim()).matches()) {
            throw new IllegalArgumentException("usuarioId deve ser um UUID");
        }
        return UUID.fromString(valor.trim());
    }
}

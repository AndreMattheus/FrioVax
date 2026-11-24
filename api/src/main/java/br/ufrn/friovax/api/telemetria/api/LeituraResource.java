package br.ufrn.friovax.api.telemetria.api;

import br.ufrn.friovax.api.compartilhado.api.DocumentacaoApi;
import br.ufrn.friovax.api.compartilhado.api.ProblemDetails;
import br.ufrn.friovax.api.telemetria.aplicacao.ConsultarLeitura;
import br.ufrn.friovax.api.telemetria.aplicacao.ReceberLeitura;
import io.quarkus.security.PermissionsAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.headers.Header;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import static br.ufrn.friovax.api.autenticacao.dominio.MatrizAcesso.CONSULTAR_OPERACAO;
import static br.ufrn.friovax.api.autenticacao.dominio.MatrizAcesso.REGISTRAR_LEITURA;

@Path("/api/leituras")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = DocumentacaoApi.TAG_LEITURAS)
@SecurityRequirement(name = "bearerAuth")
@APIResponse(responseCode = "401", description = "Bearer ausente, inválido ou expirado.")
@APIResponse(responseCode = "403", description = "Perfil sem permissão para esta operação.")
@APIResponse(responseCode = "500", ref = DocumentacaoApi.ERRO_INTERNO)
public class LeituraResource {

    @Context
    UriInfo uriInfo;

    private final ReceberLeitura receberLeitura;
    private final ConsultarLeitura consultarLeitura;

    @Inject
    public LeituraResource(ReceberLeitura receberLeitura, ConsultarLeitura consultarLeitura) {
        this.receberLeitura = receberLeitura;
        this.consultarLeitura = consultarLeitura;
    }

    @POST
    @PermissionsAllowed(REGISTRAR_LEITURA)
    @Operation(summary = "Receber leitura",
        description = "ADMINISTRADOR ou OPERADOR. "
            + "Analisa a leitura no serviço de telemetria e a grava com a versão da política e o resultado térmico. "
            + "A câmara é bloqueada só durante duas transações curtas, antes e depois da análise; nenhum lock fica "
            + "aberto durante a chamada ao serviço, então alocações na mesma câmara não esperam por ela. Se a câmara "
            + "mudar durante a análise (nova leitura, política ou resolução), a análise é refeita uma única vez. "
            + "O reenvio da mesma chave com conteúdo equivalente (temperatura em escala 1, instante em UTC e origem "
            + "após o `trim`) devolve o registro original com 200, sem nova análise: é seguro reenviar depois de "
            + "perder a resposta. "
            + "A leitura insegura que atinge o limiar N sem ocorrência aberta abre a ocorrência na mesma transação "
            + "(`ocorrenciaAbertaId`), com as evidências e a fotografia dos lotes, e põe a câmara em `EM_ALERTA`; "
            + "leituras seguintes, inclusive seguras, não fecham o alerta.")
    @APIResponse(responseCode = "201", description = "Primeira aceitação da chave.",
        headers = @Header(name = "Location", description = "Endereço da leitura aceita.",
            schema = @Schema(type = SchemaType.STRING, format = "uri")),
        content = @Content(mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = LeituraResponse.class)))
    @APIResponse(responseCode = "200", description = "Chave já aceita com conteúdo equivalente; devolve o registro "
        + "original sem nova análise.",
        content = @Content(mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = LeituraResponse.class)))
    @APIResponse(responseCode = "400", ref = DocumentacaoApi.REQUISICAO_INVALIDA)
    @APIResponse(responseCode = "404", description = "A câmara informada em `camaraId` não existe "
        + "(`/problemas/recurso-nao-encontrado`).",
        content = @Content(mediaType = DocumentacaoApi.PROBLEM_JSON,
            schema = @Schema(implementation = ProblemDetails.class)))
    @APIResponse(responseCode = "409", description = "Chave já aceita com outro conteúdo "
        + "(`/problemas/leitura-conflitante`); `medidaEm` igual ou anterior à última leitura aceita da câmara, ou "
        + "câmara inativa ou fora de `OPERACIONAL` (`/problemas/estado-incompativel`); ou câmara alterada durante "
        + "as duas análises (`/problemas/alteracao-concorrente`, pode reenviar). Nada é gravado.",
        content = @Content(mediaType = DocumentacaoApi.PROBLEM_JSON,
            schema = @Schema(implementation = ProblemDetails.class)))
    @APIResponse(responseCode = "422", ref = DocumentacaoApi.DADOS_INVALIDOS,
        description = "Campo ausente ou inválido, `medidaEm` sem offset ou mais de 5 minutos no futuro.")
    @APIResponse(responseCode = "502", description = "O serviço de análise respondeu fora do contrato ou recusou a "
        + "janela (`/problemas/falha-de-integracao`). Nada é gravado.",
        content = @Content(mediaType = DocumentacaoApi.PROBLEM_JSON,
            schema = @Schema(implementation = ProblemDetails.class)))
    @APIResponse(responseCode = "503", description = "Serviço de análise ausente ou sem resposta dentro do prazo "
        + "de cada chamada (`/problemas/analise-indisponivel`). Nada é gravado; pode reenviar.",
        content = @Content(mediaType = DocumentacaoApi.PROBLEM_JSON,
            schema = @Schema(implementation = ProblemDetails.class)))
    public Response receber(@NotNull(message = "deve ser informado") @Valid LeituraRequest body) {
        var recebida = receberLeitura.receber(body.camaraId(), body.idExterno(), body.temperatura(),
            body.instanteDaMedicao(), body.origem());
        var resposta = LeituraResponse.de(recebida.leitura(), recebida.ocorrenciaAbertaId());
        if (!recebida.criada()) {
            return Response.ok(resposta).build();
        }
        var location = uriInfo.getAbsolutePathBuilder().path(Long.toString(resposta.id())).build();
        return Response.created(location).entity(resposta).build();
    }

    @GET
    @PermissionsAllowed(CONSULTAR_OPERACAO)
    @Path("/{id}")
    @Operation(summary = "Consultar leitura", description = "ADMINISTRADOR, OPERADOR ou AUDITOR. "
        + "Retorna a leitura aceita com a política e o resultado térmico confirmados.")
    @APIResponse(responseCode = "200", description = "Leitura encontrada.")
    @APIResponse(responseCode = "404", ref = DocumentacaoApi.NAO_ENCONTRADO)
    public LeituraResponse consultar(@Parameter(description = "Identificador técnico da leitura.", example = "42")
                                     @PathParam("id") long id) {
        var consultada = consultarLeitura.consultar(id);
        return LeituraResponse.de(consultada.leitura(), consultada.ocorrenciaAbertaId());
    }
}

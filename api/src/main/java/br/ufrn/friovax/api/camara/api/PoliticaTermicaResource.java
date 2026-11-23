package br.ufrn.friovax.api.camara.api;

import br.ufrn.friovax.api.camara.aplicacao.AlterarPoliticaTermica;
import br.ufrn.friovax.api.camara.aplicacao.AlterarPoliticaTermicaDTO;
import br.ufrn.friovax.api.camara.aplicacao.ConsultarPoliticaTermica;
import br.ufrn.friovax.api.compartilhado.api.DocumentacaoApi;
import br.ufrn.friovax.api.compartilhado.api.PaginaResponse;
import br.ufrn.friovax.api.compartilhado.api.ProblemDetails;
import br.ufrn.friovax.api.compartilhado.dominio.Paginacao;
import io.quarkus.security.PermissionsAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import static br.ufrn.friovax.api.autenticacao.dominio.MatrizAcesso.ALTERAR_POLITICA;
import static br.ufrn.friovax.api.autenticacao.dominio.MatrizAcesso.CONSULTAR_OPERACAO;

@Path("/api/camaras/{id}/politica-termica")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = DocumentacaoApi.TAG_CAMARAS)
@SecurityRequirement(name = "bearerAuth")
@APIResponse(responseCode = "401", description = "Bearer ausente, inválido ou expirado.")
@APIResponse(responseCode = "403", description = "Perfil sem permissão para esta operação.")
@APIResponse(responseCode = "404", ref = DocumentacaoApi.NAO_ENCONTRADO)
@APIResponse(responseCode = "500", ref = DocumentacaoApi.ERRO_INTERNO)
public class PoliticaTermicaResource {

    @Context
    UriInfo uriInfo;

    private final ConsultarPoliticaTermica consultar;
    private final AlterarPoliticaTermica alterar;

    @Inject
    public PoliticaTermicaResource(ConsultarPoliticaTermica consultar, AlterarPoliticaTermica alterar) {
        this.consultar = consultar;
        this.alterar = alterar;
    }

    @GET
    @PermissionsAllowed(CONSULTAR_OPERACAO)
    @Operation(summary = "Consultar política térmica vigente",
        description = "ADMINISTRADOR, OPERADOR ou AUDITOR. Retorna a versão vigente, inclusive de câmara inativa. "
            + "Use `versao` como `versaoEsperada` para alterar.")
    @APIResponse(responseCode = "200", description = "Política vigente.")
    public PoliticaTermicaResponse vigente(@Parameter(description = "Identificador técnico da câmara.", example = "1")
                                           @PathParam("id") long id) {
        return PoliticaTermicaResponse.de(id, consultar.vigente(id));
    }

    @PUT
    @PermissionsAllowed(ALTERAR_POLITICA)
    @Operation(summary = "Alterar política térmica",
        description = "Somente ADMINISTRADOR. "
            + "Substitui faixa e limiar sobre a versão informada em `versaoEsperada`. Uma mudança real cria a versão "
            + "seguinte, com o administrador do token como autor, entra na trilha de auditoria e reinicia a "
            + "sequência: a próxima leitura é analisada na versão nova, e o estado térmico volta a `SEM_LEITURAS` "
            + "(exceto em `EM_ALERTA`, porque a ocorrência aberta continua aberta). Valores iguais aos vigentes "
            + "respondem 200 sem criar versão nem evento. Duas alterações sobre a mesma versão não se sobrescrevem: "
            + "a segunda recebe 409.")
    @APIResponse(responseCode = "200", description = "Política vigente depois da chamada (nova ou a mesma).")
    @APIResponse(responseCode = "400", ref = DocumentacaoApi.REQUISICAO_INVALIDA)
    @APIResponse(responseCode = "409", description = "`versaoEsperada` não é a vigente "
        + "(`/problemas/versao-desatualizada`) ou câmara inativa (`/problemas/estado-incompativel`).",
        content = @Content(mediaType = DocumentacaoApi.PROBLEM_JSON,
            schema = @Schema(implementation = ProblemDetails.class)))
    @APIResponse(responseCode = "422", ref = DocumentacaoApi.DADOS_INVALIDOS)
    public PoliticaTermicaResponse alterar(@Parameter(description = "Identificador técnico da câmara.", example = "1")
                                           @PathParam("id") long id,
                                           @NotNull(message = "deve ser informado") @Valid
                                           PoliticaTermicaRequest body) {
        var resultado = alterar.alterar(id, new AlterarPoliticaTermicaDTO(body.versaoEsperada(),
            body.temperaturaMinima(), body.temperaturaMaxima(), body.limiar()));
        return PoliticaTermicaResponse.de(id, resultado.politica());
    }

    @GET
    @Path("/versoes")
    @PermissionsAllowed(CONSULTAR_OPERACAO)
    @Operation(summary = "Listar versões da política térmica",
        description = "ADMINISTRADOR, OPERADOR ou AUDITOR. Histórico paginado, da versão mais recente para a mais "
            + "antiga. Versões anteriores nunca são alteradas nem apagadas, inclusive em câmaras inativas.")
    @APIResponse(responseCode = "200", description = "Página de versões; `items` vem vazio além da última página.")
    @APIResponse(responseCode = "400", ref = DocumentacaoApi.REQUISICAO_INVALIDA)
    public PaginaResponse<PoliticaTermicaResponse> versoes(
        @Parameter(description = "Identificador técnico da câmara.", example = "1") @PathParam("id") long id,
        @Parameter(description = "Número da página, a partir de 0.",
            schema = @Schema(type = SchemaType.INTEGER, format = "int32", minimum = "0", defaultValue = "0"))
        @QueryParam("page") String page,
        @Parameter(description = "Itens por página.", schema = @Schema(type = SchemaType.INTEGER,
            format = "int32", minimum = "1", maximum = "100", defaultValue = "20"))
        @QueryParam("size") String size) {
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
            paginacao = new Paginacao(page == null ? Paginacao.PAGINA_PADRAO : Integer.parseInt(page),
                size == null ? Paginacao.TAMANHO_PADRAO : Integer.parseInt(size));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Parâmetros de consulta inválidos.", e);
        }
        return PaginaResponse.de(consultar.historico(id, paginacao).map(p -> PoliticaTermicaResponse.de(id, p)));
    }
}

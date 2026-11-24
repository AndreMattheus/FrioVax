package br.ufrn.friovax.api.telemetria.api;

import br.ufrn.friovax.api.compartilhado.api.DocumentacaoApi;
import br.ufrn.friovax.api.compartilhado.api.ProblemDetails;
import br.ufrn.friovax.api.compartilhado.dominio.ValidacaoDeNegocio;
import br.ufrn.friovax.api.telemetria.aplicacao.ReceberLeiturasEmLote;
import br.ufrn.friovax.api.telemetria.dominio.NovaLeitura;
import br.ufrn.friovax.api.telemetria.dominio.SolicitacaoDeAnalise;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.node.NumericNode;
import io.quarkus.security.PermissionsAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static br.ufrn.friovax.api.autenticacao.dominio.MatrizAcesso.REGISTRAR_LEITURA;

@Path("/api/leituras/lote")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = DocumentacaoApi.TAG_LEITURAS)
@SecurityRequirement(name = "bearerAuth")
public class LeituraLoteResource {

    private final ReceberLeiturasEmLote receberLote;
    private final ObjectReader json;

    @Inject
    public LeituraLoteResource(ReceberLeiturasEmLote receberLote, ObjectMapper mapper) {
        this.receberLote = receberLote;
        this.json = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    }

    @POST
    @PermissionsAllowed(REGISTRAR_LEITURA)
    @Operation(summary = "Receber leituras em lote",
        description = "ADMINISTRADOR ou OPERADOR. Recebe de 1 a 50 leituras com chaves (camaraId, idExterno) distintas. "
            + "Ordena as novas por instante dentro de cada câmara e faz uma única chamada de análise quando houver "
            + "candidatas elegíveis. Cada câmara confirma suas candidatas e seus efeitos em uma transação independente. "
            + "Mudança de snapshot rejeita as novas candidatas da câmara com 409 por item, sem repetir a análise. "
            + "Reenvios com conteúdo equivalente recuperam o resultado original.")
    @RequestBody(required = true, content = @Content(mediaType = MediaType.APPLICATION_JSON,
        schema = @Schema(type = SchemaType.ARRAY, implementation = LeituraRequest.class,
            minItems = 1, maxItems = SolicitacaoDeAnalise.CANDIDATAS_MAXIMAS)))
    @APIResponse(responseCode = "200", description = "Um resultado por entrada na ordem recebida. Uma câmara pode "
        + "confirmar mesmo quando outra é rejeitada ou tem sua transação revertida.",
        content = @Content(mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(type = SchemaType.ARRAY, implementation = LeituraLoteResponse.class)))
    @APIResponse(responseCode = "400", ref = DocumentacaoApi.REQUISICAO_INVALIDA)
    @APIResponse(responseCode = "401", description = "Bearer ausente, inválido ou expirado.")
    @APIResponse(responseCode = "403", description = "Perfil sem permissão para esta operação.")
    @APIResponse(responseCode = "422", ref = DocumentacaoApi.DADOS_INVALIDOS)
    @APIResponse(responseCode = "502", description = "Resposta global da análise fora do contrato. Nenhuma nova gravação.",
        content = @Content(mediaType = DocumentacaoApi.PROBLEM_JSON,
            schema = @Schema(implementation = ProblemDetails.class)))
    @APIResponse(responseCode = "503", description = "A única chamada de análise falhou, foi cancelada ou excedeu o prazo "
        + "antes de qualquer nova gravação do lote. Pode reenviar.",
        content = @Content(mediaType = DocumentacaoApi.PROBLEM_JSON,
            schema = @Schema(implementation = ProblemDetails.class)))
    public List<LeituraLoteResponse> receber(String body) {
        return receberLote.receber(lerEntradas(body)).stream().map(LeituraLoteResponse::de).toList();
    }

    // Lê a sintaxe inteira antes de conferir a estrutura, para distinguir JSON malformado de valores inválidos.
    private List<NovaLeitura> lerEntradas(String body) {
        if (body == null || body.isBlank()) {
            throw new BadRequestException();
        }
        JsonNode raiz;
        try {
            raiz = json.readTree(body);
        } catch (JsonProcessingException malformado) {
            throw new BadRequestException(malformado);
        }
        if (!raiz.isArray() || raiz.isEmpty() || raiz.size() > SolicitacaoDeAnalise.CANDIDATAS_MAXIMAS) {
            throw new ValidacaoDeNegocio("leituras", "deve ser um array com 1 a "
                    + SolicitacaoDeAnalise.CANDIDATAS_MAXIMAS + " leituras");
        }
        var entradas = new ArrayList<NovaLeitura>(raiz.size());
        for (var item : raiz) {
            int posicao = entradas.size();
            if (!item.isObject()) {
                throw new ValidacaoDeNegocio("leituras[" + posicao + "]", "deve ser um objeto de leitura");
            }
            try {
                var dto = new LeituraRequest(
                        campo(item, "idExterno", JsonNode::isTextual, "um texto").textValue(),
                        campo(item, "camaraId", n -> n.isIntegralNumber() && n.canConvertToLong(), "um inteiro")
                                .longValue(),
                        campo(item, "temperatura", n -> n instanceof NumericNode numero && !numero.isNaN(),
                                "um número").decimalValue(),
                        campo(item, "medidaEm", JsonNode::isTextual, "um texto").textValue(),
                        campo(item, "origem", n -> n.isTextual() && !n.textValue().isBlank(),
                                "um texto não vazio").textValue());
                entradas.add(new NovaLeitura(dto.camaraId(), dto.idExterno(), dto.instanteDaMedicao(),
                        dto.temperatura(), dto.origem()));
            } catch (ValidacaoDeNegocio invalida) {
                throw new ValidacaoDeNegocio("leituras[" + posicao + "]." + invalida.campo(), invalida.getMessage());
            }
        }
        return entradas;
    }

    private static JsonNode campo(JsonNode item, String nome, Predicate<JsonNode> tipo, String esperado) {
        var valor = item.path(nome);
        if (!tipo.test(valor)) {
            throw new ValidacaoDeNegocio(nome, "deve ser informado como " + esperado);
        }
        return valor;
    }
}

package br.ufrn.friovax.api.compartilhado.api;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

//formato de erro da API, seguindo RFC 9457 (Problem Details)

@Schema(description = "Erro no formato Problem Details (RFC 9457), enviado com `Content-Type: "
    + "application/problem+json`.")
public record ProblemDetails(
        @Schema(description = "Categoria do erro: `/problemas/requisicao-invalida`, "
            + "`/problemas/recurso-nao-encontrado`, `/problemas/codigo-duplicado`, `/problemas/capacidade-excedida`, "
            + "`/problemas/estado-incompativel`, `/problemas/validacao` ou `/problemas/erro-interno`.",
            examples = "/problemas/capacidade-excedida")
        String type,
        @Schema(description = "Resumo da categoria do erro.", examples = "Capacidade da câmara excedida")
        String title,
        @Schema(description = "Status HTTP da resposta.", examples = "409")
        int status,
        @Schema(description = "Explicação desta ocorrência.",
            examples = "A câmara CAM-01 comporta 5000 doses e já possui 4500; não é possível alocar mais 1200.")
        String detail,
        @Schema(description = "Caminho da requisição.", examples = "/api/lotes")
        String instance,
        @Schema(description = "Campos inválidos; preenchido apenas em respostas 422.", nullable = true)
        List<CampoErro> erros
) {

    public ProblemDetails(String type, String title, int status, String detail, String instance) {
        this(type, title, status, detail, instance, null);
    }

    @Schema(description = "Campo rejeitado na validação.")
    public record CampoErro(
            @Schema(description = "Nome do campo; `body` quando o corpo inteiro está ausente.",
                examples = "capacidade")
            String campo,
            @Schema(examples = "deve ser maior que zero")
            String mensagem) {}
}

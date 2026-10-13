package br.ufrn.friovax.api.lote.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDate;

// O tamanho dos textos é verificado no domínio, depois do trim (contrato, §2.3).
@Schema(description = "Dados para cadastrar um lote. O lote é criado com `estado=DISPONIVEL` e `ativo=true`.")
public record LoteRequest(

    @Schema(description = "Código de negócio, imutável após o cadastro. Recebe `trim` e é convertido para "
        + "maiúsculas; depois disso deve ter de 1 a 40 caracteres entre `A-Z`, `0-9`, `-` e `/`. Único entre todos "
        + "os lotes, inclusive inativos.", examples = "fx2027a")
    @NotBlank(message = "deve ser informado")
    String codigo,

    @Schema(description = "Nome do imunobiológico, de 1 a 100 caracteres após o `trim`.",
        examples = "Febre amarela")
    @NotBlank(message = "deve ser informado")
    String imunobiologico,

    @Schema(description = "Fabricante, de 1 a 100 caracteres após o `trim`.", examples = "Bio-Manguinhos")
    @NotBlank(message = "deve ser informado")
    String fabricante,

    @Schema(description = "Data de validade, posterior a hoje em `America/Fortaleza`.", examples = "2027-03-31")
    @NotNull(message = "deve ser informada")
    LocalDate validade,

    @Schema(description = "Quantidade em doses. Somada à ocupação, não pode ultrapassar a capacidade da câmara "
        + "(409).", examples = "1200")
    @NotNull(message = "deve ser informada")
    @Positive(message = "deve ser maior que zero")
    Integer quantidade,

    @Schema(description = "Câmara de destino: deve existir (404), estar ativa e `OPERACIONAL` (409).",
        examples = "1")
    @NotNull(message = "deve ser informado")
    Long camaraId
) {}

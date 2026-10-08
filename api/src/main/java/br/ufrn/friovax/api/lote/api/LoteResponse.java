package br.ufrn.friovax.api.lote.api;

import br.ufrn.friovax.api.lote.dominio.EstadoLote;
import br.ufrn.friovax.api.lote.dominio.Lote;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Schema(description = "Representação de um lote.")
public record LoteResponse(
        @Schema(description = "Identificador técnico usado nas rotas.", examples = "10")
        long id,
        @Schema(description = "Código de negócio normalizado em maiúsculas.", examples = "FX2027A")
        String codigo,
        @Schema(examples = "Febre amarela")
        String imunobiologico,
        @Schema(examples = "Bio-Manguinhos")
        String fabricante,
        @Schema(examples = "2027-03-31")
        LocalDate validade,
        @Schema(description = "Quantidade atual em doses.", examples = "1200")
        int quantidade,
        @Schema(description = "Câmara onde o lote está alocado.", examples = "1")
        long camaraId,
        @Schema(description = "`DISPONIVEL` enquanto houver doses; `ESGOTADO` quando a quantidade chega a zero; "
            + "`DESCARTADO` após retirada de circulação. Nunca é informado pelo cliente.")
        EstadoLote estado,
        @Schema(description = "`false` depois da inativação por `DELETE`.")
        boolean ativo,
        @Schema(examples = "2026-09-23T14:10:00-03:00")
        OffsetDateTime criadoEm,
        @Schema(examples = "2026-09-23T14:10:00-03:00")
        OffsetDateTime atualizadoEm
) {
    public static LoteResponse de(Lote lote) {
        return new LoteResponse(lote.getId(), lote.getCodigo(), lote.getImunobiologico(), lote.getFabricante(),
                lote.getValidade(), lote.getQuantidade(), lote.getCamaraId(), lote.getEstado(), lote.isAtivo(),
                lote.getCriadoEm(), lote.getAtualizadoEm());
    }
}

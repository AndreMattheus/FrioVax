package br.ufrn.friovax.api.lote.api;

import br.ufrn.friovax.api.lote.dominio.EstadoLote;
import br.ufrn.friovax.api.lote.dominio.Lote;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record LoteResponse(
        long id,
        String codigo,
        String imunobiologico,
        String fabricante,
        LocalDate validade,
        int quantidade,
        long camaraId,
        EstadoLote estado,
        boolean ativo,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm
) {
    public static LoteResponse de(Lote lote) {
        return new LoteResponse(lote.getId(), lote.getCodigo(), lote.getImunobiologico(), lote.getFabricante(),
                lote.getValidade(), lote.getQuantidade(), lote.getCamaraId(), lote.getEstado(), lote.isAtivo(),
                lote.getCriadoEm(), lote.getAtualizadoEm());
    }
}

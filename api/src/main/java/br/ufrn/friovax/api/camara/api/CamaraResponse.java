package br.ufrn.friovax.api.camara.api;

import br.ufrn.friovax.api.camara.aplicacao.ConsultarCamara;
import br.ufrn.friovax.api.camara.dominio.EstadoCamara;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CamaraResponse(
        long id,
        String codigo,
        String nome,
        String unidade,
        int capacidade,
        BigDecimal temperaturaMinima,
        BigDecimal temperaturaMaxima,
        EstadoCamara estado,
        int ocupacao,
        boolean ativo,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm
) {
    public static CamaraResponse de(ConsultarCamara.Resultado resultado) {
        var c = resultado.camara();
        return new CamaraResponse(c.getId(), c.getCodigo(), c.getNome(), c.getUnidade(), c.getCapacidade(),
                c.getTemperaturaMinima(), c.getTemperaturaMaxima(), c.getEstado(),
                Math.toIntExact(resultado.ocupacao()),
                c.isAtivo(), c.getCriadoEm(), c.getAtualizadoEm());
    }
}

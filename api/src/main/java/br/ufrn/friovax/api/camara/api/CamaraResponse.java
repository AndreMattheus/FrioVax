package br.ufrn.friovax.api.camara.api;

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
) {}

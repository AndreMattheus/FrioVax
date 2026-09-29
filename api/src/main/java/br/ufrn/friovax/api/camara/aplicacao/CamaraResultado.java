package br.ufrn.friovax.api.camara.aplicacao;

import br.ufrn.friovax.api.camara.dominio.EstadoCamara;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CamaraResultado(
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

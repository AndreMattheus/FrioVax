package br.ufrn.friovax.api.telemetria.aplicacao;

import br.ufrn.friovax.api.telemetria.dominio.Leitura;

import java.util.Objects;

/**
 * Leitura confirmada devolvida pela recepção.
 *
 * @param criada {@code true} na primeira aceitação; {@code false} quando a chave já existia com o mesmo conteúdo
 */
public record LeituraRecebida(Leitura leitura, boolean criada) {

    public LeituraRecebida {
        Objects.requireNonNull(leitura, "leitura");
    }
}

package br.ufrn.friovax.api.telemetria.aplicacao;

import br.ufrn.friovax.api.telemetria.dominio.Leitura;

import java.util.Objects;

/**
 * Leitura confirmada devolvida pela recepção.
 *
 * @param criada             {@code true} na primeira aceitação; {@code false} quando a chave já existia com o mesmo
 *                           conteúdo
 * @param ocorrenciaAbertaId ocorrência aberta por esta leitura ao atingir o limiar (S2-11); nula se não abriu
 */
public record LeituraRecebida(Leitura leitura, boolean criada, Long ocorrenciaAbertaId) {

    public LeituraRecebida {
        Objects.requireNonNull(leitura, "leitura");
    }
    public LeituraRecebida(Leitura leitura, boolean criada) {
        this(leitura, criada, null);
    }
}

package br.ufrn.friovax.api.autenticacao.aplicacao;

import br.ufrn.friovax.api.autenticacao.dominio.Identidade;
import java.time.Instant;

public record SessaoResultado(String accessToken, String refreshToken, Instant expiraEm,
                              Instant refreshExpiraEm, Identidade identidade) {
    @Override
    public String toString() { return "SessaoResultado[redigido]"; }
}

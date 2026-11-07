package br.ufrn.friovax.api.autenticacao.dominio;

import java.time.Instant;

public interface EmissorAccessToken {
    String emitir(Identidade identidade, Instant emitidoEm, Instant expiraEm);
}

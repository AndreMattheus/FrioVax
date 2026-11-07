package br.ufrn.friovax.api.autenticacao.api;

import br.ufrn.friovax.api.autenticacao.aplicacao.SessaoResultado;
import br.ufrn.friovax.api.autenticacao.dominio.Identidade;
import java.time.Instant;

public record SessaoResponse(String accessToken, String refreshToken, String tokenType,
                             long expiresIn, Instant expiraEm, Instant refreshExpiraEm,
                             Identidade identidade) {
    public static SessaoResponse de(SessaoResultado sessao) {
        return new SessaoResponse(sessao.accessToken(), sessao.refreshToken(), "Bearer", 900,
                sessao.expiraEm(), sessao.refreshExpiraEm(), sessao.identidade());
    }
    @Override public String toString() { return "SessaoResponse[redigido]"; }
}

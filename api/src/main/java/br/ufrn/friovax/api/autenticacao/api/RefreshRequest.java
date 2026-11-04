package br.ufrn.friovax.api.autenticacao.api;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record RefreshRequest(@Schema(writeOnly = true) String refreshToken) {
    @Override public String toString() { return "RefreshRequest[redigido]"; }
}

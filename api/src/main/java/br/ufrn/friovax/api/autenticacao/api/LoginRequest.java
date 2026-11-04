package br.ufrn.friovax.api.autenticacao.api;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record LoginRequest(String login,
        @Schema(writeOnly = true, format = "password") String senha) {
    @Override public String toString() { return "LoginRequest[redigido]"; }
}

package br.ufrn.friovax.api.autenticacao.dominio;

import java.util.UUID;

/** O id interno estável é o subject do JWT, usado por autorização e auditoria. */
public record Identidade(UUID id, String login, Perfil perfil) {}

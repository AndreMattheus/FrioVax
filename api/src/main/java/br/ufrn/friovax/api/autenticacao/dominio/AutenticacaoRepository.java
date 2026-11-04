package br.ufrn.friovax.api.autenticacao.dominio;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AutenticacaoRepository {
    Optional<Credencial> buscarPorLogin(String login);
    void adicionar(Credencial credencial);
    void criarSessao(String hash, UUID usuarioId, Instant criadaEm, Instant expiraEm);
    /** Consome uma única vez, atomicamente, apenas uma sessão não expirada/revogada. */
    Optional<Identidade> consumirSessao(String hash, Instant agora);
}

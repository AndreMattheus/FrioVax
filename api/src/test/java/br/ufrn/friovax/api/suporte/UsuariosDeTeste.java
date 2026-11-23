package br.ufrn.friovax.api.suporte;

import br.ufrn.friovax.api.autenticacao.dominio.Perfil;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.persistence.EntityManager;

import java.util.UUID;

/**
 * Usuários gravados no PostgreSQL para testes que conferem autoria. O hash só satisfaz o formato BCrypt da tabela:
 * ninguém faz login com ele. Apague as câmaras do teste antes do usuário, porque as versões de política o referenciam.
 */
public final class UsuariosDeTeste {
    private UsuariosDeTeste() {
    }

    public static UUID cadastrar(EntityManager em, Perfil perfil) {
        var id = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery(
                        "insert into usuarios (id, login, senha_hash, perfil) values (:id, :login, :hash, :perfil)")
                .setParameter("id", id)
                .setParameter("login", login(id))
                .setParameter("hash", "$2a$10$" + "a".repeat(53))
                .setParameter("perfil", perfil.name())
                .executeUpdate());
        return id;
    }

    public static String login(UUID id) {
        return "teste-" + id;
    }

    public static void apagar(EntityManager em, UUID id) {
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("delete from usuarios where id = :id")
                .setParameter("id", id).executeUpdate());
    }
}

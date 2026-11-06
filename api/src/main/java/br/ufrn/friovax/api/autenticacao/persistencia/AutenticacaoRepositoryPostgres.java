package br.ufrn.friovax.api.autenticacao.persistencia;

import br.ufrn.friovax.api.autenticacao.dominio.*;
import jakarta.enterprise.context.ApplicationScoped;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class AutenticacaoRepositoryPostgres implements AutenticacaoRepository, PanacheRepositoryBase<UsuarioEntity, UUID> {

    @Override
    public Optional<Credencial> buscarPorLogin(String login) {
        return getEntityManager().createQuery("from UsuarioEntity where login = :login", UsuarioEntity.class)
                .setParameter("login", login).getResultStream().findFirst()
                .map(u -> new Credencial(identidade(u), u.senhaHash));
    }

    @Override
    public void adicionar(Credencial credencial) {
        var usuario = new UsuarioEntity();
        usuario.id = credencial.identidade().id();
        usuario.login = credencial.identidade().login();
        usuario.perfil = credencial.identidade().perfil();
        usuario.senhaHash = credencial.senhaHash();
        getEntityManager().persist(usuario);
        getEntityManager().flush();
    }

    @Override
    public void criarSessao(String hash, UUID usuarioId, Instant criadaEm, Instant expiraEm) {
        var sessao = new SessaoEntity();
        sessao.tokenHash = hash;
        sessao.usuarioId = usuarioId;
        sessao.criadaEm = criadaEm;
        sessao.expiraEm = expiraEm;
        getEntityManager().persist(sessao);
        getEntityManager().flush();
    }

    @Override
    public Optional<Identidade> consumirSessao(String hash, Instant agora) {
        // O lock dura até o commit da renovação/logout: só uma chamada concorrente vence.
        var sessao = getEntityManager().find(SessaoEntity.class, hash, LockModeType.PESSIMISTIC_WRITE);
        if (sessao == null || sessao.revogadaEm != null || !agora.isBefore(sessao.expiraEm)) {
            return Optional.empty();
        }
        sessao.revogadaEm = agora;
        getEntityManager().flush();
        return Optional.of(identidade(getEntityManager().find(UsuarioEntity.class, sessao.usuarioId)));
    }

    private Identidade identidade(UsuarioEntity usuario) {
        return new Identidade(usuario.id, usuario.login, usuario.perfil);
    }
}

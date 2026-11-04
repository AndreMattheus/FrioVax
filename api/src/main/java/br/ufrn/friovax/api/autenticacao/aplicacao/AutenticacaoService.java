package br.ufrn.friovax.api.autenticacao.aplicacao;

import br.ufrn.friovax.api.autenticacao.dominio.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@ApplicationScoped
public class AutenticacaoService {
    public static final Duration DURACAO_ACCESS = Duration.ofMinutes(15);
    public static final Duration DURACAO_REFRESH = Duration.ofHours(24);
    private final AutenticacaoRepository repositorio;
    private final ProtecaoCredenciais protecao;
    private final EmissorAccessToken emissor;
    private final Clock relogio;
    private final String hashAusente;

    @Inject
    public AutenticacaoService(AutenticacaoRepository repositorio, ProtecaoCredenciais protecao,
                               EmissorAccessToken emissor, Clock relogio) {
        this.repositorio = repositorio;
        this.protecao = protecao;
        this.emissor = emissor;
        this.relogio = relogio;
        // Usuário ausente também verifica BCrypt, sem caminho rápido de enumeração.
        this.hashAusente = protecao.protegerSenha("credencial-interna-sem-conta");
    }

    @Transactional
    public SessaoResultado login(String login, String senha) {
        if (login == null || login.isBlank() || login.length() > 100 || senha == null
                || senha.isBlank() || senha.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new AutenticacaoInvalida();
        }
        var credencial = repositorio.buscarPorLogin(login);
        boolean valida = protecao.verificarSenha(senha,
                credencial.map(Credencial::senhaHash).orElse(hashAusente));
        if (!valida || credencial.isEmpty()) throw new AutenticacaoInvalida();
        return emitir(credencial.orElseThrow().identidade(), relogio.instant());
    }

    @Transactional
    public SessaoResultado renovar(String refresh) {
        var agora = relogio.instant();
        var identidade = repositorio.consumirSessao(hashValidado(refresh), agora)
                .orElseThrow(AutenticacaoInvalida::new);
        return emitir(identidade, agora);
    }

    @Transactional
    public void logout(String refresh) {
        repositorio.consumirSessao(hashValidado(refresh), relogio.instant())
                .orElseThrow(AutenticacaoInvalida::new);
    }

    private String hashValidado(String refresh) {
        if (refresh == null || !refresh.matches("[A-Za-z0-9_-]{43}")) throw new AutenticacaoInvalida();
        return protecao.hashRefresh(refresh);
    }

    private SessaoResultado emitir(Identidade identidade, Instant agora) {
        var expiraEm = agora.plus(DURACAO_ACCESS);
        var refreshExpiraEm = agora.plus(DURACAO_REFRESH);
        var access = emissor.emitir(identidade, agora, expiraEm);
        var refresh = protecao.novoRefresh();
        repositorio.criarSessao(protecao.hashRefresh(refresh), identidade.id(), agora, refreshExpiraEm);
        return new SessaoResultado(access, refresh, expiraEm, refreshExpiraEm, identidade);
    }
}

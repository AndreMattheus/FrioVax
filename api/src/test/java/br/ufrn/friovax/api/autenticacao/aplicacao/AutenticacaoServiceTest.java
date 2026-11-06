package br.ufrn.friovax.api.autenticacao.aplicacao;

import br.ufrn.friovax.api.autenticacao.dominio.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AutenticacaoServiceTest {
    private static final Instant AGORA = Instant.parse("2026-11-03T19:00:00Z");
    private final Identidade identidade = new Identidade(UUID.randomUUID(), "operador", Perfil.OPERADOR);
    private final Repositorio repositorio = new Repositorio();
    private final Protecao protecao = new Protecao();

    private AutenticacaoService service(Instant agora) {
        return new AutenticacaoService(repositorio, protecao, (id, inicio, fim) -> {
            assertEquals(identidade, id);
            assertEquals(Duration.ofMinutes(15), Duration.between(inicio, fim));
            return "jwt-assinado";
        }, Clock.fixed(agora, ZoneOffset.UTC));
    }

    @Test
    void emitePrazosExatosEGuardaApenasHashDoRefresh() {
        var sessao = service(AGORA).login("operador", "senha-correta");
        assertEquals(AGORA.plusSeconds(900), sessao.expiraEm());
        assertEquals(AGORA.plusSeconds(86400), sessao.refreshExpiraEm());
        assertEquals(identidade, sessao.identidade());
        assertFalse(repositorio.sessoes.containsKey(sessao.refreshToken()));
        assertTrue(repositorio.sessoes.containsKey(protecao.hashRefresh(sessao.refreshToken())));
        assertFalse(sessao.toString().contains(sessao.refreshToken()));
    }

    @Test
    void loginInvalidoNaoRevelaContaEVerificaHashMesmoSemUsuario() {
        var service = service(AGORA);
        var incorreta = assertThrows(AutenticacaoInvalida.class, () -> service.login("operador", "errada"));
        var ausente = assertThrows(AutenticacaoInvalida.class, () -> service.login("ausente", "errada"));
        assertEquals(incorreta.getMessage(), ausente.getMessage());
        assertEquals(2, protecao.verificacoes);
        assertTrue(repositorio.sessoes.isEmpty());
        assertThrows(AutenticacaoInvalida.class, () -> service.login("operador", "á".repeat(37)));
    }

    @Test
    void refreshFuncionaAteAntesDe24HorasMasNuncaNoLimite() {
        var primeiro = service(AGORA).login("operador", "senha-correta");
        var segundo = service(AGORA).login("operador", "senha-correta");
        assertNotNull(service(AGORA.plusSeconds(86399)).renovar(primeiro.refreshToken()));
        assertThrows(AutenticacaoInvalida.class,
                () -> service(AGORA.plusSeconds(86400)).renovar(segundo.refreshToken()));
    }

    @Test
    void rotacaoELogoutImpedemReusoSemAfetarOutraSessao() {
        var service = service(AGORA);
        var primeira = service.login("operador", "senha-correta");
        var outra = service.login("operador", "senha-correta");
        var renovada = service.renovar(primeira.refreshToken());
        assertNotEquals(primeira.refreshToken(), renovada.refreshToken());
        assertThrows(AutenticacaoInvalida.class, () -> service.renovar(primeira.refreshToken()));
        service.logout(renovada.refreshToken());
        assertThrows(AutenticacaoInvalida.class, () -> service.renovar(renovada.refreshToken()));
        assertNotNull(service.renovar(outra.refreshToken()));
        for (String invalido : Arrays.asList(null, "", "token-invalido")) {
            assertThrows(AutenticacaoInvalida.class, () -> service.renovar(invalido));
            assertThrows(AutenticacaoInvalida.class, () -> service.logout(invalido));
        }
    }

    private class Repositorio implements AutenticacaoRepository {
        private final Map<String, Instant> sessoes = new HashMap<>();
        public Optional<Credencial> buscarPorLogin(String login) {
            return login.equals("operador") ? Optional.of(new Credencial(identidade, "hash:senha-correta"))
                    : Optional.empty();
        }
        public void adicionar(Credencial c) { throw new UnsupportedOperationException(); }
        public void criarSessao(String hash, UUID id, Instant criada, Instant expira) {
            sessoes.put(hash, expira);
        }
        public Optional<Identidade> consumirSessao(String hash, Instant agora) {
            var expira = sessoes.get(hash);
            if (expira == null || !agora.isBefore(expira)) return Optional.empty();
            sessoes.remove(hash);
            return Optional.of(identidade);
        }
    }

    private static class Protecao implements ProtecaoCredenciais {
        int sequencia;
        int verificacoes;
        public String protegerSenha(String senha) { return "hash:" + senha; }
        public boolean verificarSenha(String senha, String hash) {
            verificacoes++;
            return protegerSenha(senha).equals(hash);
        }
        public String novoRefresh() { return String.format("%043d", ++sequencia); }
        public String hashRefresh(String refresh) { return "hash:" + refresh; }
    }
}

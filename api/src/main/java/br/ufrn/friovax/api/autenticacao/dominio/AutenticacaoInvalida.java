package br.ufrn.friovax.api.autenticacao.dominio;

public class AutenticacaoInvalida extends RuntimeException {
    public AutenticacaoInvalida() {
        super("Credenciais ou sessão inválidas.");
    }
}

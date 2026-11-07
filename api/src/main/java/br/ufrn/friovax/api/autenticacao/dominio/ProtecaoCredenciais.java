package br.ufrn.friovax.api.autenticacao.dominio;

public interface ProtecaoCredenciais {
    String protegerSenha(String senha);
    boolean verificarSenha(String senha, String hash);
    String novoRefresh();
    String hashRefresh(String refresh);
}

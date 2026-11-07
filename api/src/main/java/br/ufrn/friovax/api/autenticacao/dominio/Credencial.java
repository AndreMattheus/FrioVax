package br.ufrn.friovax.api.autenticacao.dominio;

public final class Credencial {
    private final Identidade identidade;
    private final String senhaHash;

    public Credencial(Identidade identidade, String senhaHash) {
        this.identidade = identidade;
        this.senhaHash = senhaHash;
    }

    public Identidade identidade() { return identidade; }
    public String senhaHash() { return senhaHash; }
}

package br.ufrn.friovax.api.compartilhado.dominio;

/**
 * Regra de entrada violada em um campo específico (por exemplo, validade não futura).
 */
public class ValidacaoDeNegocio extends ExcecaoDeDominio {

    private final String campo;

    public ValidacaoDeNegocio(String campo, String mensagem) {
        super(mensagem);
        this.campo = campo;
    }

    public String campo() {
        return campo;
    }
}

package br.ufrn.friovax.api.compartilhado.dominio;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalização de textos do contrato (§2.3): {@code trim}, texto vazio tratado como ausente e códigos em maiúsculas.
 */
public final class Textos {

    private Textos() {
    }

    public static String obrigatorio(String campo, String valor, int tamanhoMaximo) {
        var normalizado = valor == null ? "" : valor.trim();
        if (normalizado.isEmpty()) {
            throw new ValidacaoDeNegocio(campo, "deve ser informado");
        }
        if (normalizado.length() > tamanhoMaximo) {
            throw new ValidacaoDeNegocio(campo, "deve ter no máximo " + tamanhoMaximo + " caracteres");
        }
        return normalizado;
    }

    public static String codigo(String valor, Pattern formato, String descricaoFormato) {
        var normalizado = valor == null ? "" : valor.trim().toUpperCase(Locale.ROOT);
        if (normalizado.isEmpty()) {
            throw new ValidacaoDeNegocio("codigo", "deve ser informado");
        }
        if (!formato.matcher(normalizado).matches()) {
            throw new ValidacaoDeNegocio("codigo", "deve ter " + descricaoFormato);
        }
        return normalizado;
    }

    /** Normaliza um texto opcional de filtro: {@code null} quando ausente ou vazio. */
    public static String opcional(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim();
    }
}

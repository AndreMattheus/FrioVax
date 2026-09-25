package br.ufrn.friovax.api.compartilhado.dominio;

/**
 * Página solicitada. {@code pagina} começa em 0; {@code tamanho} vai de 1 a 100 (contrato, D13).
 */
public record Paginacao(int pagina, int tamanho) {

    public static final int PAGINA_PADRAO = 0;
    public static final int TAMANHO_PADRAO = 20;
    public static final int TAMANHO_MAXIMO = 100;

    public Paginacao {
        if (pagina < 0) {
            throw new IllegalArgumentException("pagina deve ser maior ou igual a 0");
        }
        if (tamanho < 1 || tamanho > TAMANHO_MAXIMO) {
            throw new IllegalArgumentException("tamanho deve estar entre 1 e " + TAMANHO_MAXIMO);
        }
    }

    public static Paginacao padrao() {
        return new Paginacao(PAGINA_PADRAO, TAMANHO_PADRAO);
    }

    public long deslocamento() {
        return (long) pagina * tamanho;
    }
}

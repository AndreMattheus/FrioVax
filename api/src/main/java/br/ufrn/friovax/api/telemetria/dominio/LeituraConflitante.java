package br.ufrn.friovax.api.telemetria.dominio;

/**
 * A chave {@code (camaraId, idExterno)} já foi aceita com outro conteúdo (ADR 0003, seção 5). A comparação usa a
 * temperatura em escala 1, o instante em UTC e a origem após o {@code trim}; nada é gravado.
 */
public class LeituraConflitante extends ConflitoDeLeitura {

    private final String idExterno;

    public LeituraConflitante(long camaraId, String idExterno) {
        super(camaraId, "A leitura " + idExterno + " da câmara " + camaraId
                + " já foi registrada com outra temperatura, instante ou origem.");
        this.idExterno = idExterno;
    }

    public String idExterno() {
        return idExterno;
    }
}

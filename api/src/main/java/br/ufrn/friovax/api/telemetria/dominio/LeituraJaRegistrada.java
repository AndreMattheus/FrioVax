package br.ufrn.friovax.api.telemetria.dominio;

import br.ufrn.friovax.api.compartilhado.dominio.CodigoDuplicado;

public class LeituraJaRegistrada extends CodigoDuplicado {
    private final long camaraId;
    private final String idExterno;

    public LeituraJaRegistrada(long camaraId, String idExterno) {
        super("uma leitura na câmara " + camaraId, idExterno);
        this.camaraId = camaraId;
        this.idExterno = idExterno;
    }

    public long camaraId() {
        return camaraId;
    }

    public String idExterno() {
        return idExterno;
    }
}

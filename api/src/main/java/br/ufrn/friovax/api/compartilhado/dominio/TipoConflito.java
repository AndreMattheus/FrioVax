package br.ufrn.friovax.api.compartilhado.dominio;

// cada valor desse enum é uma linha 409 da tabela de erros em docs/contrato-api.md
// se precisar adicionar um novo tipo de conflito de negócio é só adicionar um valor aqui, sem mexer no mapper

public enum TipoConflito {
    CODIGO_DUPLICADO("/problemas/codigo-duplicado", "Código duplicado"),
    CAPACIDADE_EXCEDIDA("/problemas/capacidade-excedida", "Capacidade da câmara excedida"),
    ESTADO_INCOMPATIVEL("/problemas/estado-incompativel", "Operação incompatível com o estado atual");

    private final String tipoUri;
    private final String tituloPadrao;

    TipoConflito(String tipoUri, String tituloPadrao) {
        this.tipoUri = tipoUri;
        this.tituloPadrao = tituloPadrao;
    }

    public String tipoUri() {
        return tipoUri;
    }

    public String tituloPadrao() {
        return tituloPadrao;
    }
}

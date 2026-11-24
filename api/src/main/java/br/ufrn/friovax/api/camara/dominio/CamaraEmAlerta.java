package br.ufrn.friovax.api.camara.dominio;

import br.ufrn.friovax.api.compartilhado.dominio.EstadoIncompativel;

/**
 * A câmara tem ocorrência térmica aberta ({@code EM_ALERTA}) e recusa operações que colocariam doses em uso ou dentro
 * dela até a resolução explícita (ADR 0003, seção 4). Responde 409 como qualquer {@link EstadoIncompativel}.
 */
public class CamaraEmAlerta extends EstadoIncompativel {

    public CamaraEmAlerta(String codigo, String operacao) {
        super("A câmara " + codigo + " está EM_ALERTA, com ocorrência térmica aberta, e não aceita " + operacao
            + " até a ocorrência ser resolvida.");
    }
}

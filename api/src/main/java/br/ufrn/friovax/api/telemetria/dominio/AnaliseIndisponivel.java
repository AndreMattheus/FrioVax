package br.ufrn.friovax.api.telemetria.dominio;

import java.util.Objects;

/**
 * A análise não pôde ser obtida a tempo: serviço inacessível, prazo esgotado ou chamada cancelada. É uma
 * indisponibilidade controlada (ADR 0003, seção 7): nenhuma leitura nova deve ser confirmada e a mensagem não contém
 * detalhes de transporte.
 */
public class AnaliseIndisponivel extends RuntimeException {

    public enum Motivo {
        SERVICO_INACESSIVEL,
        PRAZO_ESGOTADO,
        CANCELADA
    }

    private final Motivo motivo;

    public AnaliseIndisponivel(Motivo motivo) {
        super(mensagem(Objects.requireNonNull(motivo, "motivo")), null, false, false);
        this.motivo = motivo;
    }

    public Motivo motivo() {
        return motivo;
    }

    private static String mensagem(Motivo motivo) {
        return switch (motivo) {
            case SERVICO_INACESSIVEL -> "O serviço de análise térmica está indisponível.";
            case PRAZO_ESGOTADO -> "O serviço de análise térmica não respondeu dentro do prazo.";
            case CANCELADA -> "A análise térmica foi cancelada antes de terminar.";
        };
    }
}

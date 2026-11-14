package br.ufrn.friovax.api.telemetria.dominio;

import java.util.Objects;

/**
 * Resultado confirmado da análise de uma leitura (ADR 0003, seção 3): classificação, sequência insegura limitada a N
 * e se o limiar foi atingido. Vem do serviço Go, então incoerências são erro de integração, não do usuário.
 */
public record ResultadoTermico(ClassificacaoTermica classificacao, int sequencia, boolean limiarAtingido) {

    public static final int SEQUENCIA_MAXIMA = 100;

    public ResultadoTermico {
        Objects.requireNonNull(classificacao, "classificacao");
        if (classificacao == ClassificacaoTermica.SEGURA && (sequencia != 0 || limiarAtingido)) {
            throw new IllegalArgumentException("Leitura segura tem sequência 0 e não atinge o limiar.");
        }
        if (classificacao == ClassificacaoTermica.INSEGURA && (sequencia < 1 || sequencia > SEQUENCIA_MAXIMA)) {
            throw new IllegalArgumentException("Leitura insegura tem sequência entre 1 e " + SEQUENCIA_MAXIMA + ".");
        }
    }

    public static ResultadoTermico segura() {
        return new ResultadoTermico(ClassificacaoTermica.SEGURA, 0, false);
    }

    public static ResultadoTermico insegura(int sequencia, boolean limiarAtingido) {
        return new ResultadoTermico(ClassificacaoTermica.INSEGURA, sequencia, limiarAtingido);
    }
}

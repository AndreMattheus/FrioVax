package br.ufrn.friovax.api.telemetria.dominio;

import java.util.List;
import java.util.Objects;

/**
 * Resultado calculado para uma candidata: classificação, sequência limitada a N, sinal de limiar e ids externos das
 * leituras que formam a sequência insegura (incluindo a própria candidata). Não confirma efeitos.
 */
public record ResultadoDaCandidata(String idExterno, ResultadoTermico resultado, List<String> evidencias) {

    public ResultadoDaCandidata {
        Objects.requireNonNull(idExterno, "idExterno");
        Objects.requireNonNull(resultado, "resultado");
        evidencias = List.copyOf(Objects.requireNonNull(evidencias, "evidencias"));
        if (resultado.classificacao() == ClassificacaoTermica.SEGURA && !evidencias.isEmpty()) {
            throw new IllegalArgumentException("Leitura segura não tem evidências.");
        }
        if (resultado.classificacao() == ClassificacaoTermica.INSEGURA
                && (evidencias.size() != resultado.sequencia() || !idExterno.equals(evidencias.getLast()))) {
            throw new IllegalArgumentException(
                    "As evidências devem ter o tamanho da sequência e terminar na própria candidata.");
        }
    }
}

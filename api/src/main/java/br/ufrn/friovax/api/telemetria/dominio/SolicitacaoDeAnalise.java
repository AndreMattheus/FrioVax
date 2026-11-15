package br.ufrn.friovax.api.telemetria.dominio;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Uma chamada de análise: identificador de correlação e uma janela por câmara, com 1 a 50 candidatas no total
 * (ADR 0003, seção 3). Serve tanto à leitura individual (uma janela) quanto ao lote (várias janelas).
 */
public record SolicitacaoDeAnalise(String correlacaoId, List<JanelaDeAnalise> janelas) {

    public static final int CANDIDATAS_MAXIMAS = 50;
    public static final int CORRELACAO_MAXIMA = 128;

    /** ASCII visível e seguro para metadado gRPC e logs: sem espaços, acentos ou caracteres de controle. */
    private static final Pattern CORRELACAO = Pattern.compile("[A-Za-z0-9._:-]{1," + CORRELACAO_MAXIMA + "}");

    public SolicitacaoDeAnalise {
        if (correlacaoId == null || !CORRELACAO.matcher(correlacaoId).matches()) {
            throw new IllegalArgumentException("O identificador de correlação deve ter de 1 a " + CORRELACAO_MAXIMA
                    + " caracteres entre letras ASCII, dígitos, '.', '_', ':' e '-'.");
        }
        janelas = List.copyOf(Objects.requireNonNull(janelas, "janelas"));
        if (janelas.isEmpty()) {
            throw new IllegalArgumentException("A solicitação deve ter ao menos uma janela.");
        }
        var camaras = new HashSet<Long>();
        int candidatas = 0;
        for (var janela : janelas) {
            if (!camaras.add(janela.camaraId())) {
                throw new IllegalArgumentException("A câmara " + janela.camaraId() + " aparece em mais de uma janela.");
            }
            candidatas += janela.candidatas().size();
        }
        if (candidatas > CANDIDATAS_MAXIMAS) {
            throw new IllegalArgumentException("A solicitação deve ter no máximo " + CANDIDATAS_MAXIMAS
                    + " candidatas no total.");
        }
    }

    public static SolicitacaoDeAnalise de(String correlacaoId, JanelaDeAnalise janela) {
        return new SolicitacaoDeAnalise(correlacaoId, List.of(janela));
    }
}

package br.ufrn.friovax.api.telemetria.dominio;

import br.ufrn.friovax.api.compartilhado.dominio.ValidacaoDeNegocio;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Leitura recebida e ainda não aceita, já normalizada: temperatura em escala 1, instante em UTC e origem após o
 * {@code trim}. O {@code idExterno} não é normalizado (ADR 0003, seção 5). É a mesma entrada usada na comparação de
 * reenvios, na janela enviada ao Go e na gravação.
 */
public record NovaLeitura(long camaraId, String idExterno, OffsetDateTime medidaEm, BigDecimal temperatura,
                          String origem) {

    /** Quanto a medição pode estar à frente do relógio da API (ADR 0003, seção 5). */
    public static final Duration TOLERANCIA_FUTURA = Duration.ofMinutes(5);

    public static final String CAMPO_MEDIDA = "medidaEm";

    public NovaLeitura {
        idExterno = Leitura.idExternoValidado(idExterno);
        medidaEm = instante(medidaEm);
        temperatura = Leitura.temperaturaNormalizada(temperatura);
        origem = Leitura.origemNormalizada(origem);
    }

    /**
     * Valida a entrada e recusa medição mais de {@link #TOLERANCIA_FUTURA} à frente de {@code agora}. Exatamente no
     * limite é aceita.
     */
    public static NovaLeitura de(long camaraId, String idExterno, OffsetDateTime medidaEm, BigDecimal temperatura,
                                 String origem, OffsetDateTime agora) {
        Objects.requireNonNull(agora, "agora");
        var nova = new NovaLeitura(camaraId, idExterno, medidaEm, temperatura, origem);
        if (nova.medidaEm.isAfter(agora.plus(TOLERANCIA_FUTURA))) {
            throw new ValidacaoDeNegocio(CAMPO_MEDIDA, "não pode estar mais de "
                    + TOLERANCIA_FUTURA.toMinutes() + " minutos no futuro");
        }
        return nova;
    }

    /** Se a leitura já aceita com a mesma chave tem conteúdo equivalente a esta entrada. */
    public boolean equivaleA(Leitura aceita) {
        Objects.requireNonNull(aceita, "aceita");
        return aceita.getCamaraId() == camaraId && aceita.getIdExterno().equals(idExterno)
                && aceita.temMesmoConteudo(temperatura, medidaEm, origem);
    }

    /** Candidata da janela de análise, com os mesmos valores normalizados. */
    public LeituraDaAnalise paraAnalise() {
        return new LeituraDaAnalise(idExterno, medidaEm, temperatura);
    }

    /** Leitura aceita com o resultado calculado para a versão de política confirmada. */
    public Leitura aceitar(int politicaVersao, ResultadoTermico resultado, OffsetDateTime aceitaEm) {
        return Leitura.nova(camaraId, idExterno, medidaEm, temperatura, origem, politicaVersao, resultado, aceitaEm);
    }

    private static OffsetDateTime instante(OffsetDateTime medidaEm) {
        try {
            return Leitura.instanteNormalizado(medidaEm);
        } catch (ValidacaoDeNegocio invalido) {
            throw new ValidacaoDeNegocio(CAMPO_MEDIDA, invalido.getMessage());
        }
    }
}

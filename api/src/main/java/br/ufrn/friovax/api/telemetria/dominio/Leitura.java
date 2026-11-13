package br.ufrn.friovax.api.telemetria.dominio;

import br.ufrn.friovax.api.compartilhado.dominio.ValidacaoDeNegocio;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

//Leitura de temperatura aceita para uma câmara
//Conserva a entrada normalizada, a versão da política usada no cálculo e o resultado confirmado, para que um reenvio idêntico recupere o mesmo tratamento. Depois de gravada, não muda.
public final class Leitura {

    public static final int TAMANHO_MAXIMO_ID_EXTERNO = 100;
    public static final int TAMANHO_MAXIMO_ORIGEM = 100;

    private static final BigDecimal TEMPERATURA_MINIMA = new BigDecimal("-999.9");
    private static final BigDecimal TEMPERATURA_MAXIMA = new BigDecimal("999.9");

    private Long id;
    private final long camaraId;
    private final String idExterno;
    private final OffsetDateTime instante;
    private final BigDecimal temperatura;
    private final String origem;
    private final int politicaVersao;
    private final ResultadoTermico resultado;
    private final OffsetDateTime aceitaEm;

    private Leitura(Long id, long camaraId, String idExterno, OffsetDateTime instante, BigDecimal temperatura,
                    String origem, int politicaVersao, ResultadoTermico resultado, OffsetDateTime aceitaEm) {
        this.id = id;
        this.camaraId = camaraId;
        this.idExterno = idExterno;
        this.instante = instante;
        this.temperatura = temperatura;
        this.origem = origem;
        this.politicaVersao = politicaVersao;
        this.resultado = resultado;
        this.aceitaEm = aceitaEm;
    }

    public static Leitura nova(long camaraId, String idExterno, OffsetDateTime instante, BigDecimal temperatura,
                               String origem, int politicaVersao, ResultadoTermico resultado,
                               OffsetDateTime aceitaEm) {
        if (politicaVersao < 1) {
            throw new IllegalArgumentException("A versão da política deve ser positiva.");
        }
        return new Leitura(null, camaraId, idExternoValidado(idExterno), instanteNormalizado(instante),
                temperaturaNormalizada(temperatura), origemNormalizada(origem), politicaVersao,
                Objects.requireNonNull(resultado, "resultado"), Objects.requireNonNull(aceitaEm, "aceitaEm"));
    }

    public static Leitura reconstituir(long id, long camaraId, String idExterno, OffsetDateTime instante,
                                       BigDecimal temperatura, String origem, int politicaVersao,
                                       ResultadoTermico resultado, OffsetDateTime aceitaEm) {
        return new Leitura(id, camaraId, idExterno, instante, temperatura, origem, politicaVersao, resultado,
                aceitaEm);
    }

    public boolean temMesmoConteudo(BigDecimal temperatura, OffsetDateTime instante, String origem) {
        return this.temperatura.compareTo(temperaturaNormalizada(temperatura)) == 0
                && this.instante.isEqual(instanteNormalizado(instante))
                && this.origem.equals(origemNormalizada(origem));
    }

    public void atribuirId(long id) {
        if (this.id != null) {
            throw new IllegalStateException("A leitura já possui id " + this.id);
        }
        this.id = id;
    }

    static OffsetDateTime instanteNormalizado(OffsetDateTime instante) {
        if (instante == null) {
            throw new ValidacaoDeNegocio("instante", "deve ser informado com fuso");
        }
        return instante.withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    private static String idExternoValidado(String idExterno) {
        if (idExterno == null || idExterno.isBlank()) {
            throw new ValidacaoDeNegocio("idExterno", "deve ser informado");
        }
        if (idExterno.length() > TAMANHO_MAXIMO_ID_EXTERNO) {
            throw new ValidacaoDeNegocio("idExterno", "deve ter no máximo " + TAMANHO_MAXIMO_ID_EXTERNO
                    + " caracteres");
        }
        return idExterno;
    }

    private static String origemNormalizada(String origem) {
        var texto = origem == null ? "" : origem.trim();
        if (texto.isEmpty()) {
            throw new ValidacaoDeNegocio("origem", "deve ser informada");
        }
        if (texto.length() > TAMANHO_MAXIMO_ORIGEM) {
            throw new ValidacaoDeNegocio("origem", "deve ter no máximo " + TAMANHO_MAXIMO_ORIGEM + " caracteres");
        }
        return texto;
    }

    private static BigDecimal temperaturaNormalizada(BigDecimal temperatura) {
        if (temperatura == null) {
            throw new ValidacaoDeNegocio("temperatura", "deve ser informada");
        }
        if (temperatura.compareTo(TEMPERATURA_MINIMA) < 0 || temperatura.compareTo(TEMPERATURA_MAXIMA) > 0) {
            throw new ValidacaoDeNegocio("temperatura", "deve estar entre -999.9 e 999.9");
        }
        var normalizada = temperatura.stripTrailingZeros();
        if (normalizada.scale() > 1) {
            throw new ValidacaoDeNegocio("temperatura", "deve ter no máximo 1 casa decimal");
        }
        return normalizada.setScale(1);
    }

    public Long getId() {
        return id;
    }

    public long getCamaraId() {
        return camaraId;
    }

    public String getIdExterno() {
        return idExterno;
    }

    /** Instante da medição, em UTC, com precisão de microssegundos. */
    public OffsetDateTime getInstante() {
        return instante;
    }

    public BigDecimal getTemperatura() {
        return temperatura;
    }

    public String getOrigem() {
        return origem;
    }

    public int getPoliticaVersao() {
        return politicaVersao;
    }

    public ResultadoTermico getResultado() {
        return resultado;
    }

    public OffsetDateTime getAceitaEm() {
        return aceitaEm;
    }
}

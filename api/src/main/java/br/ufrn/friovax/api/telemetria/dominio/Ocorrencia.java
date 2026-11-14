package br.ufrn.friovax.api.telemetria.dominio;

import br.ufrn.friovax.api.compartilhado.dominio.EstadoIncompativel;
import br.ufrn.friovax.api.compartilhado.dominio.ValidacaoDeNegocio;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Ocorrência térmica: excursão detectada quando a sequência insegura atinge o limiar N (ADR 0003, seção 4). Guarda
 * as leituras que formaram a sequência (evidências) e a fotografia dos lotes da câmara no momento da abertura. Fica
 * aberta até uma resolução explícita, e no máximo uma pode estar aberta por câmara.
 */
public class Ocorrencia {

    public static final int TAMANHO_MAXIMO_JUSTIFICATIVA = 500;

    private Long id;
    private final long camaraId;
    private final int politicaVersao;
    private final long leituraAberturaId;
    private final OffsetDateTime abertaEm;
    private final List<Long> evidencias;
    private final List<LoteAfetado> lotesAfetados;
    private OffsetDateTime resolvidaEm;
    private UUID resolvidaPor;
    private String justificativa;
    private Long leituraResolucaoId;

    private Ocorrencia(Long id, long camaraId, int politicaVersao, long leituraAberturaId, OffsetDateTime abertaEm,
                       List<Long> evidencias, List<LoteAfetado> lotesAfetados, OffsetDateTime resolvidaEm,
                       UUID resolvidaPor, String justificativa, Long leituraResolucaoId) {
        this.id = id;
        this.camaraId = camaraId;
        this.politicaVersao = politicaVersao;
        this.leituraAberturaId = leituraAberturaId;
        this.abertaEm = abertaEm;
        this.evidencias = List.copyOf(evidencias);
        this.lotesAfetados = List.copyOf(lotesAfetados);
        this.resolvidaEm = resolvidaEm;
        this.resolvidaPor = resolvidaPor;
        this.justificativa = justificativa;
        this.leituraResolucaoId = leituraResolucaoId;
    }

    /**
     * Abre uma ocorrência na leitura que atingiu o limiar.
     *
     * @param evidencias             ids das leituras que formaram a sequência, em ordem; inclui a de abertura
     * @param lotesAfetados          fotografia dos lotes ativos da câmara neste momento (pode ser vazia)
     * @param existeOcorrenciaAberta se a câmara já tem uma ocorrência aberta: nesse caso, nada é aberto
     */
    public static Ocorrencia abrir(long camaraId, int politicaVersao, long leituraAberturaId,
                                   List<Long> evidencias, List<LoteAfetado> lotesAfetados,
                                   boolean existeOcorrenciaAberta, OffsetDateTime agora) {
        if (existeOcorrenciaAberta) {
            throw new OcorrenciaJaAberta(camaraId);
        }
        if (politicaVersao < 1) {
            throw new IllegalArgumentException("A versão da política deve ser positiva.");
        }
        Objects.requireNonNull(evidencias, "evidencias");
        if (evidencias.isEmpty() || !evidencias.contains(leituraAberturaId)) {
            throw new IllegalArgumentException("As evidências devem incluir a leitura que abriu a ocorrência.");
        }
        if (new HashSet<>(evidencias).size() != evidencias.size()) {
            throw new IllegalArgumentException("Uma leitura não pode aparecer duas vezes nas evidências.");
        }
        Objects.requireNonNull(lotesAfetados, "lotesAfetados");
        if (lotesAfetados.stream().map(LoteAfetado::loteId).distinct().count() != lotesAfetados.size()) {
            throw new IllegalArgumentException("Um lote não pode aparecer duas vezes na mesma ocorrência.");
        }
        return new Ocorrencia(null, camaraId, politicaVersao, leituraAberturaId,
                Objects.requireNonNull(agora, "agora"), evidencias, lotesAfetados, null, null, null, null);
    }

    /** Recria uma ocorrência já persistida, sem revalidar. Uso exclusivo dos adaptadores de persistência. */
    public static Ocorrencia reconstituir(long id, long camaraId, int politicaVersao, long leituraAberturaId,
                                          OffsetDateTime abertaEm, List<Long> evidencias,
                                          List<LoteAfetado> lotesAfetados, OffsetDateTime resolvidaEm,
                                          UUID resolvidaPor, String justificativa, Long leituraResolucaoId) {
        return new Ocorrencia(id, camaraId, politicaVersao, leituraAberturaId, abertaEm, evidencias, lotesAfetados,
                resolvidaEm, resolvidaPor, justificativa, leituraResolucaoId);
    }

    /**
     * Registra a resolução explícita. As condições sobre a última leitura (segura, posterior à abertura e da política
     * atual) são conferidas pelo caso de uso, que tem acesso às leituras (S2-12).
     *
     * @param leituraResolucaoId última leitura aceita no momento da resolução; o próximo episódio começa depois dela
     * @param resolvidaPor       administrador que resolveu, quando identificado
     */
    public void resolver(String justificativa, long leituraResolucaoId, UUID resolvidaPor, OffsetDateTime agora) {
        if (!isAberta()) {
            throw new EstadoIncompativel("A ocorrência " + id + " já foi resolvida.");
        }
        var texto = justificativa == null ? "" : justificativa.trim();
        if (texto.isEmpty()) {
            throw new ValidacaoDeNegocio("justificativa", "deve ser informada");
        }
        if (texto.length() > TAMANHO_MAXIMO_JUSTIFICATIVA) {
            throw new ValidacaoDeNegocio("justificativa", "deve ter no máximo " + TAMANHO_MAXIMO_JUSTIFICATIVA
                    + " caracteres");
        }
        Objects.requireNonNull(agora, "agora");
        if (agora.isBefore(abertaEm)) {
            throw new IllegalArgumentException("A resolução não pode ser anterior à abertura.");
        }
        this.justificativa = texto;
        this.leituraResolucaoId = leituraResolucaoId;
        this.resolvidaPor = resolvidaPor;
        this.resolvidaEm = agora;
    }

    /** Chamado pelo repositório ao gravar a ocorrência. */
    public void atribuirId(long id) {
        if (this.id != null) {
            throw new IllegalStateException("A ocorrência já possui id " + this.id);
        }
        this.id = id;
    }

    public boolean isAberta() {
        return resolvidaEm == null;
    }

    public Long getId() {
        return id;
    }

    public long getCamaraId() {
        return camaraId;
    }

    public int getPoliticaVersao() {
        return politicaVersao;
    }

    public long getLeituraAberturaId() {
        return leituraAberturaId;
    }

    public OffsetDateTime getAbertaEm() {
        return abertaEm;
    }

    /** Ids das leituras que formaram a sequência, na ordem em que ocorreram. */
    public List<Long> getEvidencias() {
        return evidencias;
    }

    public List<LoteAfetado> getLotesAfetados() {
        return lotesAfetados;
    }

    public Optional<OffsetDateTime> getResolvidaEm() {
        return Optional.ofNullable(resolvidaEm);
    }

    public Optional<UUID> getResolvidaPor() {
        return Optional.ofNullable(resolvidaPor);
    }

    public Optional<String> getJustificativa() {
        return Optional.ofNullable(justificativa);
    }

    public Optional<Long> getLeituraResolucaoId() {
        return Optional.ofNullable(leituraResolucaoId);
    }
}

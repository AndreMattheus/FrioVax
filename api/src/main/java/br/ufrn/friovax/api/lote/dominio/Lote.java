package br.ufrn.friovax.api.lote.dominio;

import br.ufrn.friovax.api.compartilhado.dominio.EstadoIncompativel;
import br.ufrn.friovax.api.compartilhado.dominio.Textos;
import br.ufrn.friovax.api.compartilhado.dominio.ValidacaoDeNegocio;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Lote de imunobiológico alocado em uma câmara. A verificação de capacidade e do estado da câmara de destino fica
 * em {@code Camara#verificarAlocacao}; aqui ficam apenas as regras do próprio lote.
 */
public class Lote {

    private static final Pattern FORMATO_CODIGO = Pattern.compile("[A-Z0-9/-]{1,40}");
    private static final int TAMANHO_MAXIMO_TEXTO = 100;

    private Long id;
    private final String codigo;
    private String imunobiologico;
    private String fabricante;
    private LocalDate validade;
    private int quantidade;
    private long camaraId;
    private EstadoLote estado;
    private boolean ativo;
    private final OffsetDateTime criadoEm;
    private OffsetDateTime atualizadoEm;

    private Lote(Long id, String codigo, String imunobiologico, String fabricante, LocalDate validade,
                 int quantidade, long camaraId, EstadoLote estado, boolean ativo,
                 OffsetDateTime criadoEm, OffsetDateTime atualizadoEm) {
        this.id = id;
        this.codigo = codigo;
        this.imunobiologico = imunobiologico;
        this.fabricante = fabricante;
        this.validade = validade;
        this.quantidade = quantidade;
        this.camaraId = camaraId;
        this.estado = estado;
        this.ativo = ativo;
        this.criadoEm = criadoEm;
        this.atualizadoEm = atualizadoEm;
    }

    /**
     * Cria um lote ativo e {@code DISPONIVEL} ainda não persistido.
     *
     * @param hoje data de referência no fuso {@code America/Fortaleza} (D9)
     */
    public static Lote novo(String codigo, String imunobiologico, String fabricante, LocalDate validade,
                            int quantidade, long camaraId, LocalDate hoje, OffsetDateTime agora) {
        Objects.requireNonNull(agora, "agora");
        var codigoNormalizado = Textos.codigo(codigo, FORMATO_CODIGO, "de 1 a 40 caracteres entre A-Z, 0-9, - e /");
        var nomeImunobiologico = Textos.obrigatorio("imunobiologico", imunobiologico, TAMANHO_MAXIMO_TEXTO);
        var nomeFabricante = Textos.obrigatorio("fabricante", fabricante, TAMANHO_MAXIMO_TEXTO);
        exigirValidadeFutura(validade, hoje);
        if (quantidade <= 0) {
            throw new ValidacaoDeNegocio("quantidade", "deve ser maior que zero");
        }
        return new Lote(null, codigoNormalizado, nomeImunobiologico, nomeFabricante, validade, quantidade,
                camaraId, EstadoLote.DISPONIVEL, true, agora, agora);
    }

    /** Recria um lote já persistido, sem revalidar os dados. Uso exclusivo dos adaptadores de persistência. */
    public static Lote reconstituir(long id, String codigo, String imunobiologico, String fabricante,
                                    LocalDate validade, int quantidade, long camaraId, EstadoLote estado,
                                    boolean ativo, OffsetDateTime criadoEm, OffsetDateTime atualizadoEm) {
        return new Lote(id, codigo, imunobiologico, fabricante, validade, quantidade, camaraId, estado, ativo,
                criadoEm, atualizadoEm);
    }

    /**
     * Substitui os campos editáveis (D8). A validade só precisa ser futura quando muda (D9). A capacidade da nova
     * câmara é verificada pelo caso de uso antes da chamada.
     */
    public void atualizar(String imunobiologico, String fabricante, LocalDate validade, long camaraId,
                          LocalDate hoje, OffsetDateTime agora) {
        exigirAtivo("alterado");
        var nomeImunobiologico = Textos.obrigatorio("imunobiologico", imunobiologico, TAMANHO_MAXIMO_TEXTO);
        var nomeFabricante = Textos.obrigatorio("fabricante", fabricante, TAMANHO_MAXIMO_TEXTO);
        if (!Objects.equals(validade, this.validade)) {
            exigirValidadeFutura(validade, hoje);
        }
        this.imunobiologico = nomeImunobiologico;
        this.fabricante = nomeFabricante;
        this.validade = validade;
        this.camaraId = camaraId;
        this.atualizadoEm = Objects.requireNonNull(agora, "agora");
    }

    /** Baixa parcial (§4.2). Ao chegar a zero, o lote passa a {@code ESGOTADO}. */
    public void darBaixa(int quantidade, MotivoBaixa motivo, OffsetDateTime agora) {
        exigirAtivo("baixado");
        if (estado == EstadoLote.DESCARTADO) {
            throw new EstadoIncompativel("O lote " + codigo + " foi descartado e não aceita baixas.");
        }
        if (motivo == null) {
            throw new ValidacaoDeNegocio("motivo", "deve ser informado");
        }
        if (quantidade <= 0 || quantidade > this.quantidade) {
            throw new ValidacaoDeNegocio("quantidade", "deve estar entre 1 e " + this.quantidade);
        }
        this.quantidade -= quantidade;
        if (this.quantidade == 0) {
            this.estado = EstadoLote.ESGOTADO;
        }
        this.atualizadoEm = Objects.requireNonNull(agora, "agora");
    }

    /** Descarte total (§4.2). Repetir num lote já descartado não altera nada. */
    public void descartar(OffsetDateTime agora) {
        exigirAtivo("descartado");
        if (estado == EstadoLote.DESCARTADO) {
            return;
        }
        this.estado = EstadoLote.DESCARTADO;
        this.atualizadoEm = Objects.requireNonNull(agora, "agora");
    }

    /** Remoção lógica (D12); libera a quantidade na ocupação da câmara. Repetir não altera nada. */
    public void inativar(OffsetDateTime agora) {
        if (!ativo) {
            return;
        }
        this.ativo = false;
        this.atualizadoEm = Objects.requireNonNull(agora, "agora");
    }

    /** Chamado pelo repositório ao gravar um lote novo. */
    public void atribuirId(long id) {
        if (this.id != null) {
            throw new IllegalStateException("O lote já possui id " + this.id);
        }
        this.id = id;
    }

    private static void exigirValidadeFutura(LocalDate validade, LocalDate hoje) {
        Objects.requireNonNull(hoje, "hoje");
        if (validade == null) {
            throw new ValidacaoDeNegocio("validade", "deve ser informada");
        }
        if (!validade.isAfter(hoje)) {
            throw new ValidacaoDeNegocio("validade", "deve ser posterior a " + hoje);
        }
    }

    private void exigirAtivo(String operacao) {
        if (!ativo) {
            throw new EstadoIncompativel("O lote " + codigo + " está inativo e não pode ser " + operacao + ".");
        }
    }

    public Long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getImunobiologico() {
        return imunobiologico;
    }

    public String getFabricante() {
        return fabricante;
    }

    public LocalDate getValidade() {
        return validade;
    }

    public int getQuantidade() {
        return quantidade;
    }

    public long getCamaraId() {
        return camaraId;
    }

    public EstadoLote getEstado() {
        return estado;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public OffsetDateTime getCriadoEm() {
        return criadoEm;
    }

    public OffsetDateTime getAtualizadoEm() {
        return atualizadoEm;
    }
}

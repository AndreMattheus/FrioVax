package br.ufrn.friovax.api.camara.dominio;

import br.ufrn.friovax.api.compartilhado.dominio.CapacidadeExcedida;
import br.ufrn.friovax.api.compartilhado.dominio.EstadoIncompativel;
import br.ufrn.friovax.api.compartilhado.dominio.Textos;
import br.ufrn.friovax.api.compartilhado.dominio.ValidacaoDeNegocio;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Câmara fria que armazena lotes. A ocupação não é guardada aqui: é calculada pelos lotes ativos (contrato, D6)
 * e informada às operações que dependem dela.
 */
public class Camara {

    private static final Pattern FORMATO_CODIGO = Pattern.compile("[A-Z0-9-]{3,20}");
    private static final int TAMANHO_MAXIMO_TEXTO = 100;

    private Long id;
    private final String codigo;
    private String nome;
    private String unidade;
    private int capacidade;
    private BigDecimal temperaturaMinima;
    private BigDecimal temperaturaMaxima;
    private EstadoCamara estado;
    private boolean ativo;
    private final OffsetDateTime criadoEm;
    private OffsetDateTime atualizadoEm;

    private Camara(Long id, String codigo, String nome, String unidade, int capacidade,
                   BigDecimal temperaturaMinima, BigDecimal temperaturaMaxima, EstadoCamara estado,
                   boolean ativo, OffsetDateTime criadoEm, OffsetDateTime atualizadoEm) {
        this.id = id;
        this.codigo = codigo;
        this.nome = nome;
        this.unidade = unidade;
        this.capacidade = capacidade;
        this.temperaturaMinima = temperaturaMinima;
        this.temperaturaMaxima = temperaturaMaxima;
        this.estado = estado;
        this.ativo = ativo;
        this.criadoEm = criadoEm;
        this.atualizadoEm = atualizadoEm;
    }

    /** Cria uma câmara ativa ainda não persistida, validando e normalizando os campos de entrada. */
    public static Camara nova(String codigo, String nome, String unidade, int capacidade,
                              BigDecimal temperaturaMinima, BigDecimal temperaturaMaxima,
                              EstadoCamara estado, OffsetDateTime agora) {
        Objects.requireNonNull(agora, "agora");
        var camara = new Camara(null,
                Textos.codigo(codigo, FORMATO_CODIGO, "de 3 a 20 caracteres entre A-Z, 0-9 e -"),
                null, null, 0, null, null, null, true, agora, agora);
        camara.aplicarCamposEditaveis(nome, unidade, capacidade, temperaturaMinima, temperaturaMaxima, estado);
        return camara;
    }

    /** Recria uma câmara já persistida, sem revalidar os dados. Uso exclusivo dos adaptadores de persistência. */
    public static Camara reconstituir(long id, String codigo, String nome, String unidade, int capacidade,
                                      BigDecimal temperaturaMinima, BigDecimal temperaturaMaxima,
                                      EstadoCamara estado, boolean ativo,
                                      OffsetDateTime criadoEm, OffsetDateTime atualizadoEm) {
        return new Camara(id, codigo, nome, unidade, capacidade, temperaturaMinima, temperaturaMaxima,
                estado, ativo, criadoEm, atualizadoEm);
    }

    /**
     * Substitui os campos editáveis (PUT). A capacidade não pode ficar abaixo da ocupação atual (D7) e a câmara
     * só sai de {@code OPERACIONAL} sem lotes ativos alocados (§3.1).
     */
    public void atualizar(String nome, String unidade, int capacidade,
                          BigDecimal temperaturaMinima, BigDecimal temperaturaMaxima,
                          EstadoCamara estado, long ocupacaoAtual, boolean possuiLotesAtivos,
                          OffsetDateTime agora) {
        exigirAtiva("alterada");
        var atualizada = new Camara(id, codigo, null, null, 0, null, null, null, ativo, criadoEm, atualizadoEm);
        atualizada.aplicarCamposEditaveis(nome, unidade, capacidade, temperaturaMinima, temperaturaMaxima, estado);
        if (capacidade < ocupacaoAtual) {
            throw new CapacidadeExcedida("A câmara " + codigo + " possui " + ocupacaoAtual
                    + " doses alocadas; a capacidade não pode ser reduzida para " + capacidade + ".");
        }
        if (atualizada.estado != this.estado && !atualizada.estado.aceitaLotes() && possuiLotesAtivos) {
            throw new EstadoIncompativel("A câmara " + codigo + " possui lotes ativos; mova os lotes antes de"
                    + " colocá-la em " + atualizada.estado + ".");
        }
        this.nome = atualizada.nome;
        this.unidade = atualizada.unidade;
        this.capacidade = atualizada.capacidade;
        this.temperaturaMinima = atualizada.temperaturaMinima;
        this.temperaturaMaxima = atualizada.temperaturaMaxima;
        this.estado = atualizada.estado;
        this.atualizadoEm = Objects.requireNonNull(agora, "agora");
    }

    /** Remoção lógica (D12). Repetir numa câmara inativa não faz nada; com lotes ativos, é rejeitada (D7). */
    public void inativar(boolean possuiLotesAtivos, OffsetDateTime agora) {
        if (!ativo) {
            return;
        }
        if (possuiLotesAtivos) {
            throw new EstadoIncompativel("A câmara " + codigo
                    + " possui lotes ativos; mova ou inative os lotes antes de inativá-la.");
        }
        this.ativo = false;
        this.atualizadoEm = Objects.requireNonNull(agora, "agora");
    }

    /**
     * Verifica se a câmara aceita mais {@code quantidade} doses (contrato, §3.2).
     *
     * @param ocupacaoAtual ocupação dos lotes ativos, já sem o lote que está sendo movido ou alterado
     */
    public void verificarAlocacao(long ocupacaoAtual, int quantidade) {
        exigirAtiva("receber lotes");
        if (!estado.aceitaLotes()) {
            throw new EstadoIncompativel("A câmara " + codigo + " está em " + estado + " e não aceita lotes.");
        }
        if (ocupacaoAtual + quantidade > capacidade) {
            throw new CapacidadeExcedida("A câmara " + codigo + " comporta " + capacidade
                    + " doses e já possui " + ocupacaoAtual + "; não é possível alocar mais " + quantidade + ".");
        }
    }

    /** Chamado pelo repositório ao gravar uma câmara nova. */
    public void atribuirId(long id) {
        if (this.id != null) {
            throw new IllegalStateException("A câmara já possui id " + this.id);
        }
        this.id = id;
    }

    private void aplicarCamposEditaveis(String nome, String unidade, int capacidade,
                                        BigDecimal temperaturaMinima, BigDecimal temperaturaMaxima,
                                        EstadoCamara estado) {
        this.nome = Textos.obrigatorio("nome", nome, TAMANHO_MAXIMO_TEXTO);
        this.unidade = Textos.obrigatorio("unidade", unidade, TAMANHO_MAXIMO_TEXTO);
        if (capacidade <= 0) {
            throw new ValidacaoDeNegocio("capacidade", "deve ser maior que zero");
        }
        this.capacidade = capacidade;
        this.temperaturaMinima = temperatura("temperaturaMinima", temperaturaMinima);
        this.temperaturaMaxima = temperatura("temperaturaMaxima", temperaturaMaxima);
        if (this.temperaturaMinima.compareTo(this.temperaturaMaxima) >= 0) {
            throw new ValidacaoDeNegocio("temperaturaMinima", "deve ser menor que temperaturaMaxima");
        }
        if (estado == null) {
            throw new ValidacaoDeNegocio("estado", "deve ser informado");
        }
        this.estado = estado;
    }

    private static BigDecimal temperatura(String campo, BigDecimal valor) {
        if (valor == null) {
            throw new ValidacaoDeNegocio(campo, "deve ser informada");
        }
        var normalizada = valor.stripTrailingZeros();
        if (normalizada.scale() > 1) {
            throw new ValidacaoDeNegocio(campo, "deve ter no máximo 1 casa decimal");
        }
        return normalizada.setScale(1);
    }

    private void exigirAtiva(String operacao) {
        if (!ativo) {
            throw new EstadoIncompativel("A câmara " + codigo + " está inativa e não pode ser " + operacao + ".");
        }
    }

    public Long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNome() {
        return nome;
    }

    public String getUnidade() {
        return unidade;
    }

    public int getCapacidade() {
        return capacidade;
    }

    public BigDecimal getTemperaturaMinima() {
        return temperaturaMinima;
    }

    public BigDecimal getTemperaturaMaxima() {
        return temperaturaMaxima;
    }

    public EstadoCamara getEstado() {
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

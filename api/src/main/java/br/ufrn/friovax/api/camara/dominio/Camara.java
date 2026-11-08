package br.ufrn.friovax.api.camara.dominio;

import br.ufrn.friovax.api.compartilhado.dominio.CapacidadeExcedida;
import br.ufrn.friovax.api.compartilhado.dominio.EstadoIncompativel;
import br.ufrn.friovax.api.compartilhado.dominio.Textos;
import br.ufrn.friovax.api.compartilhado.dominio.ValidacaoDeNegocio;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Câmara fria que armazena lotes. A ocupação não é guardada aqui: é calculada pelos lotes ativos (contrato, D6)
 * e informada às operações que dependem dela.
 *
 * A faixa térmica pertence à PoliticaTermica vigente, que é a única fonte oficial dos valores (ADR 0003,
 * seção 2). Tanto a edição da câmara quanto a futura rota de política passam por alterarPolitica.
 */
public class Camara {

    private static final Pattern FORMATO_CODIGO = Pattern.compile("[A-Z0-9-]{3,20}");
    private static final int TAMANHO_MAXIMO_TEXTO = 100;

    private Long id;
    private final String codigo;
    private String nome;
    private String unidade;
    private int capacidade;
    private PoliticaTermica politica;
    private EstadoTermico estadoTermico;
    private EstadoCamara estado;
    private boolean ativo;
    private final OffsetDateTime criadoEm;
    private OffsetDateTime atualizadoEm;

    private Camara(Long id, String codigo, String nome, String unidade, int capacidade, PoliticaTermica politica,
                   EstadoTermico estadoTermico, EstadoCamara estado, boolean ativo,
                   OffsetDateTime criadoEm, OffsetDateTime atualizadoEm) {
        this.id = id;
        this.codigo = codigo;
        this.nome = nome;
        this.unidade = unidade;
        this.capacidade = capacidade;
        this.politica = politica;
        this.estadoTermico = estadoTermico;
        this.estado = estado;
        this.ativo = ativo;
        this.criadoEm = criadoEm;
        this.atualizadoEm = atualizadoEm;
    }

    /**
     * Cria uma câmara ativa ainda não persistida, validando e normalizando os campos de entrada. A política começa na
     * versão 1, com o limiar inicial, e o estado térmico começa em SEM_LEITURAS.
     */
    public static Camara nova(String codigo, String nome, String unidade, int capacidade,
                              BigDecimal temperaturaMinima, BigDecimal temperaturaMaxima,
                              EstadoCamara estado, OffsetDateTime agora) {
        return nova(codigo, nome, unidade, capacidade, temperaturaMinima, temperaturaMaxima, estado, null, agora);
    }

    // Igual a nova registrando o autor da primeira versão da política.
    public static Camara nova(String codigo, String nome, String unidade, int capacidade,
                              BigDecimal temperaturaMinima, BigDecimal temperaturaMaxima,
                              EstadoCamara estado, UUID autorId, OffsetDateTime agora) {
        Objects.requireNonNull(agora, "agora");
        var camara = new Camara(null,
            Textos.codigo(codigo, FORMATO_CODIGO, "de 3 a 20 caracteres entre A-Z, 0-9 e -"),
            null, null, 0, null, EstadoTermico.SEM_LEITURAS, null, true, agora, agora);
        camara.aplicarCamposEditaveis(nome, unidade, capacidade, estado);
        camara.politica = PoliticaTermica.inicial(temperaturaMinima, temperaturaMaxima, autorId, agora);
        return camara;
    }

    // Recria uma câmara já persistida, sem revalidar os dados. Uso exclusivo dos adaptadores de persistência.
    public static Camara reconstituir(long id, String codigo, String nome, String unidade, int capacidade,
                                      PoliticaTermica politica, EstadoTermico estadoTermico,
                                      EstadoCamara estado, boolean ativo,
                                      OffsetDateTime criadoEm, OffsetDateTime atualizadoEm) {
        return new Camara(id, codigo, nome, unidade, capacidade, Objects.requireNonNull(politica, "politica"),
            Objects.requireNonNull(estadoTermico, "estadoTermico"), estado, ativo, criadoEm, atualizadoEm);
    }

    /**
     * Recria uma câmara a partir só da faixa, como ela existia antes do versionamento: política na versão 1, com o
     * limiar inicial, e estado térmico SEM_LEITURAS. Mantida para os testes que montam câmaras diretamente.
     */
    public static Camara reconstituir(long id, String codigo, String nome, String unidade, int capacidade,
                                      BigDecimal temperaturaMinima, BigDecimal temperaturaMaxima,
                                      EstadoCamara estado, boolean ativo,
                                      OffsetDateTime criadoEm, OffsetDateTime atualizadoEm) {
        var politica = PoliticaTermica.reconstituir(1, temperaturaMinima, temperaturaMaxima,
            PoliticaTermica.LIMIAR_INICIAL, null, criadoEm);
        return reconstituir(id, codigo, nome, unidade, capacidade, politica, EstadoTermico.SEM_LEITURAS,
            estado, ativo, criadoEm, atualizadoEm);
    }

    /**
     * Substitui os campos editáveis (PUT). A capacidade não pode ficar abaixo da ocupação atual (D7) e a câmara
     * só sai de {@code OPERACIONAL} sem lotes ativos alocados (§3.1). A faixa passa pela política: só uma mudança
     * real gera nova versão, e o limiar vigente é preservado.
     */
    public void atualizar(String nome, String unidade, int capacidade,
                          BigDecimal temperaturaMinima, BigDecimal temperaturaMaxima,
                          EstadoCamara estado, long ocupacaoAtual, boolean possuiLotesAtivos,
                          OffsetDateTime agora) {
        atualizar(nome, unidade, capacidade, temperaturaMinima, temperaturaMaxima, estado,
            ocupacaoAtual, possuiLotesAtivos, null, agora);
    }

    // Igual ao atualizar sem autor, registrando quem fez a alteração caso a faixa mude.
    public void atualizar(String nome, String unidade, int capacidade,
                          BigDecimal temperaturaMinima, BigDecimal temperaturaMaxima,
                          EstadoCamara estado, long ocupacaoAtual, boolean possuiLotesAtivos,
                          UUID autorId, OffsetDateTime agora) {
        exigirAtiva("alterada");
        Objects.requireNonNull(agora, "agora");

        // Valida tudo numa cópia antes de alterar qualquer campo: se algo falhar, nada muda.
        var atualizada = new Camara(id, codigo, null, null, 0, politica, estadoTermico, null, ativo,
            criadoEm, atualizadoEm);
        atualizada.aplicarCamposEditaveis(nome, unidade, capacidade, estado);
        var novaPolitica = politicaComFaixa(temperaturaMinima, temperaturaMaxima, politica.getLimiar(),
            autorId, agora);

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
        this.estado = atualizada.estado;
        aplicarPolitica(novaPolitica);
        this.atualizadoEm = agora;
    }

    /**
     * Altera faixa e limiar da política vigente. Valores iguais aos atuais não geram versão (retorna
     * false). Uma nova versão reinicia a sequência: sem ocorrência aberta, o estado térmico volta a
     * SEM_LEITURAS; com ocorrência aberta, EM_ALERTA é mantido (ADR 0003, seção 2).
     *
     * retorna true se uma nova versão foi criada
     */
    public boolean alterarPolitica(BigDecimal temperaturaMinima, BigDecimal temperaturaMaxima, int limiar,
                                   UUID autorId, OffsetDateTime agora) {
        exigirAtiva("alterada");
        Objects.requireNonNull(agora, "agora");
        var novaPolitica = politicaComFaixa(temperaturaMinima, temperaturaMaxima, limiar, autorId, agora);
        if (novaPolitica == politica) {
            return false;
        }
        aplicarPolitica(novaPolitica);
        this.atualizadoEm = agora;
        return true;
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

    /**
     * Valida a faixa e o limiar e devolve a política resultante: a própria vigente, quando nada mudou, ou a
     * próxima versão. Não altera a câmara.
     */
    private PoliticaTermica politicaComFaixa(BigDecimal temperaturaMinima, BigDecimal temperaturaMaxima,
                                             int limiar, UUID autorId, OffsetDateTime agora) {
        var faixa = PoliticaTermica.faixaNormalizada(temperaturaMinima, temperaturaMaxima);
        PoliticaTermica.limiarValidado(limiar);
        if (politica.temMesmosValores(faixa[0], faixa[1], limiar)) {
            return politica;
        }
        return politica.proximaVersao(faixa[0], faixa[1], limiar, autorId, agora);
    }

    private void aplicarPolitica(PoliticaTermica novaPolitica) {
        if (novaPolitica == politica) {
            return;
        }
        this.politica = novaPolitica;
        if (estadoTermico != EstadoTermico.EM_ALERTA) {
            this.estadoTermico = EstadoTermico.SEM_LEITURAS;
        }
    }

    private void aplicarCamposEditaveis(String nome, String unidade, int capacidade, EstadoCamara estado) {
        this.nome = Textos.obrigatorio("nome", nome, TAMANHO_MAXIMO_TEXTO);
        this.unidade = Textos.obrigatorio("unidade", unidade, TAMANHO_MAXIMO_TEXTO);
        if (capacidade <= 0) {
            throw new ValidacaoDeNegocio("capacidade", "deve ser maior que zero");
        }
        this.capacidade = capacidade;
        if (estado == null) {
            throw new ValidacaoDeNegocio("estado", "deve ser informado");
        }
        this.estado = estado;
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

    public PoliticaTermica getPolitica() {
        return politica;
    }

    public BigDecimal getTemperaturaMinima() {
        return politica.getTemperaturaMinima();
    }

    public BigDecimal getTemperaturaMaxima() {
        return politica.getTemperaturaMaxima();
    }

    public EstadoTermico getEstadoTermico() {
        return estadoTermico;
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

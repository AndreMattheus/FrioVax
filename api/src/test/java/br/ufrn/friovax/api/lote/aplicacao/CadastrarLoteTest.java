package br.ufrn.friovax.api.lote.aplicacao;

import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import br.ufrn.friovax.api.compartilhado.dominio.CapacidadeExcedida;
import br.ufrn.friovax.api.compartilhado.dominio.CodigoDuplicado;
import br.ufrn.friovax.api.compartilhado.dominio.EstadoIncompativel;
import br.ufrn.friovax.api.compartilhado.dominio.Paginacao;
import br.ufrn.friovax.api.compartilhado.dominio.RecursoNaoEncontrado;
import br.ufrn.friovax.api.compartilhado.dominio.ValidacaoDeNegocio;
import br.ufrn.friovax.api.lote.dominio.EstadoLote;
import br.ufrn.friovax.api.lote.dominio.Lote;
import br.ufrn.friovax.api.lote.dominio.LoteFiltro;
import br.ufrn.friovax.api.suporte.CamaraRepositoryEmMemoria;
import br.ufrn.friovax.api.suporte.LoteRepositoryEmMemoria;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CadastrarLoteTest {
    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 10, 2, 23, 0, 0, 0, ZoneOffset.ofHours(-3));
    private static final LocalDate HOJE = AGORA.toLocalDate();
    private static final LocalDate VALIDADE = LocalDate.of(2027, 3, 31);

    private final CamaraRepositoryEmMemoria camaras = new CamaraRepositoryEmMemoria();
    private final LoteRepositoryEmMemoria lotes = new LoteRepositoryEmMemoria();
    private final CadastrarLote casoDeUso = new CadastrarLote(lotes, camaras,
            Clock.fixed(AGORA.toInstant(), ZoneId.of("America/Fortaleza")));

    @Test
    void deveCadastrarLoteDisponivelNormalizandoOCodigo() {
        var camara = novaCamara("CAM-01", 5000, EstadoCamara.OPERACIONAL);

        var lote = casoDeUso.cadastrar(" fx2027a ", "Febre amarela", "Bio-Manguinhos", VALIDADE, 1200,
                camara.getId());

        assertNotNull(lote.getId());
        var gravado = lotes.buscarPorId(lote.getId()).orElseThrow();
        assertEquals("FX2027A", gravado.getCodigo());
        assertEquals(1200, gravado.getQuantidade());
        assertEquals(camara.getId(), gravado.getCamaraId());
        assertEquals(EstadoLote.DISPONIVEL, gravado.getEstado());
        assertTrue(gravado.isAtivo());
        assertEquals(AGORA.toInstant(), gravado.getCriadoEm().toInstant());
        assertEquals(1200, lotes.ocupacaoDaCamara(camara.getId()));
    }

    @Test
    void deveAceitarOcupacaoExatamenteIgualACapacidade() {
        var camara = novaCamara("CAM-02", 1000, EstadoCamara.OPERACIONAL);
        casoDeUso.cadastrar("L-01", "Vacina", "Fabricante", VALIDADE, 600, camara.getId());

        casoDeUso.cadastrar("L-02", "Vacina", "Fabricante", VALIDADE, 400, camara.getId());

        assertEquals(1000, lotes.ocupacaoDaCamara(camara.getId()));
    }

    @Test
    void deveRecusarExcedenteSemGravarOLote() {
        var camara = novaCamara("CAM-03", 1000, EstadoCamara.OPERACIONAL);
        casoDeUso.cadastrar("L-03", "Vacina", "Fabricante", VALIDADE, 600, camara.getId());

        assertThrows(CapacidadeExcedida.class,
                () -> casoDeUso.cadastrar("L-04", "Vacina", "Fabricante", VALIDADE, 401, camara.getId()));

        assertEquals(600, lotes.ocupacaoDaCamara(camara.getId()));
        assertFalse(lotes.existePorCodigo("L-04"));
    }

    @Test
    void deveDesconsiderarLotesInativosNaOcupacao() {
        var camara = novaCamara("CAM-04", 1000, EstadoCamara.OPERACIONAL);
        var antigo = casoDeUso.cadastrar("L-05", "Vacina", "Fabricante", VALIDADE, 1000, camara.getId());
        antigo.inativar(AGORA);
        lotes.salvar(antigo);

        casoDeUso.cadastrar("L-06", "Vacina", "Fabricante", VALIDADE, 1000, camara.getId());

        assertEquals(1000, lotes.ocupacaoDaCamara(camara.getId()));
    }

    @ParameterizedTest
    @EnumSource(value = EstadoCamara.class, names = {"MANUTENCAO", "DESATIVADA"})
    void deveRecusarCamaraForaDeOperacao(EstadoCamara estado) {
        var camara = novaCamara("CAM-" + estado.ordinal() + "X", 1000, estado);

        assertThrows(EstadoIncompativel.class,
                () -> casoDeUso.cadastrar("L-07", "Vacina", "Fabricante", VALIDADE, 10, camara.getId()));

        assertFalse(lotes.existePorCodigo("L-07"));
    }

    @Test
    void deveRecusarCamaraInativa() {
        var camara = novaCamara("CAM-05", 1000, EstadoCamara.OPERACIONAL);
        camara.inativar(false, AGORA);
        camaras.salvar(camara);

        assertThrows(EstadoIncompativel.class,
                () -> casoDeUso.cadastrar("L-08", "Vacina", "Fabricante", VALIDADE, 10, camara.getId()));
        assertFalse(lotes.existePorCodigo("L-08"));
        assertEquals(0, lotes.ocupacaoDaCamara(camara.getId()));
        assertEquals(0, lotes.listar(LoteFiltro.ativos(), Paginacao.padrao()).totalElementos());
    }

    @Test
    void deveIndicarCamaraInexistente() {
        assertThrows(RecursoNaoEncontrado.class,
                () -> casoDeUso.cadastrar("L-09", "Vacina", "Fabricante", VALIDADE, 10, 42));
        assertFalse(lotes.existePorCodigo("L-09"));
        assertEquals(0, lotes.listar(LoteFiltro.ativos(), Paginacao.padrao()).totalElementos());
    }

    @Test
    void deveRecusarCodigoJaUsadoMesmoPorLoteInativo() {
        var camara = novaCamara("CAM-06", 1000, EstadoCamara.OPERACIONAL);
        var existente = casoDeUso.cadastrar("L-10", "Vacina", "Fabricante", VALIDADE, 10, camara.getId());
        existente.inativar(AGORA);
        lotes.salvar(existente);

        assertThrows(CodigoDuplicado.class,
                () -> casoDeUso.cadastrar("l-10", "Vacina", "Fabricante", VALIDADE, 10, camara.getId()));
        assertFalse(lotes.buscarPorId(existente.getId()).orElseThrow().isAtivo());
        assertEquals(1, lotes.listar(new LoteFiltro(null, null, null, null, null, false),
                Paginacao.padrao()).totalElementos());
        assertEquals(0, lotes.listar(LoteFiltro.ativos(), Paginacao.padrao()).totalElementos());
    }

    @Test
    void deveExigirValidadeFuturaNoFusoDoRelogio() {
        var camara = novaCamara("CAM-07", 1000, EstadoCamara.OPERACIONAL);

        var erro = assertThrows(ValidacaoDeNegocio.class,
                () -> casoDeUso.cadastrar("L-11", "Vacina", "Fabricante", HOJE, 10, camara.getId()));

        assertEquals("validade", erro.campo());
        assertFalse(lotes.existePorCodigo("L-11"));
        assertEquals(0, lotes.ocupacaoDaCamara(camara.getId()));
        Lote lote = casoDeUso.cadastrar("L-12", "Vacina", "Fabricante", HOJE.plusDays(1), 10, camara.getId());
        assertEquals(HOJE.plusDays(1), lote.getValidade());
    }

    @ParameterizedTest
    @ValueSource(strings = {"l-ativo", " L-ATIVO ", " l-ativo "})
    void deveRejeitarDuplicidadeNormalizadaSemAlterarLoteAtivo(String codigo) {
        var camara = novaCamara("CAM-08", 1000, EstadoCamara.OPERACIONAL);
        var existente = casoDeUso.cadastrar("L-ATIVO", "Vacina", "Fabricante", VALIDADE, 10, camara.getId());

        assertThrows(CodigoDuplicado.class, () -> casoDeUso.cadastrar(codigo, "Outra vacina",
                "Outro fabricante", VALIDADE.plusDays(1), 20, camara.getId()));

        var salvo = lotes.buscarPorId(existente.getId()).orElseThrow();
        assertEquals(existente.getId(), salvo.getId());
        assertEquals("L-ATIVO", salvo.getCodigo());
        assertEquals("Vacina", salvo.getImunobiologico());
        assertEquals("Fabricante", salvo.getFabricante());
        assertEquals(VALIDADE, salvo.getValidade());
        assertEquals(10, salvo.getQuantidade());
        assertEquals(camara.getId(), salvo.getCamaraId());
        assertEquals(EstadoLote.DISPONIVEL, salvo.getEstado());
        assertTrue(salvo.isAtivo());
        assertEquals(AGORA, salvo.getCriadoEm());
        assertEquals(AGORA, salvo.getAtualizadoEm());
        assertEquals(10, lotes.ocupacaoDaCamara(camara.getId()));
        assertEquals(1, lotes.listar(LoteFiltro.ativos(), Paginacao.padrao()).totalElementos());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void deveRejeitarQuantidadeInvalidaSemGravar(int quantidade) {
        var camara = novaCamara("CAM-09", 1000, EstadoCamara.OPERACIONAL);

        var erro = assertThrows(ValidacaoDeNegocio.class, () -> casoDeUso.cadastrar("L-INVALIDO",
                "Vacina", "Fabricante", VALIDADE, quantidade, camara.getId()));

        assertEquals("quantidade", erro.campo());
        assertFalse(lotes.existePorCodigo("L-INVALIDO"));
        assertEquals(0, lotes.ocupacaoDaCamara(camara.getId()));
        assertEquals(0, lotes.listar(LoteFiltro.ativos(), Paginacao.padrao()).totalElementos());
    }

    private Camara novaCamara(String codigo, int capacidade, EstadoCamara estado) {
        return camaras.salvar(Camara.nova(codigo, "Câmara", "UBS Centro", capacidade,
                new BigDecimal("2.0"), new BigDecimal("8.0"), estado, AGORA.minusDays(1)));
    }
}

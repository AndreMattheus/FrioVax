package br.ufrn.friovax.api.lote.aplicacao;

import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import br.ufrn.friovax.api.compartilhado.dominio.CapacidadeExcedida;
import br.ufrn.friovax.api.compartilhado.dominio.EstadoIncompativel;
import br.ufrn.friovax.api.compartilhado.dominio.RecursoNaoEncontrado;
import br.ufrn.friovax.api.compartilhado.dominio.ValidacaoDeNegocio;
import br.ufrn.friovax.api.lote.dominio.Lote;
import br.ufrn.friovax.api.lote.dominio.MotivoBaixa;
import br.ufrn.friovax.api.suporte.CamaraRepositoryEmMemoria;
import br.ufrn.friovax.api.suporte.LoteRepositoryEmMemoria;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtualizarLoteTest {
    // Já é dia 10 em UTC, mas ainda é dia 9 em Fortaleza.
    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-10-10T01:00:00Z"),
            ZoneId.of("America/Fortaleza"));
    private static final OffsetDateTime AGORA = OffsetDateTime.now(RELOGIO);
    private static final OffsetDateTime CRIACAO = AGORA.minusDays(10);
    private static final LocalDate HOJE = LocalDate.now(RELOGIO);
    private final CamaraRepositoryEmMemoria camaras = new CamaraRepositoryEmMemoria();
    private final LoteRepositoryEmMemoria lotes = new LoteRepositoryEmMemoria();
    private final AtualizarLote casoDeUso = new AtualizarLote(lotes, camaras, RELOGIO);

    @Test
    void deveAtualizarSemDuplaContagemAteACapacidadeExata() {
        var camara = camara("CAM-01", 100, EstadoCamara.OPERACIONAL);
        var lote = lote("L1", 30, camara);
        lote("L2", 50, camara);

        var resultado = casoDeUso.atualizar(lote.getId(), " Outra vacina ", " Outro fabricante ",
                HOJE.plusDays(1), 50, camara.getId());

        var salvo = lotes.buscarPorId(lote.getId()).orElseThrow();
        assertMesmoLote(resultado, salvo);
        assertAll(
                () -> assertEquals(lote.getId(), salvo.getId()),
                () -> assertEquals("L1", salvo.getCodigo()),
                () -> assertEquals("Outra vacina", salvo.getImunobiologico()),
                () -> assertEquals("Outro fabricante", salvo.getFabricante()),
                () -> assertEquals(HOJE.plusDays(1), salvo.getValidade()),
                () -> assertEquals(50, salvo.getQuantidade()),
                () -> assertEquals(camara.getId(), salvo.getCamaraId()),
                () -> assertEquals(lote.getEstado(), salvo.getEstado()),
                () -> assertTrue(salvo.isAtivo()),
                () -> assertEquals(CRIACAO, salvo.getCriadoEm()),
                () -> assertEquals(AGORA, salvo.getAtualizadoEm()),
                () -> assertEquals(100, lotes.ocupacaoDaCamara(camara.getId())));
    }

    @Test
    void deveTransferirParaDestinoComCapacidadeExataIgnorandoLotesInativos() {
        var origem = camara("ORIGEM", 100, EstadoCamara.MANUTENCAO);
        var destino = camara("DESTINO", 100, EstadoCamara.OPERACIONAL);
        var lote = lote("L1", 30, origem);
        lote("L2", 20, origem);
        lote("L3", 60, destino);
        var inativo = lote("L4", 100, destino);
        inativo.inativar(CRIACAO.plusHours(1));
        lotes.salvar(inativo);

        casoDeUso.atualizar(lote.getId(), "Vacina", "Fabricante", lote.getValidade(), 40, destino.getId());

        assertEquals(20, lotes.ocupacaoDaCamara(origem.getId()));
        assertEquals(100, lotes.ocupacaoDaCamara(destino.getId()));
        assertEquals(destino.getId(), lotes.buscarPorId(lote.getId()).orElseThrow().getCamaraId());
        assertEquals(100, lotes.buscarPorId(inativo.getId()).orElseThrow().getQuantidade());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void deveRejeitarExcessoSemAlterarDadosOuOcupacoes(boolean transferir) {
        var origem = camara("ORIGEM", 100, EstadoCamara.OPERACIONAL);
        var destino = camara("DESTINO", 100, EstadoCamara.OPERACIONAL);
        var lote = lote("L1", 30, origem);
        lote("L2", 50, origem);
        lote("L3", 50, destino);

        assertThrows(CapacidadeExcedida.class, () -> casoDeUso.atualizar(lote.getId(), "Outra vacina",
                "Outro fabricante", HOJE.plusDays(1), 51, transferir ? destino.getId() : origem.getId()));

        assertMesmoLote(lote, lotes.buscarPorId(lote.getId()).orElseThrow());
        assertEquals(80, lotes.ocupacaoDaCamara(origem.getId()));
        assertEquals(50, lotes.ocupacaoDaCamara(destino.getId()));
    }

    @ParameterizedTest
    @EnumSource(value = EstadoCamara.class, names = {"MANUTENCAO", "DESATIVADA"})
    void deveRejeitarDestinoNaoOperacional(EstadoCamara estado) {
        var origem = camara("ORIGEM", 100, EstadoCamara.OPERACIONAL);
        var destino = camara("DESTINO", 100, estado);
        var lote = lote("L1", 30, origem);

        assertThrows(EstadoIncompativel.class, () -> alterar(lote, destino.getId()));

        assertRejeicao(lote, origem.getId(), destino.getId());
    }

    @Test
    void deveRejeitarDestinoInativo() {
        var origem = camara("ORIGEM", 100, EstadoCamara.OPERACIONAL);
        var destino = camara("DESTINO", 100, EstadoCamara.OPERACIONAL);
        destino.inativar(false, CRIACAO);
        camaras.salvar(destino);
        var lote = lote("L1", 30, origem);

        assertThrows(EstadoIncompativel.class, () -> alterar(lote, destino.getId()));

        assertRejeicao(lote, origem.getId(), destino.getId());
    }

    @Test
    void deveRejeitarDestinoInexistente() {
        var origem = camara("ORIGEM", 100, EstadoCamara.OPERACIONAL);
        var lote = lote("L1", 30, origem);

        assertThrows(RecursoNaoEncontrado.class, () -> alterar(lote, 999));

        assertRejeicao(lote, origem.getId(), 999);
    }

    @Test
    void deveRejeitarLoteInexistenteSemAlterarOutroRegistro() {
        var camara = camara("CAM-01", 100, EstadoCamara.OPERACIONAL);
        var lote = lote("L1", 30, camara);

        assertThrows(RecursoNaoEncontrado.class, () -> casoDeUso.atualizar(999, "Outra vacina",
                "Outro fabricante", HOJE.plusDays(1), 40, camara.getId()));

        assertMesmoLote(lote, lotes.buscarPorId(lote.getId()).orElseThrow());
        assertEquals(30, lotes.ocupacaoDaCamara(camara.getId()));
        assertTrue(lotes.buscarPorId(999).isEmpty());
    }

    @Test
    void deveRejeitarLoteInativo() {
        var origem = camara("ORIGEM", 100, EstadoCamara.OPERACIONAL);
        var destino = camara("DESTINO", 100, EstadoCamara.OPERACIONAL);
        var lote = lote("L1", 30, origem);
        lote.inativar(CRIACAO.plusHours(1));
        lotes.salvar(lote);

        assertThrows(EstadoIncompativel.class, () -> alterar(lote, destino.getId()));

        assertMesmoLote(lote, lotes.buscarPorId(lote.getId()).orElseThrow());
        assertEquals(0, lotes.ocupacaoDaCamara(origem.getId()));
        assertEquals(0, lotes.ocupacaoDaCamara(destino.getId()));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 29})
    void deveRejeitarReducaoSemAlterarDados(int quantidade) {
        var origem = camara("ORIGEM", 100, EstadoCamara.OPERACIONAL);
        var destino = camara("DESTINO", 100, EstadoCamara.OPERACIONAL);
        var lote = lote("L1", 30, origem);

        var erro = assertThrows(ValidacaoDeNegocio.class, () -> casoDeUso.atualizar(lote.getId(),
                "Outra vacina", "Outro fabricante", HOJE.plusDays(1), quantidade, destino.getId()));

        assertEquals("quantidade", erro.campo());
        assertRejeicao(lote, origem.getId(), destino.getId());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"2026-10-08", "2026-10-09"})
    void deveRejeitarNovaValidadeNaoFuturaNoFusoLocal(String data) {
        var origem = camara("ORIGEM", 100, EstadoCamara.OPERACIONAL);
        var destino = camara("DESTINO", 100, EstadoCamara.OPERACIONAL);
        var lote = lote("L1", 30, origem);

        var erro = assertThrows(ValidacaoDeNegocio.class, () -> casoDeUso.atualizar(lote.getId(),
                "Outra vacina", "Outro fabricante", data == null ? null : LocalDate.parse(data), 40, destino.getId()));

        assertEquals("validade", erro.campo());
        assertRejeicao(lote, origem.getId(), destino.getId());
    }

    @Test
    void devePermitirEditarLoteVencidoMantendoValidade() {
        var camara = camara("CAM-01", 100, EstadoCamara.OPERACIONAL);
        var lote = lotes.salvar(Lote.novo("L1", "Vacina", "Fabricante", HOJE.minusDays(1),
                30, camara.getId(), CRIACAO.toLocalDate(), CRIACAO));

        var resultado = casoDeUso.atualizar(lote.getId(), "Outra vacina", "Fabricante",
                lote.getValidade(), 30, camara.getId());

        assertEquals(lote.getValidade(), resultado.getValidade());
        assertEquals("Outra vacina", resultado.getImunobiologico());
        assertEquals(AGORA, resultado.getAtualizadoEm());
        assertMesmoLote(resultado, lotes.buscarPorId(lote.getId()).orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void devePreservarEstadoSemPermitirNovasDoses(boolean esgotado) {
        var camara = camara("CAM-01", 100, EstadoCamara.OPERACIONAL);
        var lote = lote("L1", 30, camara);
        if (esgotado) {
            lote.darBaixa(30, MotivoBaixa.ADMINISTRADA, CRIACAO.plusHours(1));
        } else {
            lote.descartar(CRIACAO.plusHours(1));
        }
        lotes.salvar(lote);
        var quantidade = lote.getQuantidade();
        var editado = casoDeUso.atualizar(lote.getId(), "Outra vacina", "Fabricante",
                lote.getValidade(), quantidade, camara.getId());
        assertEquals(lote.getEstado(), editado.getEstado());
        assertEquals(quantidade, editado.getQuantidade());
        assertMesmoLote(editado, lotes.buscarPorId(lote.getId()).orElseThrow());

        assertThrows(EstadoIncompativel.class, () -> casoDeUso.atualizar(lote.getId(), "Terceira vacina",
                "Outro fabricante", HOJE.plusDays(1), quantidade + 1, camara.getId()));

        assertMesmoLote(editado, lotes.buscarPorId(lote.getId()).orElseThrow());
        assertEquals(quantidade, lotes.ocupacaoDaCamara(camara.getId()));
    }

    private void alterar(Lote lote, long destino) {
        casoDeUso.atualizar(lote.getId(), "Outra vacina", "Outro fabricante", HOJE.plusDays(1), 40, destino);
    }

    private void assertRejeicao(Lote anterior, long origem, long destino) {
        assertMesmoLote(anterior, lotes.buscarPorId(anterior.getId()).orElseThrow());
        assertEquals(30, lotes.ocupacaoDaCamara(origem));
        assertEquals(0, lotes.ocupacaoDaCamara(destino));
    }

    private static void assertMesmoLote(Lote esperado, Lote atual) {
        assertAll(
                () -> assertEquals(esperado.getId(), atual.getId(), "id"),
                () -> assertEquals(esperado.getCodigo(), atual.getCodigo(), "codigo"),
                () -> assertEquals(esperado.getImunobiologico(), atual.getImunobiologico(), "imunobiologico"),
                () -> assertEquals(esperado.getFabricante(), atual.getFabricante(), "fabricante"),
                () -> assertEquals(esperado.getValidade(), atual.getValidade(), "validade"),
                () -> assertEquals(esperado.getQuantidade(), atual.getQuantidade(), "quantidade"),
                () -> assertEquals(esperado.getCamaraId(), atual.getCamaraId(), "camaraId"),
                () -> assertEquals(esperado.getEstado(), atual.getEstado(), "estado"),
                () -> assertEquals(esperado.isAtivo(), atual.isAtivo(), "ativo"),
                () -> assertEquals(esperado.getCriadoEm(), atual.getCriadoEm(), "criadoEm"),
                () -> assertEquals(esperado.getAtualizadoEm(), atual.getAtualizadoEm(), "atualizadoEm"));
    }

    private Camara camara(String codigo, int capacidade, EstadoCamara estado) {
        return camaras.salvar(Camara.nova(codigo, "Câmara", "UBS", capacidade,
                new BigDecimal("2.0"), new BigDecimal("8.0"), estado, CRIACAO));
    }

    private Lote lote(String codigo, int quantidade, Camara camara) {
        return lotes.salvar(Lote.novo(codigo, "Vacina", "Fabricante", HOJE.plusDays(20),
                quantidade, camara.getId(), CRIACAO.toLocalDate(), CRIACAO));
    }
}

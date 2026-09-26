package br.ufrn.friovax.api.lote.dominio;

import br.ufrn.friovax.api.compartilhado.dominio.EstadoIncompativel;
import br.ufrn.friovax.api.compartilhado.dominio.ValidacaoDeNegocio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoteTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 9, 25);
    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 9, 25, 11, 0, 0, 0, ZoneOffset.ofHours(-3));
    private static final OffsetDateTime DEPOIS = AGORA.plusHours(1);

    private static Lote lote(int quantidade) {
        return Lote.novo(" fx2027a/1 ", " Febre amarela ", "Bio-Manguinhos", LocalDate.of(2027, 3, 31),
                quantidade, 1L, HOJE, AGORA);
    }

    @Test
    void deveCriarLoteDisponivelComCamposNormalizados() {
        var lote = lote(1200);

        assertNull(lote.getId());
        assertEquals("FX2027A/1", lote.getCodigo());
        assertEquals("Febre amarela", lote.getImunobiologico());
        assertEquals(EstadoLote.DISPONIVEL, lote.getEstado());
        assertEquals(1200, lote.getQuantidade());
        assertEquals(AGORA, lote.getCriadoEm());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "LOTE_1", "LOTE 1"})
    void deveRejeitarCodigoInvalido(String codigo) {
        var erro = assertThrows(ValidacaoDeNegocio.class, () -> Lote.novo(codigo, "Vacina", "Fabricante",
                HOJE.plusDays(1), 10, 1L, HOJE, AGORA));

        assertEquals("codigo", erro.campo());
    }

    @Test
    void deveRejeitarCodigoComMaisDe40Caracteres() {
        assertThrows(ValidacaoDeNegocio.class, () -> Lote.novo("L".repeat(41), "Vacina", "Fabricante",
                HOJE.plusDays(1), 10, 1L, HOJE, AGORA));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -5})
    void deveRejeitarQuantidadeNaoPositiva(int quantidade) {
        assertEquals("quantidade", assertThrows(ValidacaoDeNegocio.class, () -> lote(quantidade)).campo());
    }

    @Test
    void deveExigirValidadePosteriorAHoje() {
        assertEquals("validade", assertThrows(ValidacaoDeNegocio.class, () -> Lote.novo("L1", "Vacina",
                "Fabricante", HOJE, 10, 1L, HOJE, AGORA)).campo());
        assertEquals(HOJE.plusDays(1), Lote.novo("L1", "Vacina", "Fabricante", HOJE.plusDays(1), 10, 1L, HOJE,
                AGORA).getValidade());
    }

    @Test
    void deveAtualizarCamposEditaveisETrocarDeCamara() {
        var lote = lote(1200);

        lote.atualizar("Febre amarela", "Outro fabricante", LocalDate.of(2027, 6, 30), 2L, HOJE, DEPOIS);

        assertEquals("Outro fabricante", lote.getFabricante());
        assertEquals(2L, lote.getCamaraId());
        assertEquals(1200, lote.getQuantidade());
        assertEquals(DEPOIS, lote.getAtualizadoEm());
    }

    @Test
    void devePermitirEditarLoteVencidoQuandoValidadeNaoMuda() {
        var lote = lote(1200);
        var depoisDoVencimento = LocalDate.of(2027, 4, 1);

        lote.atualizar("Febre amarela", "Bio-Manguinhos", LocalDate.of(2027, 3, 31), 1L, depoisDoVencimento, DEPOIS);

        assertThrows(ValidacaoDeNegocio.class, () -> lote.atualizar("Febre amarela", "Bio-Manguinhos",
                LocalDate.of(2027, 4, 1), 1L, depoisDoVencimento, DEPOIS));
    }

    @Test
    void deveManterDadosQuandoAtualizacaoEhRejeitada() {
        var lote = lote(1200);

        assertThrows(ValidacaoDeNegocio.class, () -> lote.atualizar("Outra vacina", "", HOJE.plusDays(10), 2L,
                HOJE, DEPOIS));

        assertEquals("Febre amarela", lote.getImunobiologico());
        assertEquals(1L, lote.getCamaraId());
    }

    @Test
    void deveDarBaixaParcialEEsgotarAoChegarAZero() {
        var lote = lote(10);

        lote.darBaixa(4, MotivoBaixa.ADMINISTRADA, DEPOIS);
        assertEquals(6, lote.getQuantidade());
        assertEquals(EstadoLote.DISPONIVEL, lote.getEstado());

        lote.darBaixa(6, MotivoBaixa.PERDA, DEPOIS);
        assertEquals(0, lote.getQuantidade());
        assertEquals(EstadoLote.ESGOTADO, lote.getEstado());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 11})
    void deveRejeitarBaixaForaDoIntervalo(int quantidade) {
        var lote = lote(10);

        assertEquals("quantidade", assertThrows(ValidacaoDeNegocio.class,
                () -> lote.darBaixa(quantidade, MotivoBaixa.ADMINISTRADA, DEPOIS)).campo());
        assertEquals(10, lote.getQuantidade());
    }

    @Test
    void deveExigirMotivoDaBaixa() {
        assertEquals("motivo", assertThrows(ValidacaoDeNegocio.class,
                () -> lote(10).darBaixa(1, null, DEPOIS)).campo());
    }

    @Test
    void deveDescartarDeFormaIdempotenteEImpedirBaixas() {
        var lote = lote(10);

        lote.descartar(DEPOIS);
        lote.descartar(DEPOIS.plusHours(1));

        assertEquals(EstadoLote.DESCARTADO, lote.getEstado());
        assertEquals(10, lote.getQuantidade());
        assertEquals(DEPOIS, lote.getAtualizadoEm());
        assertThrows(EstadoIncompativel.class, () -> lote.darBaixa(1, MotivoBaixa.PERDA, DEPOIS));
    }

    @Test
    void deveImpedirOperacoesEmLoteInativo() {
        var lote = lote(10);
        lote.inativar(DEPOIS);
        lote.inativar(DEPOIS.plusHours(1));

        assertFalse(lote.isAtivo());
        assertEquals(DEPOIS, lote.getAtualizadoEm());
        assertThrows(EstadoIncompativel.class, () -> lote.atualizar("Vacina", "Fabricante", HOJE.plusDays(1), 1L,
                HOJE, DEPOIS));
        assertThrows(EstadoIncompativel.class, () -> lote.darBaixa(1, MotivoBaixa.PERDA, DEPOIS));
        assertThrows(EstadoIncompativel.class, () -> lote.descartar(DEPOIS));
    }
}

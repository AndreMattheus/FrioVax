package br.ufrn.friovax.api.lote.dominio;

import br.ufrn.friovax.api.compartilhado.dominio.EstadoIncompativel;
import br.ufrn.friovax.api.compartilhado.dominio.ValidacaoDeNegocio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertAll;
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
    @NullSource
    @ValueSource(strings = {"", "   ", "LOTE_1", "LOTE 1"})
    void deveRejeitarCodigoInvalido(String codigo) {
        var erro = assertThrows(ValidacaoDeNegocio.class, () -> Lote.novo(codigo, "Vacina", "Fabricante",
                HOJE.plusDays(1), 10, 1L, HOJE, AGORA));

        assertEquals("codigo", erro.campo());
    }

    @Test
    void deveRejeitarCodigoComMaisDe40Caracteres() {
        assertEquals("codigo", assertThrows(ValidacaoDeNegocio.class, () -> Lote.novo(
                " " + "L".repeat(41) + " ", "Vacina", "Fabricante",
                HOJE.plusDays(1), 10, 1L, HOJE, AGORA)).campo());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 40})
    void deveAceitarLimitesDoCodigoDepoisDaNormalizacao(int tamanho) {
        var lote = Lote.novo(" " + "a".repeat(tamanho) + " ", "Vacina", "Fabricante",
                HOJE.plusDays(1), 1, 1L, HOJE, AGORA);

        assertEquals("A".repeat(tamanho), lote.getCodigo());
        assertEquals(1, lote.getQuantidade());
    }

    @ParameterizedTest
    @CsvSource(value = {"imunobiologico,NULL", "imunobiologico,''", "imunobiologico,'   '",
            "fabricante,NULL", "fabricante,''", "fabricante,'   '"}, nullValues = "NULL")
    void deveExigirTextosObrigatoriosNaCriacaoENaAtualizacao(String campo, String valor) {
        var imunobiologico = campo.equals("imunobiologico") ? valor : "Outra vacina";
        var fabricante = campo.equals("fabricante") ? valor : "Outro fabricante";
        assertEquals(campo, assertThrows(ValidacaoDeNegocio.class, () -> Lote.novo("L1",
                imunobiologico, fabricante, HOJE.plusDays(1), 10, 1L, HOJE, AGORA)).campo());

        var lote = lote(10);
        lote.atribuirId(7);
        var anterior = copia(lote);
        assertEquals(campo, assertThrows(ValidacaoDeNegocio.class, () -> lote.atualizar(
                imunobiologico, fabricante, HOJE.plusDays(10), 20, 2L, HOJE, DEPOIS)).campo());
        assertMesmoLote(anterior, lote);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 100})
    void deveAceitarLimitesDosTextosDepoisDoTrim(int tamanho) {
        var texto = "x".repeat(tamanho);
        var lote = Lote.novo("L1", " " + texto + " ", " " + texto + " ",
                HOJE.plusDays(1), 10, 1L, HOJE, AGORA);

        assertEquals(texto, lote.getImunobiologico());
        assertEquals(texto, lote.getFabricante());
        lote.atualizar(" " + texto + " ", " " + texto + " ", HOJE.plusDays(2), 20, 2L, HOJE, DEPOIS);
        assertEquals(texto, lote.getImunobiologico());
        assertEquals(texto, lote.getFabricante());
        assertEquals(DEPOIS, lote.getAtualizadoEm());
    }

    @ParameterizedTest
    @ValueSource(strings = {"imunobiologico", "fabricante"})
    void deveRejeitarTextosLongosSemAlterarOLote(String campo) {
        var longo = " " + "x".repeat(101) + " ";
        var imunobiologico = campo.equals("imunobiologico") ? longo : "Outra vacina";
        var fabricante = campo.equals("fabricante") ? longo : "Outro fabricante";
        assertEquals(campo, assertThrows(ValidacaoDeNegocio.class, () -> Lote.novo("L1",
                imunobiologico, fabricante, HOJE.plusDays(1), 10, 1L, HOJE, AGORA)).campo());

        var lote = lote(10);
        lote.atribuirId(7);
        var anterior = copia(lote);
        assertEquals(campo, assertThrows(ValidacaoDeNegocio.class, () -> lote.atualizar(
                imunobiologico, fabricante, HOJE.plusDays(10), 20, 2L, HOJE, DEPOIS)).campo());
        assertMesmoLote(anterior, lote);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"2026-09-24", "2026-09-25"})
    void deveRejeitarValidadeAusenteOuNaoFuturaSemAlterarOLote(String data) {
        var validade = data == null ? null : LocalDate.parse(data);
        assertEquals("validade", assertThrows(ValidacaoDeNegocio.class, () -> Lote.novo("L1",
                "Vacina", "Fabricante", validade, 10, 1L, HOJE, AGORA)).campo());

        var lote = lote(10);
        lote.atribuirId(7);
        var anterior = copia(lote);
        assertEquals("validade", assertThrows(ValidacaoDeNegocio.class, () -> lote.atualizar(
                "Outra vacina", "Outro fabricante", validade, 20, 2L, HOJE, DEPOIS)).campo());
        assertMesmoLote(anterior, lote);
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

        lote.atualizar("Febre amarela", "Outro fabricante", LocalDate.of(2027, 6, 30), 1200, 2L, HOJE, DEPOIS);

        assertEquals("Outro fabricante", lote.getFabricante());
        assertEquals(2L, lote.getCamaraId());
        assertEquals(1200, lote.getQuantidade());
        assertEquals(DEPOIS, lote.getAtualizadoEm());
    }

    @Test
    void devePermitirEditarLoteVencidoQuandoValidadeNaoMuda() {
        var lote = lote(1200);
        lote.atribuirId(7);
        var depoisDoVencimento = LocalDate.of(2027, 4, 1);

        lote.atualizar("Febre amarela", "Bio-Manguinhos", LocalDate.of(2027, 3, 31), 1200, 1L, depoisDoVencimento, DEPOIS);

        assertEquals(LocalDate.of(2027, 3, 31), lote.getValidade());
        assertEquals(DEPOIS, lote.getAtualizadoEm());
        var anterior = copia(lote);
        assertThrows(ValidacaoDeNegocio.class, () -> lote.atualizar("Febre amarela", "Bio-Manguinhos",
                LocalDate.of(2027, 4, 1), 1200, 1L, depoisDoVencimento, DEPOIS.plusHours(1)));
        assertMesmoLote(anterior, lote);
    }

    @Test
    void deveManterDadosQuandoAtualizacaoEhRejeitada() {
        var lote = lote(1200);
        lote.atribuirId(7);
        var anterior = copia(lote);

        assertThrows(ValidacaoDeNegocio.class, () -> lote.atualizar("Outra vacina", "", HOJE.plusDays(10), 1200, 2L,
                HOJE, DEPOIS));

        assertMesmoLote(anterior, lote);
    }

    @Test
    void devePermitirAumentoMasExigirBaixaParaReducao() {
        var lote = lote(10);
        lote.atribuirId(7);

        lote.atualizar("Febre amarela", "Bio-Manguinhos", lote.getValidade(), 20, 1L, HOJE, DEPOIS);

        assertEquals(20, lote.getQuantidade());
        var anterior = copia(lote);
        assertEquals("quantidade", assertThrows(ValidacaoDeNegocio.class, () -> lote.atualizar(
                "Outra vacina", "Outro fabricante", lote.getValidade(), 19, 2L, HOJE, DEPOIS.plusHours(1))).campo());
        assertMesmoLote(anterior, lote);
    }

    @Test
    void loteEsgotadoPodeSerEditadoSemReceberNovasDoses() {
        var lote = lote(10);
        lote.atribuirId(7);
        lote.darBaixa(10, MotivoBaixa.ADMINISTRADA, DEPOIS);

        lote.atualizar("Outra vacina", "Bio-Manguinhos", lote.getValidade(), 0, 2L, HOJE, DEPOIS);

        assertEquals(0, lote.getQuantidade());
        assertEquals(2L, lote.getCamaraId());
        var anterior = copia(lote);
        assertThrows(EstadoIncompativel.class, () -> lote.atualizar("Outra vacina", "Bio-Manguinhos",
                lote.getValidade(), 1, 2L, HOJE, DEPOIS.plusHours(1)));
        assertMesmoLote(anterior, lote);
    }

    @Test
    void deveImpedirAumentoEmLoteDescartadoSemAlterarDados() {
        var lote = lote(10);
        lote.atribuirId(7);
        lote.descartar(DEPOIS);
        var anterior = copia(lote);

        assertThrows(EstadoIncompativel.class, () -> lote.atualizar("Outra vacina", "Outro fabricante",
                HOJE.plusDays(10), 11, 2L, HOJE, DEPOIS.plusHours(1)));

        assertMesmoLote(anterior, lote);
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
        lote.atribuirId(7);
        lote.inativar(DEPOIS);
        lote.inativar(DEPOIS.plusHours(1));

        assertFalse(lote.isAtivo());
        assertEquals(DEPOIS, lote.getAtualizadoEm());
        var anterior = copia(lote);
        assertThrows(EstadoIncompativel.class, () -> lote.atualizar("Vacina", "Fabricante", HOJE.plusDays(1), 10, 1L,
                HOJE, DEPOIS));
        assertThrows(EstadoIncompativel.class, () -> lote.darBaixa(1, MotivoBaixa.PERDA, DEPOIS));
        assertThrows(EstadoIncompativel.class, () -> lote.descartar(DEPOIS));
        assertMesmoLote(anterior, lote);
    }

    private static Lote copia(Lote lote) {
        return Lote.reconstituir(lote.getId(), lote.getCodigo(), lote.getImunobiologico(), lote.getFabricante(),
                lote.getValidade(), lote.getQuantidade(), lote.getCamaraId(), lote.getEstado(), lote.isAtivo(),
                lote.getCriadoEm(), lote.getAtualizadoEm());
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
}

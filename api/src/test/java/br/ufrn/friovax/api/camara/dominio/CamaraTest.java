package br.ufrn.friovax.api.camara.dominio;

import br.ufrn.friovax.api.compartilhado.dominio.CapacidadeExcedida;
import br.ufrn.friovax.api.compartilhado.dominio.EstadoIncompativel;
import br.ufrn.friovax.api.compartilhado.dominio.ValidacaoDeNegocio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CamaraTest {

    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 9, 25, 9, 0, 0, 0, ZoneOffset.ofHours(-3));
    private static final OffsetDateTime DEPOIS = AGORA.plusHours(1);

    private static Camara camara(int capacidade) {
        return Camara.nova(" cam-01 ", "  Câmara principal ", "UBS Centro", capacidade,
                new BigDecimal("2.0"), new BigDecimal("8"), EstadoCamara.OPERACIONAL, AGORA);
    }

    private static void atualizar(Camara camara, int capacidade, EstadoCamara estado,
                                  long ocupacao, boolean possuiLotesAtivos) {
        camara.atualizar("Câmara principal", "UBS Centro", capacidade, new BigDecimal("2.0"),
                new BigDecimal("8.0"), estado, ocupacao, possuiLotesAtivos, DEPOIS);
    }

    @Test
    void deveCriarCamaraAtivaComCamposNormalizados() {
        var camara = camara(5000);

        assertNull(camara.getId());
        assertEquals("CAM-01", camara.getCodigo());
        assertEquals("Câmara principal", camara.getNome());
        assertEquals(new BigDecimal("8.0"), camara.getTemperaturaMaxima());
        assertTrue(camara.isAtivo());
        assertEquals(AGORA, camara.getCriadoEm());
        assertEquals(AGORA, camara.getAtualizadoEm());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "AB", "CAM_01", "CAMARA-COM-MAIS-DE-20"})
    void deveRejeitarCodigoInvalido(String codigo) {
        var erro = assertThrows(ValidacaoDeNegocio.class, () -> Camara.nova(codigo, "Nome", "UBS", 10,
                BigDecimal.ONE, BigDecimal.TEN, EstadoCamara.OPERACIONAL, AGORA));

        assertEquals("codigo", erro.campo());
    }

    @Test
    void deveRejeitarNomeVazioOuLongo() {
        assertEquals("nome", assertThrows(ValidacaoDeNegocio.class, () -> Camara.nova("CAM-01", " ", "UBS", 10,
                BigDecimal.ONE, BigDecimal.TEN, EstadoCamara.OPERACIONAL, AGORA)).campo());
        assertEquals("unidade", assertThrows(ValidacaoDeNegocio.class, () -> Camara.nova("CAM-01", "Nome",
                "x".repeat(101), 10, BigDecimal.ONE, BigDecimal.TEN, EstadoCamara.OPERACIONAL, AGORA)).campo());
    }

    @Test
    void deveAceitarNomeEUnidadeComCemCaracteresDepoisDoTrim() {
        var texto = "x".repeat(100);
        var camara = Camara.nova("CAM-01", " " + texto + " ", " " + texto + " ", 10,
                BigDecimal.ONE, BigDecimal.TEN, EstadoCamara.OPERACIONAL, AGORA);

        assertEquals(texto, camara.getNome());
        assertEquals(texto, camara.getUnidade());
    }

    @ParameterizedTest
    @ValueSource(strings = {"nome", "unidade"})
    void deveRejeitarTextoQueContinuaLongoDepoisDoTrim(String campo) {
        var texto = " " + "x".repeat(101) + " ";
        var erro = assertThrows(ValidacaoDeNegocio.class, () -> Camara.nova("CAM-01",
                campo.equals("nome") ? texto : "Nome", campo.equals("unidade") ? texto : "UBS", 10,
                BigDecimal.ONE, BigDecimal.TEN, EstadoCamara.OPERACIONAL, AGORA));

        assertEquals(campo, erro.campo());
        assertEquals("deve ter no máximo 100 caracteres", erro.getMessage());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void deveRejeitarCapacidadeNaoPositiva(int capacidade) {
        var erro = assertThrows(ValidacaoDeNegocio.class, () -> camara(capacidade));

        assertEquals("capacidade", erro.campo());
    }

    @Test
    void deveExigirTemperaturaMinimaMenorQueMaxima() {
        var erro = assertThrows(ValidacaoDeNegocio.class, () -> Camara.nova("CAM-01", "Nome", "UBS", 10,
                new BigDecimal("8.0"), new BigDecimal("8.0"), EstadoCamara.OPERACIONAL, AGORA));

        assertEquals("temperaturaMinima", erro.campo());
    }

    @ParameterizedTest
    @CsvSource({"2.05, 8.0, temperaturaMinima", "2.0, 8.05, temperaturaMaxima"})
    void deveRejeitarTemperaturaComMaisDeUmaCasaDecimal(String minima, String maxima, String campo) {
        var erro = assertThrows(ValidacaoDeNegocio.class, () -> Camara.nova("CAM-01", "Nome", "UBS", 10,
                new BigDecimal(minima), new BigDecimal(maxima), EstadoCamara.OPERACIONAL, AGORA));

        assertEquals(campo, erro.campo());
        assertEquals("deve ter no máximo 1 casa decimal", erro.getMessage());
    }

    @ParameterizedTest
    @CsvSource({
            "-1000.0, 8.0, temperaturaMinima",
            "1000.0, 1001.0, temperaturaMinima",
            "-999.9, -1000.0, temperaturaMaxima",
            "2.0, 1000.0, temperaturaMaxima"
    })
    void deveRejeitarTemperaturaForaDoIntervalo(String minima, String maxima, String campo) {
        var erro = assertThrows(ValidacaoDeNegocio.class, () -> Camara.nova("CAM-01", "Nome", "UBS", 10,
                new BigDecimal(minima), new BigDecimal(maxima), EstadoCamara.OPERACIONAL, AGORA));

        assertEquals(campo, erro.campo());
        assertEquals("deve estar entre -999.9 e 999.9", erro.getMessage());
    }

    @Test
    void deveAceitarLimitesInclusivosDeTemperatura() {
        var camara = Camara.nova("CAM-01", "Nome", "UBS", 10,
                new BigDecimal("-999.9"), new BigDecimal("999.9"), EstadoCamara.OPERACIONAL, AGORA);

        assertEquals(new BigDecimal("-999.9"), camara.getTemperaturaMinima());
        assertEquals(new BigDecimal("999.9"), camara.getTemperaturaMaxima());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2", "2.0", "2.00"})
    void deveNormalizarTemperaturaSemContarZerosADireita(String minima) {
        var camara = Camara.nova("CAM-01", "Nome", "UBS", 10,
                new BigDecimal(minima), BigDecimal.TEN, EstadoCamara.OPERACIONAL, AGORA);

        assertEquals(new BigDecimal("2.0"), camara.getTemperaturaMinima());
        assertEquals(new BigDecimal("10.0"), camara.getTemperaturaMaxima());
    }

    @Test
    void devePermitirCapacidadeIgualAOcupacao() {
        var camara = camara(5000);

        atualizar(camara, 4500, EstadoCamara.OPERACIONAL, 4500, true);

        assertEquals(4500, camara.getCapacidade());
        assertEquals(DEPOIS, camara.getAtualizadoEm());
    }

    @Test
    void deveRejeitarCapacidadeAbaixoDaOcupacaoSemAlterarACamara() {
        var camara = camara(5000);

        assertThrows(CapacidadeExcedida.class, () -> atualizar(camara, 4499, EstadoCamara.OPERACIONAL, 4500, true));

        assertEquals(5000, camara.getCapacidade());
        assertEquals(AGORA, camara.getAtualizadoEm());
    }

    @Test
    void deveManterDadosQuandoAtualizacaoTemCampoInvalido() {
        var camara = camara(5000);

        assertThrows(ValidacaoDeNegocio.class, () -> camara.atualizar("Outro nome", "", 100, BigDecimal.ONE,
                BigDecimal.TEN, EstadoCamara.OPERACIONAL, 0, false, DEPOIS));

        assertEquals("Câmara principal", camara.getNome());
    }

    @ParameterizedTest
    @ValueSource(strings = {"MANUTENCAO", "DESATIVADA"})
    void deveImpedirSairDeOperacionalComLotesAtivos(EstadoCamara estado) {
        var camara = camara(5000);

        assertThrows(EstadoIncompativel.class, () -> atualizar(camara, 5000, estado, 100, true));
        assertEquals(EstadoCamara.OPERACIONAL, camara.getEstado());
    }

    @Test
    void devePermitirMudarEstadoSemLotesAtivos() {
        var camara = camara(5000);

        atualizar(camara, 5000, EstadoCamara.MANUTENCAO, 0, false);
        assertEquals(EstadoCamara.MANUTENCAO, camara.getEstado());

        atualizar(camara, 5000, EstadoCamara.OPERACIONAL, 0, false);
        assertEquals(EstadoCamara.OPERACIONAL, camara.getEstado());
    }

    @Test
    void deveInativarCamaraSemLotesDeFormaIdempotente() {
        var camara = camara(5000);

        camara.inativar(false, DEPOIS);
        camara.inativar(true, DEPOIS.plusHours(1));

        assertFalse(camara.isAtivo());
        assertEquals(DEPOIS, camara.getAtualizadoEm());
    }

    @Test
    void deveImpedirInativarCamaraComLotesAtivos() {
        var camara = camara(5000);

        assertThrows(EstadoIncompativel.class, () -> camara.inativar(true, DEPOIS));
        assertTrue(camara.isAtivo());
    }

    @Test
    void deveImpedirAlterarCamaraInativa() {
        var camara = camara(5000);
        camara.inativar(false, DEPOIS);

        assertThrows(EstadoIncompativel.class, () -> atualizar(camara, 6000, EstadoCamara.OPERACIONAL, 0, false));
    }

    @Test
    void deveAceitarAlocacaoAteACapacidadeExata() {
        var camara = camara(5000);

        assertDoesNotThrow(() -> camara.verificarAlocacao(3800, 1200));
        assertThrows(CapacidadeExcedida.class, () -> camara.verificarAlocacao(3801, 1200));
    }

    @Test
    void deveRecusarAlocacaoEmCamaraNaoOperacionalOuInativa() {
        var emManutencao = camara(5000);
        atualizar(emManutencao, 5000, EstadoCamara.MANUTENCAO, 0, false);
        var inativa = camara(5000);
        inativa.inativar(false, DEPOIS);

        assertThrows(EstadoIncompativel.class, () -> emManutencao.verificarAlocacao(0, 1));
        assertThrows(EstadoIncompativel.class, () -> inativa.verificarAlocacao(0, 1));
    }

    @Test
    void deveAtribuirIdApenasUmaVez() {
        var camara = camara(5000);

        camara.atribuirId(7);

        assertEquals(7L, camara.getId());
        assertThrows(IllegalStateException.class, () -> camara.atribuirId(8));
    }
}

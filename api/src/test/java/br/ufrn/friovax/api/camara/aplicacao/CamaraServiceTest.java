package br.ufrn.friovax.api.camara.aplicacao;

import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.CamaraFiltro;
import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import br.ufrn.friovax.api.compartilhado.dominio.CodigoDuplicado;
import br.ufrn.friovax.api.compartilhado.dominio.Paginacao;
import br.ufrn.friovax.api.compartilhado.dominio.ValidacaoDeNegocio;
import br.ufrn.friovax.api.suporte.CamaraRepositoryEmMemoria;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CamaraServiceTest {

    private static final Clock RELOGIO = Clock.fixed(
            Instant.parse("2026-09-29T12:00:00Z"), ZoneId.of("America/Fortaleza"));
    private static final OffsetDateTime AGORA = OffsetDateTime.parse("2026-09-29T09:00:00-03:00");

    private final CamaraRepositoryEmMemoria repositorio = new CamaraRepositoryEmMemoria();
    private final CamaraService service = new CamaraService(repositorio, RELOGIO);

    private static CadastrarCamaraDTO dadosCadastro(String codigo, EstadoCamara estado) {
        return new CadastrarCamaraDTO(codigo, " Câmara principal ", " UBS Centro ", 5000,
                new BigDecimal("2.00"), new BigDecimal("8"), estado);
    }

    @Test
    void deveCadastrarCamaraNormalizadaComIdEDatasDoRelogio() {
        var resultado = service.cadastrar(dadosCadastro(" cam-01 ", EstadoCamara.OPERACIONAL));

        assertEquals(new CamaraResultado(1L, "CAM-01", "Câmara principal", "UBS Centro", 5000,
                new BigDecimal("2.0"), new BigDecimal("8.0"), EstadoCamara.OPERACIONAL,
                0, true, AGORA, AGORA), resultado);

        var salva = repositorio.buscarPorId(resultado.id()).orElseThrow();
        assertEquals("CAM-01", salva.getCodigo());
        assertEquals("Câmara principal", salva.getNome());
        assertEquals("UBS Centro", salva.getUnidade());
        assertEquals(5000, salva.getCapacidade());
        assertEquals(new BigDecimal("2.0"), salva.getTemperaturaMinima());
        assertEquals(new BigDecimal("8.0"), salva.getTemperaturaMaxima());
        assertEquals(EstadoCamara.OPERACIONAL, salva.getEstado());
        assertTrue(salva.isAtivo());
        assertEquals(AGORA, salva.getCriadoEm());
        assertEquals(AGORA, salva.getAtualizadoEm());
    }

    @ParameterizedTest
    @ValueSource(strings = {"OPERACIONAL", "MANUTENCAO", "DESATIVADA"})
    void deveCadastrarAtivaESemOcupacaoEmTodosOsEstados(EstadoCamara estado) {
        var resultado = service.cadastrar(dadosCadastro("CAM-01", estado));

        assertEquals(estado, resultado.estado());
        assertTrue(resultado.ativo());
        assertEquals(0, resultado.ocupacao());
        assertEquals(estado, repositorio.buscarPorId(resultado.id()).orElseThrow().getEstado());
    }

    @Test
    void deveRetornarOsIdsAtribuidosPeloRepositorioParaCadaCadastro() {
        var primeira = service.cadastrar(dadosCadastro("CAM-01", EstadoCamara.OPERACIONAL));
        var segunda = service.cadastrar(dadosCadastro("CAM-02", EstadoCamara.OPERACIONAL));

        assertNotEquals(primeira.id(), segunda.id());
        assertEquals("CAM-01", repositorio.buscarPorId(primeira.id()).orElseThrow().getCodigo());
        assertEquals("CAM-02", repositorio.buscarPorId(segunda.id()).orElseThrow().getCodigo());
        assertEquals(2, repositorio.listar(CamaraFiltro.ativas(), Paginacao.padrao()).totalElementos());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CAM-01", "cam-01", " cam-01 "})
    void deveRejeitarCodigoDuplicadoAposNormalizacaoSemCriarOutroRegistro(String codigo) {
        var primeira = service.cadastrar(dadosCadastro("CAM-01", EstadoCamara.OPERACIONAL));

        var erro = assertThrows(CodigoDuplicado.class,
                () -> service.cadastrar(dadosCadastro(codigo, EstadoCamara.MANUTENCAO)));

        assertEquals("Já existe câmara com o código CAM-01.", erro.getMessage());
        assertEquals(1, repositorio.listar(CamaraFiltro.ativas(), Paginacao.padrao()).totalElementos());
        assertEquals(EstadoCamara.OPERACIONAL,
                repositorio.buscarPorId(primeira.id()).orElseThrow().getEstado());
    }

    @Test
    void deveRejeitarCodigoReservadoPorCamaraInativa() {
        var resultado = service.cadastrar(dadosCadastro("CAM-01", EstadoCamara.OPERACIONAL));
        var camara = repositorio.buscarPorId(resultado.id()).orElseThrow();
        camara.inativar(false, AGORA.plusHours(1));
        repositorio.salvar(camara);

        assertThrows(CodigoDuplicado.class,
                () -> service.cadastrar(dadosCadastro(" cam-01 ", EstadoCamara.OPERACIONAL)));

        assertEquals(0, repositorio.listar(CamaraFiltro.ativas(), Paginacao.padrao()).totalElementos());
        assertEquals(1, repositorio.listar(new CamaraFiltro(null, null, false),
                Paginacao.padrao()).totalElementos());
        assertFalse(repositorio.buscarPorId(resultado.id()).orElseThrow().isAtivo());
    }

    @ParameterizedTest
    @CsvSource({
            "0, 2.0, 8.0, capacidade",
            "10, 8.0, 2.0, temperaturaMinima",
            "10, 2.0, 1000.0, temperaturaMaxima"
    })
    void devePropagarValidacaoDoDominioSemGravar(int capacidade, String minima, String maxima, String campo) {
        var invalido = new CadastrarCamaraDTO("CAM-01", "Nome", "UBS", capacidade,
                new BigDecimal(minima), new BigDecimal(maxima), EstadoCamara.OPERACIONAL);

        var erro = assertThrows(ValidacaoDeNegocio.class, () -> service.cadastrar(invalido));

        assertEquals(campo, erro.campo());
        assertFalse(repositorio.existePorCodigo("CAM-01"));
        assertEquals(0, repositorio.listar(CamaraFiltro.ativas(), Paginacao.padrao()).totalElementos());
    }

    @Test
    void devePropagarCodigoDuplicadoIdentificadoAoSalvar() {
        var conflito = new CodigoDuplicado("câmara", "CAM-01");
        var repositorioComConflito = new CamaraRepositoryEmMemoria() {
            @Override
            public Camara salvar(Camara camara) {
                throw conflito;
            }
        };
        var serviceComConflito = new CamaraService(repositorioComConflito, RELOGIO);

        assertFalse(repositorioComConflito.existePorCodigo("CAM-01"));
        var erro = assertThrows(CodigoDuplicado.class,
                () -> serviceComConflito.cadastrar(dadosCadastro("CAM-01", EstadoCamara.OPERACIONAL)));

        assertSame(conflito, erro);
        assertEquals(0, repositorioComConflito.listar(CamaraFiltro.ativas(),
                Paginacao.padrao()).totalElementos());
    }
}

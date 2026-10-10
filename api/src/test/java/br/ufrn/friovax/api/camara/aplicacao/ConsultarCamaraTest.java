package br.ufrn.friovax.api.camara.aplicacao;

import br.ufrn.friovax.api.camara.api.CamaraResource;
import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.CamaraFiltro;
import br.ufrn.friovax.api.compartilhado.dominio.Paginacao;
import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import br.ufrn.friovax.api.compartilhado.dominio.RecursoNaoEncontrado;
import br.ufrn.friovax.api.lote.dominio.Lote;
import br.ufrn.friovax.api.suporte.CamaraRepositoryEmMemoria;
import br.ufrn.friovax.api.suporte.LoteRepositoryEmMemoria;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsultarCamaraTest {
    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 9, 29, 12, 0, 0, 0, ZoneOffset.ofHours(-3));

    private final CamaraRepositoryEmMemoria repositorio = new CamaraRepositoryEmMemoria();
    private final LoteRepositoryEmMemoria lotes = new LoteRepositoryEmMemoria();
    private final ConsultarCamara casoDeUso = new ConsultarCamara(repositorio, lotes);
    private final CamaraResource resource = new CamaraResource(new CamaraService(repositorio, Clock.systemUTC()), casoDeUso,
        new AtualizarCamara(repositorio, lotes, Clock.systemUTC()),
        new InativarCamara(repositorio, lotes, Clock.systemUTC()));

    @Test
    void deveConsultarCamaraComTodosOsCamposDoContrato() {
        var camara = repositorio.salvar(Camara.nova("cam-01", "Câmara principal", "UBS Centro", 5000,
                new BigDecimal("2.0"), new BigDecimal("8.0"), EstadoCamara.OPERACIONAL, AGORA));
        lotes.salvar(Lote.novo("L-01", "Vacina A", "Fabricante", LocalDate.of(2027, 1, 1),
                1200, camara.getId(), AGORA.toLocalDate(), AGORA));
        var inativo = lotes.salvar(Lote.novo("L-02", "Vacina B", "Fabricante", LocalDate.of(2027, 1, 1),
                300, camara.getId(), AGORA.toLocalDate(), AGORA));
        inativo.inativar(AGORA.plusHours(1));
        lotes.salvar(inativo);

        var resposta = resource.consultar(camara.getId());

        assertEquals(camara.getId(), resposta.id());
        assertEquals("CAM-01", resposta.codigo());
        assertEquals("Câmara principal", resposta.nome());
        assertEquals("UBS Centro", resposta.unidade());
        assertEquals(5000, resposta.capacidade());
        assertEquals(new BigDecimal("2.0"), resposta.temperaturaMinima());
        assertEquals(new BigDecimal("8.0"), resposta.temperaturaMaxima());
        assertEquals(EstadoCamara.OPERACIONAL, resposta.estado());
        assertEquals(1200, resposta.ocupacao());
        assertEquals(true, resposta.ativo());
        assertEquals(AGORA, resposta.criadoEm());
        assertEquals(AGORA, resposta.atualizadoEm());
    }

    @Test
    void deveConsultarCamaraInativa() {
        var camara = repositorio.salvar(Camara.nova("CAM-02", "Reserva", "UBS Norte", 100,
                new BigDecimal("2.0"), new BigDecimal("8.0"), EstadoCamara.OPERACIONAL, AGORA));
        camara.inativar(false, AGORA.plusHours(1));
        repositorio.salvar(camara);

        var resposta = resource.consultar(camara.getId());

        assertFalse(resposta.ativo());
        assertEquals(AGORA.plusHours(1), resposta.atualizadoEm());
    }

    @Test
    void deveListarComFiltrosMetadadosEOcupacao() {
        var camara = repositorio.salvar(Camara.nova("CAM-LISTA", "Principal", "UBS Centro", 5000,
                new BigDecimal("2.0"), new BigDecimal("8.0"), EstadoCamara.OPERACIONAL, AGORA));
        repositorio.salvar(Camara.nova("CAM-OUTRA", "Outra", "UBS Norte", 5000,
                new BigDecimal("2.0"), new BigDecimal("8.0"), EstadoCamara.OPERACIONAL, AGORA));
        lotes.salvar(Lote.novo("L-LISTA", "Vacina", "Fabricante", LocalDate.of(2027, 1, 1),
                25, camara.getId(), AGORA.toLocalDate(), AGORA));
        var pagina = resource.listar("  ubs centro  ", "OPERACIONAL", null, null, "1");
        assertEquals(1, pagina.totalElements());
        assertEquals(1, pagina.totalPages());
        assertEquals(0, pagina.page());
        assertEquals(1, pagina.size());
        assertEquals(camara.getId(), pagina.items().getFirst().id());
        assertEquals(25, pagina.items().getFirst().ocupacao());
        var vazia = resource.listar("UBS Centro", "OPERACIONAL", null, "1", "1");
        assertEquals(0, vazia.items().size());
        assertEquals(1, vazia.totalElements());
        assertEquals(1, vazia.totalPages());
        var padrao = resource.listar(null, null, null, null, null);
        assertEquals(20, padrao.size());
        assertEquals(2, padrao.totalElements());
    }

    @Test
    void deveRejeitarParametrosInvalidosDaListagem() {
        assertThrows(jakarta.ws.rs.BadRequestException.class,
                () -> resource.listar(null, "INVALIDO", null, null, null));
        assertThrows(jakarta.ws.rs.BadRequestException.class,
                () -> resource.listar(null, null, "sim", null, null));
        assertThrows(jakarta.ws.rs.BadRequestException.class,
                () -> resource.listar(null, null, null, "-1", null));
        assertThrows(jakarta.ws.rs.BadRequestException.class,
                () -> resource.listar(null, null, null, null, "101"));
        assertThrows(jakarta.ws.rs.BadRequestException.class,
                () -> resource.listar(null, null, null, "abc", null));
    }

    @Test
    void deveIndicarCamaraInexistente() {
        assertThrows(RecursoNaoEncontrado.class, () -> casoDeUso.consultar(42));
    }

    @Test
    void deveCombinarFiltrosNoCasoDeUsoAntesDePaginarECalcularOcupacao() {
        var primeira = salvarCamara("CAM-Z", "UBS Centro", EstadoCamara.OPERACIONAL);
        var segunda = salvarCamara("CAM-A", "UBS Centro", EstadoCamara.OPERACIONAL);
        var terceira = salvarCamara("CAM-M", "UBS Centro", EstadoCamara.OPERACIONAL);
        salvarCamara("CAM-UNIDADE", "UBS Norte", EstadoCamara.OPERACIONAL);
        salvarCamara("CAM-ESTADO", "UBS Centro", EstadoCamara.MANUTENCAO);
        var inativa = salvarCamara("CAM-INATIVA", "UBS Centro", EstadoCamara.OPERACIONAL);
        inativa.inativar(false, AGORA.plusHours(1));
        repositorio.salvar(inativa);
        salvarLote("L1", primeira, 80);
        salvarLote("L2", segunda, 20);
        var loteInativo = salvarLote("L3", segunda, 100);
        loteInativo.inativar(AGORA.plusHours(1));
        lotes.salvar(loteInativo);
        var filtro = new CamaraFiltro("  ubs centro  ", EstadoCamara.OPERACIONAL, true);

        var pagina = casoDeUso.listar(filtro, new Paginacao(1, 1));

        assertEquals(List.of(segunda.getId()), pagina.itens().stream().map(r -> r.camara().getId()).toList());
        assertEquals(20, pagina.itens().getFirst().ocupacao());
        assertEquals(1, pagina.pagina());
        assertEquals(1, pagina.tamanho());
        assertEquals(3, pagina.totalElementos());
        assertEquals(3, pagina.totalPaginas());
        var todas = casoDeUso.listar(filtro, Paginacao.padrao());
        assertEquals(List.of(primeira.getId(), segunda.getId(), terceira.getId()),
                todas.itens().stream().map(r -> r.camara().getId()).toList());
        assertEquals(List.of(80L, 20L, 0L), todas.itens().stream().map(ConsultarCamara.Resultado::ocupacao).toList());
        var distante = casoDeUso.listar(filtro, new Paginacao(3, 1));
        assertTrue(distante.itens().isEmpty());
        assertEquals(3, distante.pagina());
        assertEquals(1, distante.tamanho());
        assertEquals(3, distante.totalElementos());
        assertEquals(3, distante.totalPaginas());
    }

    @Test
    void deveConsultarDiretamenteInativosEListarPorSituacao() {
        var ativa = salvarCamara("CAM-ATIVA", "UBS", EstadoCamara.OPERACIONAL);
        var inativa = salvarCamara("CAM-INATIVA", "UBS", EstadoCamara.OPERACIONAL);
        inativa.inativar(false, AGORA.plusHours(1));
        repositorio.salvar(inativa);

        var consultada = casoDeUso.consultar(inativa.getId());
        assertFalse(consultada.camara().isAtivo());
        assertEquals(inativa.getId(), consultada.camara().getId());
        assertEquals(AGORA.plusHours(1), consultada.camara().getAtualizadoEm());
        assertEquals(0, consultada.ocupacao());
        var padrao = casoDeUso.listar(CamaraFiltro.ativas(), Paginacao.padrao());
        assertEquals(List.of(ativa.getId()), padrao.itens().stream().map(r -> r.camara().getId()).toList());
        assertEquals(0, padrao.pagina());
        assertEquals(20, padrao.tamanho());
        assertEquals(1, padrao.totalElementos());
        assertEquals(1, padrao.totalPaginas());
        var inativas = casoDeUso.listar(new CamaraFiltro(null, null, false), Paginacao.padrao());
        assertEquals(List.of(inativa.getId()), inativas.itens().stream().map(r -> r.camara().getId()).toList());
        var vazia = casoDeUso.listar(new CamaraFiltro("Inexistente", null, true), Paginacao.padrao());
        assertTrue(vazia.itens().isEmpty());
        assertEquals(0, vazia.pagina());
        assertEquals(20, vazia.tamanho());
        assertEquals(0, vazia.totalElementos());
        assertEquals(0, vazia.totalPaginas());
    }

    private Camara salvarCamara(String codigo, String unidade, EstadoCamara estado) {
        return repositorio.salvar(Camara.nova(codigo, "Câmara", unidade, 5000,
                new BigDecimal("2.0"), new BigDecimal("8.0"), estado, AGORA));
    }

    private Lote salvarLote(String codigo, Camara camara, int quantidade) {
        return lotes.salvar(Lote.novo(codigo, "Vacina", "Fabricante", LocalDate.of(2027, 1, 1),
                quantidade, camara.getId(), AGORA.toLocalDate(), AGORA));
    }
}

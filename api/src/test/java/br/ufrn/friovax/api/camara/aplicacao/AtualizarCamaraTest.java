package br.ufrn.friovax.api.camara.aplicacao;

import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import br.ufrn.friovax.api.compartilhado.dominio.CapacidadeExcedida;
import br.ufrn.friovax.api.compartilhado.dominio.EstadoIncompativel;
import br.ufrn.friovax.api.compartilhado.dominio.RecursoNaoEncontrado;
import br.ufrn.friovax.api.compartilhado.dominio.ValidacaoDeNegocio;
import br.ufrn.friovax.api.lote.dominio.Lote;
import br.ufrn.friovax.api.suporte.CamaraRepositoryEmMemoria;
import br.ufrn.friovax.api.suporte.LoteRepositoryEmMemoria;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AtualizarCamaraTest {
    private static final OffsetDateTime CRIACAO = OffsetDateTime.of(2026, 9, 29, 12, 0, 0, 0, ZoneOffset.ofHours(-3));
    private static final OffsetDateTime AGORA = CRIACAO.plusDays(1);

    private final CamaraRepositoryEmMemoria camaras = new CamaraRepositoryEmMemoria();
    private final LoteRepositoryEmMemoria lotes = new LoteRepositoryEmMemoria();
    private final AtualizarCamara casoDeUso = new AtualizarCamara(camaras, lotes,
            Clock.fixed(AGORA.toInstant(), AGORA.getOffset()));

    @Test
    void deveAtualizarCamposEditaveisPreservandoIdCodigoECriacao() {
        var camara = novaCamara("CAM-01");

        var resultado = casoDeUso.atualizar(camara.getId(), new AtualizarCamaraDTO("Câmara reformada", "UBS Norte",
                2000, new BigDecimal("3.0"), new BigDecimal("7.0"), EstadoCamara.MANUTENCAO));

        var gravada = camaras.buscarPorId(camara.getId()).orElseThrow();
        assertEquals(camara.getId(), gravada.getId());
        assertEquals("CAM-01", gravada.getCodigo());
        assertEquals("Câmara reformada", gravada.getNome());
        assertEquals("UBS Norte", gravada.getUnidade());
        assertEquals(2000, gravada.getCapacidade());
        assertEquals(EstadoCamara.MANUTENCAO, gravada.getEstado());
        assertEquals(CRIACAO, gravada.getCriadoEm());
        assertEquals(AGORA.toInstant(), gravada.getAtualizadoEm().toInstant());
        assertEquals(0, resultado.ocupacao());
    }

    @Test
    void deveAceitarCapacidadeIgualAOcupacao() {
        var camara = novaCamara("CAM-02");
        lotes.salvar(novoLote("L-01", camara, 300));

        var resultado = casoDeUso.atualizar(camara.getId(), dados(300, EstadoCamara.OPERACIONAL));

        assertEquals(300, camaras.buscarPorId(camara.getId()).orElseThrow().getCapacidade());
        assertEquals(300, resultado.ocupacao());
    }

    @Test
    void deveRecusarCapacidadeAbaixoDaOcupacaoSemAlterarNada() {
        var camara = novaCamara("CAM-03");
        lotes.salvar(novoLote("L-02", camara, 300));

        assertThrows(CapacidadeExcedida.class,
                () -> casoDeUso.atualizar(camara.getId(), dados(299, EstadoCamara.OPERACIONAL)));

        assertSemAlteracao(camara.getId());
    }

    @Test
    void deveRecusarManutencaoComLotesAtivosSemAlterarNada() {
        var camara = novaCamara("CAM-04");
        lotes.salvar(novoLote("L-03", camara, 100));

        assertThrows(EstadoIncompativel.class,
                () -> casoDeUso.atualizar(camara.getId(), dados(1000, EstadoCamara.MANUTENCAO)));

        assertSemAlteracao(camara.getId());
    }

    @Test
    void deveRecusarCamaraInativa() {
        var camara = novaCamara("CAM-05");
        camara.inativar(false, CRIACAO.plusHours(1));
        camaras.salvar(camara);

        assertThrows(EstadoIncompativel.class,
                () -> casoDeUso.atualizar(camara.getId(), dados(1000, EstadoCamara.OPERACIONAL)));
    }

    @Test
    void deveRecusarTemperaturasInvertidasSemAlterarNada() {
        var camara = novaCamara("CAM-06");

        assertThrows(ValidacaoDeNegocio.class, () -> casoDeUso.atualizar(camara.getId(),
                new AtualizarCamaraDTO("Outro nome", "UBS Centro", 1000,
                        new BigDecimal("8.0"), new BigDecimal("2.0"), EstadoCamara.OPERACIONAL)));

        assertSemAlteracao(camara.getId());
    }

    @Test
    void deveIndicarCamaraInexistente() {
        assertThrows(RecursoNaoEncontrado.class, () -> casoDeUso.atualizar(42, dados(1000, EstadoCamara.OPERACIONAL)));
    }

    private void assertSemAlteracao(long id) {
        var gravada = camaras.buscarPorId(id).orElseThrow();
        assertEquals("Câmara", gravada.getNome());
        assertEquals(1000, gravada.getCapacidade());
        assertEquals(EstadoCamara.OPERACIONAL, gravada.getEstado());
        assertEquals(CRIACAO, gravada.getAtualizadoEm());
    }

    private static AtualizarCamaraDTO dados(int capacidade, EstadoCamara estado) {
        return new AtualizarCamaraDTO("Câmara atualizada", "UBS Centro", capacidade,
                new BigDecimal("2.0"), new BigDecimal("8.0"), estado);
    }

    private Camara novaCamara(String codigo) {
        return camaras.salvar(Camara.nova(codigo, "Câmara", "UBS Centro", 1000,
                new BigDecimal("2.0"), new BigDecimal("8.0"), EstadoCamara.OPERACIONAL, CRIACAO));
    }

    private static Lote novoLote(String codigo, Camara camara, int quantidade) {
        return Lote.novo(codigo, "Vacina A", "Fabricante", LocalDate.of(2027, 1, 1),
                quantidade, camara.getId(), CRIACAO.toLocalDate(), CRIACAO);
    }
}

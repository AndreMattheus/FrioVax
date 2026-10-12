package br.ufrn.friovax.api.lote.aplicacao;

import br.ufrn.friovax.api.compartilhado.dominio.EstadoIncompativel;
import br.ufrn.friovax.api.compartilhado.dominio.RecursoNaoEncontrado;
import br.ufrn.friovax.api.compartilhado.dominio.ValidacaoDeNegocio;
import br.ufrn.friovax.api.lote.dominio.EstadoLote;
import br.ufrn.friovax.api.lote.dominio.Lote;
import br.ufrn.friovax.api.lote.dominio.MotivoBaixa;
import br.ufrn.friovax.api.suporte.LoteRepositoryEmMemoria;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RegistrarBaixaLoteTest {
    private static final OffsetDateTime CRIACAO = OffsetDateTime.parse("2026-10-01T10:00:00-03:00");
    private static final OffsetDateTime AGORA = CRIACAO.plusDays(1);
    private final LoteRepositoryEmMemoria lotes = new LoteRepositoryEmMemoria();
    private final RegistrarBaixaLote casoDeUso = new RegistrarBaixaLote(lotes,
            Clock.fixed(AGORA.toInstant(), ZoneId.of("America/Fortaleza")));

    @Test
    void baixaParcialReduzQuantidadeEOcupacao() {
        var lote = lotes.salvar(novo("L1", 100, 1));
        lotes.salvar(novo("L2", 50, 1));

        var resultado = casoDeUso.registrar(lote.getId(), 30, MotivoBaixa.ADMINISTRADA);

        var salvo = lotes.buscarPorId(lote.getId()).orElseThrow();
        assertEquals(70, salvo.getQuantidade());
        assertEquals(EstadoLote.DISPONIVEL, salvo.getEstado());
        assertEquals(AGORA.toInstant(), salvo.getAtualizadoEm().toInstant());
        assertEquals(70, resultado.getQuantidade());
        assertEquals(120, lotes.ocupacaoDaCamara(1));
    }

    @Test
    void baixaDoRestanteEsgotaOLote() {
        var lote = lotes.salvar(novo("L1", 40, 1));

        casoDeUso.registrar(lote.getId(), 40, MotivoBaixa.PERDA);

        var salvo = lotes.buscarPorId(lote.getId()).orElseThrow();
        assertEquals(0, salvo.getQuantidade());
        assertEquals(EstadoLote.ESGOTADO, salvo.getEstado());
        assertEquals(0, lotes.ocupacaoDaCamara(1));
    }

    @Test
    void recusaQuantidadeForaDoIntervaloSemAlterarNada() {
        var lote = lotes.salvar(novo("L1", 40, 1));

        assertThrows(ValidacaoDeNegocio.class, () -> casoDeUso.registrar(lote.getId(), 41, MotivoBaixa.PERDA));
        assertThrows(ValidacaoDeNegocio.class, () -> casoDeUso.registrar(lote.getId(), 0, MotivoBaixa.PERDA));

        assertSemAlteracao(lote.getId(), 40, EstadoLote.DISPONIVEL);
    }

    @Test
    void recusaBaixaSemMotivo() {
        var lote = lotes.salvar(novo("L1", 40, 1));

        assertThrows(ValidacaoDeNegocio.class, () -> casoDeUso.registrar(lote.getId(), 10, null));

        assertSemAlteracao(lote.getId(), 40, EstadoLote.DISPONIVEL);
    }

    @Test
    void recusaBaixaEmLoteDescartado() {
        var lote = novo("L1", 40, 1);
        lote.descartar(CRIACAO);
        lotes.salvar(lote);

        assertThrows(EstadoIncompativel.class, () -> casoDeUso.registrar(lote.getId(), 10, MotivoBaixa.PERDA));

        assertSemAlteracao(lote.getId(), 40, EstadoLote.DESCARTADO);
    }

    @Test
    void recusaBaixaEmLoteInativo() {
        var lote = novo("L1", 40, 1);
        lote.inativar(CRIACAO);
        lotes.salvar(lote);

        assertThrows(EstadoIncompativel.class, () -> casoDeUso.registrar(lote.getId(), 10, MotivoBaixa.PERDA));

        assertSemAlteracao(lote.getId(), 40, EstadoLote.DISPONIVEL);
    }

    @Test
    void indicaLoteInexistente() {
        assertThrows(RecursoNaoEncontrado.class, () -> casoDeUso.registrar(999, 10, MotivoBaixa.PERDA));
    }

    private void assertSemAlteracao(long id, int quantidade, EstadoLote estado) {
        var salvo = lotes.buscarPorId(id).orElseThrow();
        assertEquals(quantidade, salvo.getQuantidade());
        assertEquals(estado, salvo.getEstado());
        assertEquals(CRIACAO, salvo.getAtualizadoEm());
    }

    private static Lote novo(String codigo, int quantidade, long camaraId) {
        return Lote.novo(codigo, "Vacina", "Fabricante", LocalDate.of(2027, 1, 1),
                quantidade, camaraId, CRIACAO.toLocalDate(), CRIACAO);
    }
}

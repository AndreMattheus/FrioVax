package br.ufrn.friovax.api.lote.aplicacao;

import br.ufrn.friovax.api.compartilhado.dominio.EstadoIncompativel;
import br.ufrn.friovax.api.compartilhado.dominio.RecursoNaoEncontrado;
import br.ufrn.friovax.api.lote.dominio.EstadoLote;
import br.ufrn.friovax.api.lote.dominio.Lote;
import br.ufrn.friovax.api.suporte.LoteRepositoryEmMemoria;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DescartarLoteTest {
    private static final OffsetDateTime CRIACAO = OffsetDateTime.parse("2026-10-01T10:00:00-03:00");
    private static final OffsetDateTime AGORA = CRIACAO.plusDays(1);
    private final LoteRepositoryEmMemoria lotes = new LoteRepositoryEmMemoria();
    private final DescartarLote casoDeUso = new DescartarLote(lotes,
            Clock.fixed(AGORA.toInstant(), ZoneId.of("America/Fortaleza")));

    @Test
    void descartaMantendoQuantidadeEOcupacaoAteAInativacao() {
        var lote = lotes.salvar(novo("L1", 100, 1));

        var resultado = casoDeUso.descartar(lote.getId());

        var salvo = lotes.buscarPorId(lote.getId()).orElseThrow();
        assertEquals(EstadoLote.DESCARTADO, salvo.getEstado());
        assertEquals(EstadoLote.DESCARTADO, resultado.getEstado());
        assertEquals(100, salvo.getQuantidade());
        assertEquals(AGORA.toInstant(), salvo.getAtualizadoEm().toInstant());
        // O lote descartado continua ativo: ocupa a câmara até ser inativado (D6).
        assertEquals(100, lotes.ocupacaoDaCamara(1));
    }

    @Test
    void repetirODescarteNaoAlteraOLote() {
        var lote = lotes.salvar(novo("L1", 100, 1));
        casoDeUso.descartar(lote.getId());

        var depois = new DescartarLote(lotes, Clock.fixed(AGORA.plusDays(1).toInstant(),
                ZoneId.of("America/Fortaleza")));
        depois.descartar(lote.getId());

        var salvo = lotes.buscarPorId(lote.getId()).orElseThrow();
        assertEquals(EstadoLote.DESCARTADO, salvo.getEstado());
        assertEquals(AGORA.toInstant(), salvo.getAtualizadoEm().toInstant());
    }

    @Test
    void recusaLoteInativo() {
        var lote = novo("L1", 100, 1);
        lote.inativar(CRIACAO);
        lotes.salvar(lote);

        assertThrows(EstadoIncompativel.class, () -> casoDeUso.descartar(lote.getId()));

        var salvo = lotes.buscarPorId(lote.getId()).orElseThrow();
        assertEquals(EstadoLote.DISPONIVEL, salvo.getEstado());
        assertEquals(CRIACAO, salvo.getAtualizadoEm());
    }

    @Test
    void indicaLoteInexistente() {
        assertThrows(RecursoNaoEncontrado.class, () -> casoDeUso.descartar(999));
    }

    private static Lote novo(String codigo, int quantidade, long camaraId) {
        return Lote.novo(codigo, "Vacina", "Fabricante", LocalDate.of(2027, 1, 1),
                quantidade, camaraId, CRIACAO.toLocalDate(), CRIACAO);
    }
}

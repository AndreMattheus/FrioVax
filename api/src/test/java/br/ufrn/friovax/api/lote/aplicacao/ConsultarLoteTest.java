package br.ufrn.friovax.api.lote.aplicacao;

import br.ufrn.friovax.api.compartilhado.dominio.RecursoNaoEncontrado;
import br.ufrn.friovax.api.lote.dominio.EstadoLote;
import br.ufrn.friovax.api.lote.dominio.Lote;
import br.ufrn.friovax.api.lote.dominio.MotivoBaixa;
import br.ufrn.friovax.api.suporte.LoteRepositoryEmMemoria;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsultarLoteTest {
    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 9, 29, 12, 0, 0, 0, ZoneOffset.ofHours(-3));

    private final LoteRepositoryEmMemoria lotes = new LoteRepositoryEmMemoria();
    private final ConsultarLote casoDeUso = new ConsultarLote(lotes);

    @Test
    void deveConsultarLoteComCamaraESituacao() {
        var lote = lotes.salvar(novoLote("fx2027a"));

        var consultado = casoDeUso.consultar(lote.getId());

        assertEquals(lote.getId(), consultado.getId());
        assertEquals("FX2027A", consultado.getCodigo());
        assertEquals("Febre amarela", consultado.getImunobiologico());
        assertEquals("Bio-Manguinhos", consultado.getFabricante());
        assertEquals(LocalDate.of(2027, 3, 31), consultado.getValidade());
        assertEquals(1200, consultado.getQuantidade());
        assertEquals(7, consultado.getCamaraId());
        assertEquals(EstadoLote.DISPONIVEL, consultado.getEstado());
        assertTrue(consultado.isAtivo());
        assertEquals(AGORA, consultado.getCriadoEm());
        assertEquals(AGORA, consultado.getAtualizadoEm());
    }

    @Test
    void deveConsultarLoteInativo() {
        var lote = lotes.salvar(novoLote("L-INATIVO"));
        lote.inativar(AGORA.plusHours(1));
        lotes.salvar(lote);

        var consultado = casoDeUso.consultar(lote.getId());

        assertFalse(consultado.isAtivo());
        assertEquals(AGORA.plusHours(1), consultado.getAtualizadoEm());
    }

    @Test
    void deveConsultarLoteEsgotado() {
        var lote = lotes.salvar(novoLote("L-ESGOTADO"));
        lote.darBaixa(1200, MotivoBaixa.ADMINISTRADA, AGORA.plusHours(1));
        lotes.salvar(lote);

        var consultado = casoDeUso.consultar(lote.getId());

        assertEquals(EstadoLote.ESGOTADO, consultado.getEstado());
        assertEquals(0, consultado.getQuantidade());
    }

    @Test
    void deveIndicarLoteInexistente() {
        assertThrows(RecursoNaoEncontrado.class, () -> casoDeUso.consultar(42));
    }

    private static Lote novoLote(String codigo) {
        return Lote.novo(codigo, "Febre amarela", "Bio-Manguinhos", LocalDate.of(2027, 3, 31),
                1200, 7, AGORA.toLocalDate(), AGORA);
    }
}

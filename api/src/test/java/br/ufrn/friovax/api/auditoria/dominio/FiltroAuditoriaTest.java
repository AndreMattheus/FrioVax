package br.ufrn.friovax.api.auditoria.dominio;

import br.ufrn.friovax.api.autenticacao.dominio.Perfil;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FiltroAuditoriaTest {
    private static final OffsetDateTime T = OffsetDateTime.parse("2026-11-18T12:00:00Z");
    private static final UUID ANA = UUID.randomUUID();
    private static final EventoDeAuditoria EVENTO = EventoDeAuditoria.novo(T, Ator.usuario(ANA, "ana",
            Perfil.ADMINISTRADOR), RecursoAuditado.LOTE, 5, OperacaoAuditada.DESCARTE, "Descarte", "req-1");

    @Test
    void periodoEInclusivoNasDuasPontas() {
        assertTrue(new FiltroAuditoria(T, T, null, null, null).aceita(EVENTO));
        assertFalse(new FiltroAuditoria(T.plusNanos(1000), null, null, null, null).aceita(EVENTO));
        assertFalse(new FiltroAuditoria(null, T.minusNanos(1000), null, null, null).aceita(EVENTO));
    }

    @Test
    void combinaUsuarioERecurso() {
        assertTrue(new FiltroAuditoria(null, null, ANA, RecursoAuditado.LOTE, 5L).aceita(EVENTO));
        assertFalse(new FiltroAuditoria(null, null, UUID.randomUUID(), null, null).aceita(EVENTO));
        assertFalse(new FiltroAuditoria(null, null, null, RecursoAuditado.CAMARA, null).aceita(EVENTO));
        assertFalse(new FiltroAuditoria(null, null, null, RecursoAuditado.LOTE, 6L).aceita(EVENTO));
        assertTrue(FiltroAuditoria.nenhum().aceita(EVENTO));
    }

    @Test
    void recusaPeriodoInvertidoEIdSemTipo() {
        assertThrows(IllegalArgumentException.class, () -> new FiltroAuditoria(T, T.minusSeconds(1), null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new FiltroAuditoria(null, null, null, null, 5L));
        assertThrows(IllegalArgumentException.class,
                () -> new FiltroAuditoria(null, null, null, RecursoAuditado.LOTE, 0L));
    }

    @Test
    void sistemaNuncaCasaComFiltroDeUsuario() {
        var doSistema = EventoDeAuditoria.novo(T, Ator.SISTEMA, RecursoAuditado.CAMARA, 1,
                OperacaoAuditada.ATUALIZACAO, "Atualização", "sistema-1");
        assertFalse(new FiltroAuditoria(null, null, ANA, null, null).aceita(doSistema));
    }
}

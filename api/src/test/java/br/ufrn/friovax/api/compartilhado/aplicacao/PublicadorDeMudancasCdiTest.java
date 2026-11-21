package br.ufrn.friovax.api.compartilhado.aplicacao;

import br.ufrn.friovax.api.auditoria.dominio.Ator;
import br.ufrn.friovax.api.auditoria.dominio.AuditoriaRepository;
import br.ufrn.friovax.api.auditoria.dominio.EventoDeAuditoria;
import br.ufrn.friovax.api.auditoria.dominio.FiltroAuditoria;
import br.ufrn.friovax.api.auditoria.dominio.OperacaoAuditada;
import br.ufrn.friovax.api.auditoria.dominio.RecursoAuditado;
import br.ufrn.friovax.api.compartilhado.dominio.Paginacao;
import br.ufrn.friovax.api.suporte.ColetorDeMudancas;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** O aviso de mudança confirmada só chega aos observadores depois do commit (S2-05). */
@QuarkusTest
class PublicadorDeMudancasCdiTest {

    @Inject
    PublicadorDeMudancas publicador;

    @Inject
    ColetorDeMudancas coletor;

    @Inject
    AuditoriaRepository trilha;

    @Test
    void entregaSomenteDepoisDoCommit() {
        long id = idUnico();

        QuarkusTransaction.requiringNew().run(() -> {
            publicador.publicar(mudanca(id));
            assertTrue(coletor.recebidasDe("teste", id).isEmpty(), "ainda não houve commit");
        });

        assertEquals(1, coletor.recebidasDe("teste", id).size());
    }

    @Test
    void descartaQuandoATransacaoEDesfeita() {
        long id = idUnico();

        assertThrows(IllegalStateException.class, () -> QuarkusTransaction.requiringNew().run(() -> {
            publicador.publicar(mudanca(id));
            throw new IllegalStateException("falha depois de publicar");
        }));

        assertTrue(coletor.recebidasDe("teste", id).isEmpty());
    }

    @Test
    void exigeTransacaoAtiva() {
        assertThrows(IllegalStateException.class, () -> publicador.publicar(mudanca(idUnico())));
    }

    @Test
    void mudancaAuditadaGravaEventoNaMesmaTransacao() {
        long id = idUnico();
        var instante = OffsetDateTime.parse("2026-11-18T10:00:00-03:00");

        QuarkusTransaction.requiringNew().run(() -> publicador.publicar(MudancaConfirmada.auditada(
                RecursoAuditado.LOTE, id, OperacaoAuditada.DESCARTE, "Lote de teste descartado", instante)));

        var eventos = eventosDoLote(id);
        assertEquals(1, eventos.size());
        var evento = eventos.get(0);
        assertEquals(OperacaoAuditada.DESCARTE, evento.operacao());
        assertEquals("Lote de teste descartado", evento.descricao());
        assertEquals(instante.toInstant(), evento.instante().toInstant());
        assertEquals(Ator.SISTEMA, evento.ator(), "sem usuário autenticado, o ator é o SISTEMA");
        assertTrue(evento.correlacaoId().startsWith("sistema-"), evento.correlacaoId());
        assertEquals(1, coletor.recebidasDe("lote", id).size());
    }

    @Test
    void rollbackDesfazOEventoDeAuditoria() {
        long id = idUnico();

        assertThrows(IllegalStateException.class, () -> QuarkusTransaction.requiringNew().run(() -> {
            publicador.publicar(MudancaConfirmada.auditada(RecursoAuditado.LOTE, id, OperacaoAuditada.BAIXA,
                    "Baixa de teste", OffsetDateTime.now()));
            throw new IllegalStateException("falha depois de publicar");
        }));

        assertTrue(eventosDoLote(id).isEmpty());
        assertTrue(coletor.recebidasDe("lote", id).isEmpty());
    }

    @Test
    void mudancaSemOperacaoNaoEntraNaTrilha() {
        long id = idUnico();

        QuarkusTransaction.requiringNew().run(() -> publicador.publicar(
                new MudancaConfirmada("lote", id, "Só invalida projeções", null, OffsetDateTime.now())));

        assertTrue(eventosDoLote(id).isEmpty());
        assertEquals(1, coletor.recebidasDe("lote", id).size());
    }

    private List<EventoDeAuditoria> eventosDoLote(long id) {
        return QuarkusTransaction.requiringNew().call(() -> trilha.listar(
                new FiltroAuditoria(null, null, null, RecursoAuditado.LOTE, id), new Paginacao(0, 10))).itens();
    }

    private static MudancaConfirmada mudanca(long id) {
        return new MudancaConfirmada("teste", id, "mudança de teste", null, OffsetDateTime.now());
    }

    private static long idUnico() {
        return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }
}

package br.ufrn.friovax.api.auditoria.persistencia;

import br.ufrn.friovax.api.auditoria.dominio.Ator;
import br.ufrn.friovax.api.auditoria.dominio.EventoDeAuditoria;
import br.ufrn.friovax.api.auditoria.dominio.FiltroAuditoria;
import br.ufrn.friovax.api.auditoria.dominio.OperacaoAuditada;
import br.ufrn.friovax.api.auditoria.dominio.RecursoAuditado;
import br.ufrn.friovax.api.autenticacao.dominio.Perfil;
import br.ufrn.friovax.api.compartilhado.dominio.Paginacao;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** A tabela só cresce (não há DELETE): cada teste usa um usuário e um recurso próprios para se isolar. */
@QuarkusTest
class AuditoriaRepositoryPostgresTest {
    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-11-18T09:00:00.123456-03:00");

    @Inject AuditoriaRepositoryPostgres auditoria;
    @Inject EntityManager entityManager;

    private static long recursoUnico() {
        return ThreadLocalRandom.current().nextLong(1_000_000_000L, Long.MAX_VALUE);
    }

    private EventoDeAuditoria gravar(OffsetDateTime instante, Ator ator, RecursoAuditado recurso, long id,
                                     OperacaoAuditada operacao) {
        return QuarkusTransaction.requiringNew().call(() -> auditoria.registrar(EventoDeAuditoria.novo(instante, ator,
                recurso, id, operacao, operacao + " de teste", "teste-" + id)));
    }

    @Test
    void gravaELeTodosOsCamposDeVolta() {
        var ana = Ator.usuario(UUID.randomUUID(), "ana", Perfil.ADMINISTRADOR);
        long lote = recursoUnico();

        var gravado = gravar(T0, ana, RecursoAuditado.LOTE, lote, OperacaoAuditada.BAIXA);
        entityManager.clear();
        var lidos = QuarkusTransaction.requiringNew().call(() -> auditoria.listar(
                new FiltroAuditoria(null, null, ana.id(), null, null), new Paginacao(0, 10)));

        assertNotNull(gravado.id());
        assertEquals(1, lidos.totalElementos());
        var lido = lidos.itens().get(0);
        assertEquals(gravado.id(), lido.id());
        assertEquals(ana, lido.ator());
        assertEquals(RecursoAuditado.LOTE, lido.recurso());
        assertEquals(lote, lido.recursoId());
        assertEquals(OperacaoAuditada.BAIXA, lido.operacao());
        assertEquals("BAIXA de teste", lido.descricao());
        assertEquals("teste-" + lote, lido.correlacaoId());
        assertEquals(T0.toInstant(), lido.instante().toInstant(), "microssegundos preservados");
    }

    @Test
    void sistemaEGravadoSemId() {
        long camara = recursoUnico();
        gravar(T0, Ator.SISTEMA, RecursoAuditado.CAMARA, camara, OperacaoAuditada.ATUALIZACAO);

        var lido = QuarkusTransaction.requiringNew().call(() -> auditoria.listar(
                new FiltroAuditoria(null, null, null, RecursoAuditado.CAMARA, camara), new Paginacao(0, 10)))
                .itens().get(0);

        assertNull(lido.ator().id());
        assertEquals(Ator.SISTEMA, lido.ator());
    }

    @Test
    void filtraPorPeriodoUsuarioERecursoEPagina() {
        var bia = Ator.usuario(UUID.randomUUID(), "bia", Perfil.OPERADOR);
        var outro = Ator.usuario(UUID.randomUUID(), "outro", Perfil.ADMINISTRADOR);
        long lote = recursoUnico();
        long camara = recursoUnico();
        var e1 = gravar(T0, bia, RecursoAuditado.LOTE, lote, OperacaoAuditada.BAIXA);
        var e2 = gravar(T0.plusMinutes(1), bia, RecursoAuditado.LOTE, lote, OperacaoAuditada.BAIXA);
        var e3 = gravar(T0.plusMinutes(2), bia, RecursoAuditado.CAMARA, camara, OperacaoAuditada.ATUALIZACAO);
        gravar(T0.plusMinutes(1), outro, RecursoAuditado.LOTE, lote, OperacaoAuditada.DESCARTE);

        var daBia = new FiltroAuditoria(null, null, bia.id(), null, null);
        assertEquals(List.of(e1.id(), e2.id()), ids(daBia, new Paginacao(0, 2)));
        assertEquals(List.of(e3.id()), ids(daBia, new Paginacao(1, 2)));
        assertEquals(3, QuarkusTransaction.requiringNew().call(() -> auditoria.listar(daBia, new Paginacao(1, 2)))
                .totalElementos());

        assertEquals(List.of(e2.id(), e3.id()), ids(new FiltroAuditoria(T0.plusMinutes(1), T0.plusMinutes(2),
                bia.id(), null, null), new Paginacao(0, 10)), "período inclusivo nas duas pontas");
        assertEquals(List.of(e1.id(), e2.id()), ids(new FiltroAuditoria(null, null, bia.id(),
                RecursoAuditado.LOTE, lote), new Paginacao(0, 10)));
        assertEquals(List.of(e3.id()), ids(new FiltroAuditoria(null, null, null, RecursoAuditado.CAMARA, camara),
                new Paginacao(0, 10)));
    }

    @Test
    void transacaoDesfeitaNaoDeixaEvento() {
        var ana = Ator.usuario(UUID.randomUUID(), "ana", Perfil.ADMINISTRADOR);

        assertThrows(IllegalStateException.class, () -> QuarkusTransaction.requiringNew().run(() -> {
            auditoria.registrar(EventoDeAuditoria.novo(T0, ana, RecursoAuditado.LOTE, recursoUnico(),
                    OperacaoAuditada.DESCARTE, "Descarte", "req-rollback"));
            throw new IllegalStateException("falha depois de registrar");
        }));

        assertEquals(0, QuarkusTransaction.requiringNew().call(() -> auditoria.listar(
                new FiltroAuditoria(null, null, ana.id(), null, null), new Paginacao(0, 10))).totalElementos());
    }

    @Test
    void naoRegravaEventoComId() {
        var gravado = gravar(T0, Ator.SISTEMA, RecursoAuditado.LOTE, recursoUnico(), OperacaoAuditada.CADASTRO);

        assertThrows(IllegalStateException.class,
                () -> QuarkusTransaction.requiringNew().run(() -> auditoria.registrar(gravado)));
    }

    private List<Long> ids(FiltroAuditoria filtro, Paginacao paginacao) {
        return QuarkusTransaction.requiringNew().call(() -> auditoria.listar(filtro, paginacao)).itens().stream()
                .map(EventoDeAuditoria::id).toList();
    }
}

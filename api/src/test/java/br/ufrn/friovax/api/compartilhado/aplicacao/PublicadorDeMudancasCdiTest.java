package br.ufrn.friovax.api.compartilhado.aplicacao;

import br.ufrn.friovax.api.suporte.ColetorDeMudancas;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
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

    private static MudancaConfirmada mudanca(long id) {
        return new MudancaConfirmada("teste", id, "mudança de teste", null, OffsetDateTime.now());
    }

    private static long idUnico() {
        return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }
}

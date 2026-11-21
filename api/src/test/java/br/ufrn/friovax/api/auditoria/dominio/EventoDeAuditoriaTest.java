package br.ufrn.friovax.api.auditoria.dominio;

import br.ufrn.friovax.api.autenticacao.dominio.Perfil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventoDeAuditoriaTest {
    private static final UUID USUARIO = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Ator ADMIN = Ator.usuario(USUARIO, "admin", Perfil.ADMINISTRADOR);

    private static EventoDeAuditoria evento(String descricao, String correlacao) {
        return EventoDeAuditoria.novo(OffsetDateTime.parse("2026-11-18T09:00:00.123456789-03:00"), ADMIN,
                RecursoAuditado.LOTE, 7, OperacaoAuditada.BAIXA, descricao, correlacao);
    }

    @Test
    void normalizaInstanteParaUtcEmMicrossegundos() {
        var evento = evento("Baixa de 2 doses", "req-1");

        assertEquals(OffsetDateTime.parse("2026-11-18T12:00:00.123456Z"), evento.instante());
        assertNull(evento.id());
        assertEquals(10L, evento.comId(10).id());
    }

    @Test
    void naoGravaDuasVezes() {
        var gravado = evento("Baixa", "req-1").comId(1);

        assertThrows(IllegalStateException.class, () -> gravado.comId(2));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "com espaço", "barra/invalida", "acentuação"})
    void recusaCorrelacaoForaDoFormato(String correlacao) {
        assertThrows(IllegalArgumentException.class, () -> evento("Baixa", correlacao));
    }

    @Test
    void aceitaCorrelacaoNoLimiteDe128Caracteres() {
        assertEquals(128, evento("Baixa", "a".repeat(128)).correlacaoId().length());
        assertThrows(IllegalArgumentException.class, () -> evento("Baixa", "a".repeat(129)));
    }

    @Test
    void recusaDescricaoVaziaOuLonga() {
        assertThrows(IllegalArgumentException.class, () -> evento(" ", "req-1"));
        assertThrows(IllegalArgumentException.class, () -> evento("x".repeat(501), "req-1"));
    }

    @Test
    void recusaRecursoSemId() {
        assertThrows(IllegalArgumentException.class, () -> EventoDeAuditoria.novo(OffsetDateTime.now(), ADMIN,
                RecursoAuditado.CAMARA, 0, OperacaoAuditada.CADASTRO, "Cadastro", "req-1"));
    }

    @Test
    void sistemaNaoTemIdEUsuarioSempreTem() {
        assertTrue(Ator.SISTEMA.isSistema());
        assertThrows(IllegalArgumentException.class, () -> new Ator(null, "admin", "ADMINISTRADOR"));
        assertThrows(IllegalArgumentException.class, () -> new Ator(USUARIO, "SISTEMA", "SISTEMA"));
        assertThrows(IllegalArgumentException.class, () -> new Ator(USUARIO, "admin", "ROOT"));
        assertThrows(IllegalArgumentException.class, () -> new Ator(USUARIO, " ", "OPERADOR"));
    }
}

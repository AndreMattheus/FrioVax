package br.ufrn.friovax.api.telemetria.persistencia;

import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.CamaraRepository;
import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import br.ufrn.friovax.api.lote.dominio.Lote;
import br.ufrn.friovax.api.lote.dominio.LoteRepository;
import br.ufrn.friovax.api.telemetria.dominio.Leitura;
import br.ufrn.friovax.api.telemetria.dominio.LoteAfetado;
import br.ufrn.friovax.api.telemetria.dominio.Ocorrencia;
import br.ufrn.friovax.api.telemetria.dominio.ResultadoTermico;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class TelemetriaPersistenciaPostgresTest {
    private static final OffsetDateTime AGORA = OffsetDateTime.parse("2026-11-12T10:00:00-03:00");

    @Inject CamaraRepository camaras;
    @Inject LoteRepository lotes;
    @Inject LeituraRepositoryPostgres leituras;
    @Inject OcorrenciaRepositoryPostgres ocorrencias;
    @Inject EntityManager entityManager;

    private long camaraId;
    private long loteId;

    @BeforeEach
    void criarCamaraELote() {
        var sufixo = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        camaraId = QuarkusTransaction.requiringNew().call(() -> camaras.salvar(Camara.nova("T-" + sufixo, "Câmara",
                "UBS teste", 1000, new BigDecimal("2.0"), new BigDecimal("8.0"), EstadoCamara.OPERACIONAL,
                AGORA)).getId());
        loteId = QuarkusTransaction.requiringNew().call(() -> lotes.salvar(Lote.novo("L-" + sufixo, "Febre amarela",
                "Fabricante", LocalDate.of(2030, 1, 1), 100, camaraId, AGORA.toLocalDate(), AGORA)).getId());
    }

    @AfterEach
    void apagar() {
        // Ordem importa: a fotografia aponta para o lote, e o lote não tem cascata.
        QuarkusTransaction.requiringNew().run(() -> {
            entityManager.createNativeQuery("delete from ocorrencias where camara_id = :id")
                    .setParameter("id", camaraId).executeUpdate();
            entityManager.createNativeQuery("delete from lotes where camara_id = :id")
                    .setParameter("id", camaraId).executeUpdate();
            entityManager.createNativeQuery("delete from camaras where id = :id")
                    .setParameter("id", camaraId).executeUpdate();
        });
    }

    @Test
    void gravaERecuperaALeituraComEntradaPoliticaEResultado() {
        long id = salvarLeitura("S-1", AGORA.minusMinutes(5), "9.5", ResultadoTermico.insegura(1, false));

        var lida = QuarkusTransaction.requiringNew().call(() -> leituras.buscarPorId(id).orElseThrow());

        assertEquals(camaraId, lida.getCamaraId());
        assertEquals("S-1", lida.getIdExterno());
        assertTrue(lida.getInstante().isEqual(AGORA.minusMinutes(5)));
        assertEquals(0, new BigDecimal("9.5").compareTo(lida.getTemperatura()));
        assertEquals("sensor-1", lida.getOrigem());
        assertEquals(1, lida.getPoliticaVersao());
        assertEquals(ResultadoTermico.insegura(1, false), lida.getResultado());
    }

    @Test
    void gravaERecuperaAOcorrenciaComEvidenciasELotesAfetados() {
        long l1 = salvarLeitura("S-1", AGORA.minusMinutes(3), "9.0", ResultadoTermico.insegura(1, false));
        long l2 = salvarLeitura("S-2", AGORA.minusMinutes(2), "9.0", ResultadoTermico.insegura(2, false));
        long l3 = salvarLeitura("S-3", AGORA.minusMinutes(1), "9.0", ResultadoTermico.insegura(3, true));
        var fotografia = List.of(new LoteAfetado(loteId, "L-X", "Febre amarela", 100));

        long ocorrenciaId = QuarkusTransaction.requiringNew().call(() -> ocorrencias.salvar(Ocorrencia.abrir(
                camaraId, 1, l3, List.of(l1, l2, l3), fotografia, false, AGORA)).getId());

        var lida = QuarkusTransaction.requiringNew().call(() -> ocorrencias.buscarPorId(ocorrenciaId).orElseThrow());
        assertTrue(lida.isAberta());
        assertEquals(List.of(l1, l2, l3), lida.getEvidencias());
        assertEquals(fotografia, lida.getLotesAfetados());
        assertEquals(ocorrenciaId, QuarkusTransaction.requiringNew()
                .call(() -> ocorrencias.abertaDaCamara(camaraId).orElseThrow().getId()));
    }

    private long salvarLeitura(String idExterno, OffsetDateTime instante, String temperatura,
                               ResultadoTermico resultado) {
        return QuarkusTransaction.requiringNew().call(() -> leituras.salvar(Leitura.nova(camaraId, idExterno,
                instante, new BigDecimal(temperatura), "sensor-1", 1, resultado, AGORA)).getId());
    }
}

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
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Primeira metade da prova de persistência após reinício da API: grava câmara, lote, três leituras e uma ocorrência
 * com evidências e fotografia do lote, cada uma na sua transação confirmada. Não apaga nada; a limpeza fica com
 * {@link PersistenciaAposReinicio2ConferenciaTest}, que roda depois do reinício (ver {@link ReinicioDaApi}).
 */
@QuarkusTest
@TestProfile(ReinicioDaApi.AntesDoReinicio.class)
class PersistenciaAposReinicio1GravacaoTest {

    /** Com microssegundos, para conferir também que a fração não se perde no banco. */
    static final OffsetDateTime T0 = OffsetDateTime.parse("2026-11-14T10:00:00.123456-03:00");

    @Inject CamaraRepository camaras;
    @Inject LoteRepository lotes;
    @Inject LeituraRepositoryPostgres leituras;
    @Inject OcorrenciaRepositoryPostgres ocorrencias;
    @Inject IdentidadeDaAplicacao aplicacao;

    @Test
    void gravaOsDadosAntesDoReinicio() {
        var sufixo = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        var codigoLote = "L-R-" + sufixo;

        long camaraId = QuarkusTransaction.requiringNew().call(() -> camaras.salvar(Camara.nova(
                ReinicioDaApi.PREFIXO_CODIGO + sufixo, ReinicioDaApi.nomeDaCamara(aplicacao.id()), "UBS teste",
                1000, new BigDecimal("2.0"), new BigDecimal("8.0"), EstadoCamara.OPERACIONAL, T0)).getId());
        long loteId = QuarkusTransaction.requiringNew().call(() -> lotes.salvar(Lote.novo(codigoLote,
                ReinicioDaApi.IMUNOBIOLOGICO, "Fabricante", LocalDate.of(2030, 1, 1), 100, camaraId,
                T0.toLocalDate(), T0)).getId());

        long l1 = salvarLeitura(camaraId, 1, ResultadoTermico.insegura(1, false));
        long l2 = salvarLeitura(camaraId, 2, ResultadoTermico.insegura(2, false));
        long l3 = salvarLeitura(camaraId, 3, ResultadoTermico.insegura(3, true));
        var fotografia = List.of(new LoteAfetado(loteId, codigoLote, ReinicioDaApi.IMUNOBIOLOGICO, 100));
        QuarkusTransaction.requiringNew().run(() -> ocorrencias.salvar(Ocorrencia.abrir(
                camaraId, 1, l3, List.of(l1, l2, l3), fotografia, false, T0.plusMinutes(3))));
    }

    private long salvarLeitura(long camaraId, int minutos, ResultadoTermico resultado) {
        return QuarkusTransaction.requiringNew().call(() -> leituras.salvar(Leitura.nova(camaraId, "R-" + minutos,
                T0.plusMinutes(minutos), new BigDecimal("9.5"), "sensor-1", 1, resultado, T0.plusMinutes(minutos)))
                .getId());
    }
}

package br.ufrn.friovax.api.telemetria.persistencia;

import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.CamaraRepository;
import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import br.ufrn.friovax.api.compartilhado.dominio.Paginacao;
import br.ufrn.friovax.api.lote.dominio.Lote;
import br.ufrn.friovax.api.lote.dominio.LoteRepository;
import br.ufrn.friovax.api.lote.dominio.MotivoBaixa;
import br.ufrn.friovax.api.telemetria.dominio.Leitura;
import br.ufrn.friovax.api.telemetria.dominio.LeituraForaDeOrdem;
import br.ufrn.friovax.api.telemetria.dominio.LeituraJaRegistrada;
import br.ufrn.friovax.api.telemetria.dominio.LoteAfetado;
import br.ufrn.friovax.api.telemetria.dominio.Ocorrencia;
import br.ufrn.friovax.api.telemetria.dominio.OcorrenciaJaAberta;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class TelemetriaRegrasPostgresTest {
    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-11-12T10:00:00-03:00");

    @Inject CamaraRepository camaras;
    @Inject LoteRepository lotes;
    @Inject LeituraRepositoryPostgres leituras;
    @Inject OcorrenciaRepositoryPostgres ocorrencias;
    @Inject EntityManager entityManager;

    private long camaraId;
    private long loteId;
    private String codigoLote;

    @BeforeEach
    void criarCamaraELote() {
        var sufixo = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        codigoLote = "L-" + sufixo;
        camaraId = QuarkusTransaction.requiringNew().call(() -> camaras.salvar(Camara.nova("R-" + sufixo, "Câmara",
                "UBS teste", 1000, new BigDecimal("2.0"), new BigDecimal("8.0"), EstadoCamara.OPERACIONAL,
                T0)).getId());
        loteId = QuarkusTransaction.requiringNew().call(() -> lotes.salvar(Lote.novo(codigoLote, "Febre amarela",
                "Fabricante", LocalDate.of(2030, 1, 1), 100, camaraId, T0.toLocalDate(), T0)).getId());
    }

    @AfterEach
    void apagar() {
        QuarkusTransaction.requiringNew().run(() -> {
            nativo("delete from ocorrencias where camara_id = :id");
            nativo("delete from lotes where camara_id = :id");
            nativo("delete from camaras where id = :id"); // leva junto leituras e políticas
        });
    }

    // ---------- leituras ----------

    @Test
    void mesmaChaveExternaEhRecusadaEADesfeita() {
        salvar("S-1", 0, 1, ResultadoTermico.segura());

        assertThrows(LeituraJaRegistrada.class, () -> salvar("S-1", 5, 1, ResultadoTermico.segura()));

        assertEquals(1, contar("select count(*) from leituras where camara_id = :id"));
    }

    @Test
    void mesmoInstanteNaMesmaCamaraEhRecusado() {
        salvar("S-1", 0, 1, ResultadoTermico.segura());

        assertThrows(LeituraForaDeOrdem.class, () -> salvar("S-2", 0, 1, ResultadoTermico.segura()));
    }

    @Test
    void bancoNaoImpedeInstanteAnteriorAUltimaLeitura() {
        // A constraint só barra instantes iguais. "Posterior à última leitura" (ADR 0003, seção 5) é conferido pela
        // recepção (S2-10) com a câmara bloqueada, usando ultimaDaCamara.
        salvar("S-1", 5, 1, ResultadoTermico.segura());
        long anterior = salvar("S-2", 1, 1, ResultadoTermico.segura());

        assertEquals("S-1", transacao(() -> leituras.ultimaDaCamara(camaraId).orElseThrow().getIdExterno()));
        assertTrue(transacao(() -> leituras.buscarPorId(anterior)).isPresent());
    }

    @Test
    void leituraNumaVersaoDePoliticaInexistenteEhRecusadaPeloBanco() {
        assertThrows(RuntimeException.class, () -> salvar("S-1", 0, 99, ResultadoTermico.segura()));

        assertEquals(0, contar("select count(*) from leituras where camara_id = :id"));
    }

    // ---------- ocorrências ----------

    @Test
    void bancoRecusaSegundaOcorrenciaAbertaMesmoSemORegraDoDominio() {
        long l1 = salvar("S-1", 1, 1, ResultadoTermico.insegura(3, true));
        long l2 = salvar("S-2", 2, 1, ResultadoTermico.insegura(3, true));
        abrir(l1, List.of());

        // Passa "não existe aberta" de propósito, para provar que o banco também barra.
        assertThrows(OcorrenciaJaAberta.class, () -> abrir(l2, List.of()));
    }

    @Test
    void depoisDaResolucaoUmaNovaOcorrenciaPodeSerAberta() {
        long l1 = salvar("S-1", 1, 1, ResultadoTermico.insegura(3, true));
        long l2 = salvar("S-2", 2, 1, ResultadoTermico.segura());
        long l3 = salvar("S-3", 3, 1, ResultadoTermico.insegura(3, true));
        long primeira = abrir(l1, List.of());

        QuarkusTransaction.requiringNew().run(() -> {
            var ocorrencia = ocorrencias.buscarPorId(primeira).orElseThrow();
            ocorrencia.resolver("Câmara verificada", l2, null, T0.plusHours(1));
            ocorrencias.salvar(ocorrencia);
        });
        long segunda = abrir(l3, List.of());

        assertEquals(primeira, transacao(() -> ocorrencias.ultimaResolvidaDaCamara(camaraId)).orElseThrow().getId());
        assertEquals(segunda, transacao(() -> ocorrencias.abertaDaCamara(camaraId)).orElseThrow().getId());
        assertEquals(l2, transacao(() -> ocorrencias.buscarPorId(primeira)).orElseThrow()
                .getLeituraResolucaoId().orElseThrow());
    }

    @Test
    void fotografiaNaoMudaQuandoOLoteRecebeBaixaEEInativado() {
        long l1 = salvar("S-1", 1, 1, ResultadoTermico.insegura(3, true));
        long ocorrenciaId = abrir(l1, List.of(new LoteAfetado(loteId, codigoLote, "Febre amarela", 100)));

        QuarkusTransaction.requiringNew().run(() -> {
            var lote = lotes.buscarPorIdParaAlteracao(loteId).orElseThrow();
            lote.darBaixa(30, MotivoBaixa.PERDA, T0.plusHours(1));
            lote.inativar(T0.plusHours(2));
            lotes.salvar(lote);
        });

        var fotografia = transacao(() -> ocorrencias.buscarPorId(ocorrenciaId)).orElseThrow().getLotesAfetados();
        assertEquals(List.of(new LoteAfetado(loteId, codigoLote, "Febre amarela", 100)), fotografia);
    }

    @Test
    void bancoRecusaOMesmoLoteDuasVezesNaFotografia() {
        long l1 = salvar("S-1", 1, 1, ResultadoTermico.insegura(3, true));
        long ocorrenciaId = abrir(l1, List.of(new LoteAfetado(loteId, codigoLote, "Febre amarela", 100)));

        assertThrows(RuntimeException.class, () -> QuarkusTransaction.requiringNew().run(() -> entityManager
                .createNativeQuery("""
                        insert into ocorrencia_lotes (ocorrencia_id, lote_id, codigo_lote, imunobiologico, quantidade)
                        values (:ocorrencia, :lote, 'X', 'Y', 1)
                        """)
                .setParameter("ocorrencia", ocorrenciaId)
                .setParameter("lote", loteId)
                .executeUpdate()));
    }

    @Test
    void falhaNoMeioDesfazLeituraOcorrenciaEvidenciasEFotografia() {
        assertThrows(IllegalStateException.class, () -> QuarkusTransaction.requiringNew().run(() -> {
            var leitura = leituras.salvar(Leitura.nova(camaraId, "S-1", T0, new BigDecimal("9.0"), "sensor", 1,
                    ResultadoTermico.insegura(1, true), T0));
            ocorrencias.salvar(Ocorrencia.abrir(camaraId, 1, leitura.getId(), List.of(leitura.getId()),
                    List.of(new LoteAfetado(loteId, codigoLote, "Febre amarela", 100)), false, T0));
            throw new IllegalStateException("falha depois de gravar tudo, na mesma transação");
        }));

        assertEquals(0, contar("select count(*) from leituras where camara_id = :id"));
        assertEquals(0, contar("select count(*) from ocorrencias where camara_id = :id"));
        assertEquals(0, contar("""
                select count(*) from ocorrencia_lotes ol join lotes l on l.id = ol.lote_id where l.camara_id = :id
                """));
    }

    // ---------- consultas ----------

    @Test
    void contextoDaJanelaLimitadoOrdenadoNaVersaoVigenteESemAtravessarOReinicio() {
        long a = salvar("A", 1, 1, ResultadoTermico.insegura(1, false));
        salvar("B", 2, 1, ResultadoTermico.insegura(2, false));
        salvar("C", 3, 1, ResultadoTermico.segura());
        novaVersaoDaPolitica();
        salvar("D", 4, 2, ResultadoTermico.segura());

        assertEquals(List.of("B", "C"), ids(transacao(() -> leituras.contextoDaJanela(camaraId, 1, 2, null))));
        assertEquals(List.of("A", "B", "C"), ids(transacao(() -> leituras.contextoDaJanela(camaraId, 1, 99, null))));
        assertEquals(List.of("B", "C"), ids(transacao(() -> leituras.contextoDaJanela(camaraId, 1, 99, a))));
        assertEquals(List.of("D"), ids(transacao(() -> leituras.contextoDaJanela(camaraId, 2, 99, null))));
        assertTrue(transacao(() -> leituras.contextoDaJanela(camaraId, 1, 0, null)).isEmpty());
    }

    @Test
    void listagensPaginadasEmOrdemDeterministica() {
        salvar("A", 1, 1, ResultadoTermico.segura());
        salvar("B", 2, 1, ResultadoTermico.segura());
        salvar("C", 3, 1, ResultadoTermico.segura());

        var primeira = transacao(() -> leituras.listarDaCamara(camaraId, new Paginacao(0, 2)));
        var segunda = transacao(() -> leituras.listarDaCamara(camaraId, new Paginacao(1, 2)));

        assertEquals(List.of("C", "B"), ids(primeira.itens()));
        assertEquals(List.of("A"), ids(segunda.itens()));
        assertEquals(3, primeira.totalElementos());
        assertEquals("C", transacao(() -> leituras.ultimaDaCamara(camaraId)).orElseThrow().getIdExterno());
        assertEquals("B", transacao(() -> leituras.buscarPorChave(camaraId, "B")).orElseThrow().getIdExterno());
    }

    @Test
    void dadosPermanecemNumaNovaSessaoDoBanco() {
        long l1 = salvar("S-1", 1, 1, ResultadoTermico.insegura(3, true));
        long ocorrenciaId = abrir(l1, List.of(new LoteAfetado(loteId, codigoLote, "Febre amarela", 100)));

        // Nova transação, com um contexto de persistência vazio: os dados vêm do banco, não da memória.
        var lida = QuarkusTransaction.requiringNew().call(() -> {
            entityManager.clear();
            return ocorrencias.abertaPelaLeitura(l1).orElseThrow();
        });

        assertEquals(ocorrenciaId, lida.getId());
        assertEquals(List.of(l1), lida.getEvidencias());
        assertEquals(1, lida.getLotesAfetados().size());
    }

    // ---------- apoio ----------

    private long salvar(String idExterno, int minutos, int versao, ResultadoTermico resultado) {
        return QuarkusTransaction.requiringNew().call(() -> leituras.salvar(Leitura.nova(camaraId, idExterno,
                T0.plusMinutes(minutos), new BigDecimal("9.0"), "sensor", versao, resultado, T0)).getId());
    }

    private long abrir(long leituraId, List<LoteAfetado> fotografia) {
        return QuarkusTransaction.requiringNew().call(() -> ocorrencias.salvar(Ocorrencia.abrir(camaraId, 1,
                leituraId, List.of(leituraId), fotografia, false, T0)).getId());
    }

    private void novaVersaoDaPolitica() {
        QuarkusTransaction.requiringNew().run(() -> {
            var camara = camaras.buscarPorIdParaAlteracao(camaraId).orElseThrow();
            camara.alterarPolitica(new BigDecimal("3.0"), new BigDecimal("7.0"), 3, null, T0.plusMinutes(3));
            camaras.salvar(camara);
        });
    }

    private <T> T transacao(java.util.concurrent.Callable<T> consulta) {
        return QuarkusTransaction.requiringNew().call(consulta);
    }

    private long contar(String sql) {
        return transacao(() -> ((Number) entityManager.createNativeQuery(sql)
                .setParameter("id", camaraId).getSingleResult()).longValue());
    }

    private void nativo(String sql) {
        entityManager.createNativeQuery(sql).setParameter("id", camaraId).executeUpdate();
    }

    private static List<String> ids(List<Leitura> lista) {
        return lista.stream().map(Leitura::getIdExterno).toList();
    }
}

package br.ufrn.friovax.api.integracao;

import br.ufrn.friovax.api.camara.aplicacao.AtualizarCamara;
import br.ufrn.friovax.api.camara.aplicacao.AtualizarCamaraDTO;
import br.ufrn.friovax.api.camara.aplicacao.InativarCamara;
import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.CamaraRepository;
import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import br.ufrn.friovax.api.compartilhado.dominio.CapacidadeExcedida;
import br.ufrn.friovax.api.compartilhado.dominio.CodigoDuplicado;
import br.ufrn.friovax.api.compartilhado.dominio.EstadoIncompativel;
import br.ufrn.friovax.api.lote.aplicacao.AtualizarLote;
import br.ufrn.friovax.api.lote.aplicacao.CadastrarLote;
import br.ufrn.friovax.api.lote.dominio.LoteRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Exercita os casos de uso em transações reais e simultâneas no PostgreSQL. Os testes de ordem fixa retêm a primeira
 * transação aberta, confirmam pelo {@code pg_stat_activity} que a segunda está esperando um bloqueio e só então deixam
 * a primeira confirmar; assim a segunda sempre decide com o que a primeira gravou.
 */
@QuarkusTest
class AlocacaoConcorrenteTest {

    private static final ZoneId FUSO = ZoneId.of("America/Fortaleza");

    @Inject CadastrarLote cadastrarLote;
    @Inject AtualizarLote atualizarLote;
    @Inject AtualizarCamara atualizarCamara;
    @Inject InativarCamara inativarCamara;
    @Inject CamaraRepository camaras;
    @Inject LoteRepository lotes;
    @Inject EntityManager entityManager;

    private final List<Long> camarasDoTeste = new ArrayList<>();

    @AfterEach
    void limparRegistrosDoTeste() {
        QuarkusTransaction.requiringNew().run(() -> camarasDoTeste.forEach(id -> {
            entityManager.createNativeQuery("delete from lotes where camara_id = :id")
                    .setParameter("id", id).executeUpdate();
            entityManager.createNativeQuery("delete from camaras where id = :id")
                    .setParameter("id", id).executeUpdate();
        }));
    }

    @Test
    void alocacoesSimultaneasNaoUltrapassamACapacidade() throws Exception {
        long camaraId = novaCamara(1000);

        var falhas = simultaneamente(List.of(
                () -> cadastrar(camaraId, 300), () -> cadastrar(camaraId, 300),
                () -> cadastrar(camaraId, 300), () -> cadastrar(camaraId, 300),
                () -> cadastrar(camaraId, 300), () -> cadastrar(camaraId, 300),
                () -> cadastrar(camaraId, 300), () -> cadastrar(camaraId, 300)));

        assertEquals(3, falhas.stream().filter(falha -> falha == null).count());
        assertTrue(falhas.stream().filter(falha -> falha != null).allMatch(CapacidadeExcedida.class::isInstance),
                () -> "falhas inesperadas: " + falhas);
        assertEquals(900, ocupacao(camaraId));
        assertEquals(3, lotesAtivos(camaraId));
    }

    @Test
    void movimentacoesECadastrosSimultaneosNoMesmoDestinoNaoUltrapassamACapacidade() throws Exception {
        long origem = novaCamara(5000);
        long destino = novaCamara(1000);
        var lotesDaOrigem = new ArrayList<Long>();
        for (int i = 0; i < 4; i++) {
            lotesDaOrigem.add(cadastrar(origem, 300));
        }

        var tarefas = new ArrayList<Runnable>();
        lotesDaOrigem.forEach(lote -> tarefas.add(() -> mover(lote, destino, 300)));
        for (int i = 0; i < 4; i++) {
            tarefas.add(() -> cadastrar(destino, 300));
        }
        var falhas = simultaneamente(tarefas);

        long movimentacoes = falhas.subList(0, 4).stream().filter(falha -> falha == null).count();
        assertEquals(3, falhas.stream().filter(falha -> falha == null).count());
        assertTrue(falhas.stream().filter(falha -> falha != null).allMatch(CapacidadeExcedida.class::isInstance),
                () -> "falhas inesperadas: " + falhas);
        assertEquals(900, ocupacao(destino));
        assertEquals(300 * (4 - movimentacoes), ocupacao(origem));
    }

    @Test
    void reducaoDeCapacidadeConfirmadaAntesRecusaAlocacaoQueCabiaNaCapacidadeAnterior() throws Exception {
        long camaraId = novaCamara(1000);
        cadastrar(camaraId, 300);

        var falha = segundaDepoisDaPrimeira(() -> redimensionar(camaraId, 500), () -> cadastrar(camaraId, 300));

        assertInstanceOf(CapacidadeExcedida.class, falha);
        assertEquals(500, capacidade(camaraId));
        assertEquals(300, ocupacao(camaraId));
    }

    @Test
    void alocacaoConfirmadaAntesRecusaReducaoDeCapacidadeAbaixoDaNovaOcupacao() throws Exception {
        long camaraId = novaCamara(1000);
        cadastrar(camaraId, 300);

        var falha = segundaDepoisDaPrimeira(() -> cadastrar(camaraId, 300), () -> redimensionar(camaraId, 500));

        assertInstanceOf(CapacidadeExcedida.class, falha);
        assertEquals(1000, capacidade(camaraId));
        assertEquals(600, ocupacao(camaraId));
    }

    @Test
    void inativacaoConfirmadaAntesRecusaAlocacaoNaCamara() throws Exception {
        long camaraId = novaCamara(1000);

        var falha = segundaDepoisDaPrimeira(() -> inativarCamara.inativar(camaraId), () -> cadastrar(camaraId, 100));

        assertInstanceOf(EstadoIncompativel.class, falha);
        assertFalse(ativa(camaraId));
        assertEquals(0, lotesAtivos(camaraId));
    }

    @Test
    void alocacaoConfirmadaAntesRecusaInativacaoDaCamara() throws Exception {
        long camaraId = novaCamara(1000);

        var falha = segundaDepoisDaPrimeira(() -> cadastrar(camaraId, 100), () -> inativarCamara.inativar(camaraId));

        assertInstanceOf(EstadoIncompativel.class, falha);
        assertTrue(ativa(camaraId));
        assertEquals(100, ocupacao(camaraId));
    }

    @Test
    void movimentacaoConfirmadaAntesOcupaODestinoELiberaAOrigem() throws Exception {
        long origem = novaCamara(1000);
        long destino = novaCamara(1000);
        long lote = cadastrar(origem, 600);
        cadastrar(destino, 300);

        // Sem a movimentação, 300 + 300 caberiam no destino.
        var falha = segundaDepoisDaPrimeira(() -> mover(lote, destino, 600), () -> cadastrar(destino, 300));

        assertInstanceOf(CapacidadeExcedida.class, falha);
        assertEquals(0, ocupacao(origem));
        assertEquals(900, ocupacao(destino));
        assertEquals(destino, camaraDoLote(lote));
    }

    @Test
    void inativacaoDoDestinoConfirmadaAntesRecusaMovimentacaoEMantemLoteNaOrigem() throws Exception {
        long origem = novaCamara(1000);
        long destino = novaCamara(1000);
        long lote = cadastrar(origem, 100);

        var falha = segundaDepoisDaPrimeira(() -> inativarCamara.inativar(destino), () -> mover(lote, destino, 100));

        assertInstanceOf(EstadoIncompativel.class, falha);
        assertFalse(ativa(destino));
        assertEquals(origem, camaraDoLote(lote));
        assertEquals(100, ocupacao(origem));
        assertEquals(0, ocupacao(destino));
    }

    @Test
    void cadastrosSimultaneosDoMesmoCodigoGravamUmLoteEDesfazemOOutro() throws Exception {
        long primeira = novaCamara(1000);
        long segunda = novaCamara(1000);
        var codigo = codigoDeLote();

        // Câmaras diferentes não se bloqueiam: as duas passam pela consulta do código e quem decide é o índice único.
        var falha = segundaDepoisDaPrimeira(() -> cadastrar(codigo, primeira, 100), () -> cadastrar(codigo, segunda, 100));

        assertInstanceOf(CodigoDuplicado.class, falha);
        assertEquals(100, ocupacao(primeira));
        assertEquals(0, ocupacao(segunda));
        assertEquals(1L, QuarkusTransaction.requiringNew().call(() -> ((Number) entityManager
                .createNativeQuery("select count(*) from lotes where codigo = :codigo")
                .setParameter("codigo", codigo).getSingleResult()).longValue()));
    }

    /**
     * Executa as tarefas ao mesmo tempo, cada uma na transação do próprio caso de uso, e devolve a exceção de cada uma
     * na ordem recebida ({@code null} quando a tarefa terminou sem erro).
     */
    private static List<Throwable> simultaneamente(List<Runnable> tarefas) throws Exception {
        var prontas = new CountDownLatch(tarefas.size());
        var largada = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(tarefas.size())) {
            var futuros = new ArrayList<Future<?>>();
            for (Runnable tarefa : tarefas) {
                futuros.add(executor.submit(() -> {
                    prontas.countDown();
                    aguardar(largada);
                    tarefa.run();
                }));
            }
            assertTrue(prontas.await(10, TimeUnit.SECONDS));
            largada.countDown();
            var falhas = new ArrayList<Throwable>();
            for (Future<?> futuro : futuros) {
                falhas.add(falhaDe(futuro));
            }
            return falhas;
        }
    }

    /**
     * Executa {@code primeira} numa transação retida, dispara {@code segunda} em paralelo e só confirma a primeira
     * depois que a segunda estiver esperando um bloqueio no banco. Devolve a exceção da segunda, ou {@code null}.
     */
    private Throwable segundaDepoisDaPrimeira(Runnable primeira, Runnable segunda) throws Exception {
        var executada = new CountDownLatch(1);
        var confirmar = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            try {
                var retida = executor.submit(() -> QuarkusTransaction.requiringNew().run(() -> {
                    primeira.run();
                    executada.countDown();
                    aguardar(confirmar);
                }));
                if (!executada.await(10, TimeUnit.SECONDS)) {
                    fail("a primeira operação não terminou", falhaDe(retida));
                }

                var concorrente = executor.submit(segunda);
                aguardarSessaoEsperandoBloqueio();
                assertFalse(concorrente.isDone(), "a segunda operação terminou sem esperar a primeira");

                confirmar.countDown();
                assertEquals(null, falhaDe(retida));
                return falhaDe(concorrente);
            } finally {
                confirmar.countDown();
            }
        }
    }

    private void aguardarSessaoEsperandoBloqueio() throws InterruptedException {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < limite) {
            long esperando = QuarkusTransaction.requiringNew().call(() -> ((Number) entityManager
                    .createNativeQuery("""
                            select count(*) from pg_stat_activity
                            where datname = current_database() and wait_event_type = 'Lock'
                            """)
                    .getSingleResult()).longValue());
            if (esperando > 0) {
                return;
            }
            Thread.sleep(20);
        }
        fail("a segunda operação não ficou esperando o bloqueio da primeira");
    }

    private static Throwable falhaDe(Future<?> futuro) throws InterruptedException {
        try {
            futuro.get(30, TimeUnit.SECONDS);
            return null;
        } catch (ExecutionException e) {
            return e.getCause();
        } catch (TimeoutException e) {
            return fail("a operação não terminou a tempo", e);
        }
    }

    private static void aguardar(CountDownLatch sinal) {
        try {
            if (!sinal.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("sinal não recebido a tempo");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private long novaCamara(int capacidade) {
        long id = QuarkusTransaction.requiringNew().call(() -> camaras.salvar(Camara.nova(
                "C-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT), "Câmara",
                "UBS concorrência", capacidade, new BigDecimal("2.0"), new BigDecimal("8.0"),
                EstadoCamara.OPERACIONAL, OffsetDateTime.now(FUSO))).getId());
        camarasDoTeste.add(id);
        return id;
    }

    private long cadastrar(long camaraId, int quantidade) {
        return cadastrar(codigoDeLote(), camaraId, quantidade);
    }

    private long cadastrar(String codigo, long camaraId, int quantidade) {
        return cadastrarLote.cadastrar(codigo, "Febre amarela", "Bio-Manguinhos", validade(), quantidade, camaraId)
                .getId();
    }

    private void mover(long loteId, long destino, int quantidade) {
        atualizarLote.atualizar(loteId, "Febre amarela", "Bio-Manguinhos", validade(), quantidade, destino);
    }

    private void redimensionar(long camaraId, int capacidade) {
        atualizarCamara.atualizar(camaraId, new AtualizarCamaraDTO("Câmara", "UBS concorrência", capacidade,
                new BigDecimal("2.0"), new BigDecimal("8.0"), EstadoCamara.OPERACIONAL));
    }

    private static String codigoDeLote() {
        return "L-" + UUID.randomUUID().toString().substring(0, 13).toUpperCase(Locale.ROOT);
    }

    private static LocalDate validade() {
        return LocalDate.now(FUSO).plusYears(1);
    }

    private long ocupacao(long camaraId) {
        return QuarkusTransaction.requiringNew().call(() -> lotes.ocupacaoDaCamara(camaraId));
    }

    private long lotesAtivos(long camaraId) {
        return QuarkusTransaction.requiringNew().call(() -> ((Number) entityManager
                .createNativeQuery("select count(*) from lotes where camara_id = :id and ativo")
                .setParameter("id", camaraId).getSingleResult()).longValue());
    }

    private int capacidade(long camaraId) {
        return QuarkusTransaction.requiringNew().call(() -> camaras.buscarPorId(camaraId).orElseThrow()
                .getCapacidade());
    }

    private boolean ativa(long camaraId) {
        return QuarkusTransaction.requiringNew().call(() -> camaras.buscarPorId(camaraId).orElseThrow().isAtivo());
    }

    private long camaraDoLote(long loteId) {
        return QuarkusTransaction.requiringNew().call(() -> lotes.buscarPorId(loteId).orElseThrow().getCamaraId());
    }
}

package br.ufrn.friovax.api.telemetria.api;

import br.ufrn.friovax.api.autenticacao.dominio.Perfil;
import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.CamaraRepository;
import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import br.ufrn.friovax.api.suporte.AutenticacaoHttpSupport;
import br.ufrn.friovax.api.suporte.CenarioDeOcorrencia;
import br.ufrn.friovax.api.suporte.UsuariosDeTeste;
import br.ufrn.friovax.api.telemetria.dominio.LeituraRepository;
import br.ufrn.friovax.api.telemetria.dominio.OcorrenciaRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Resolução de ocorrência de ponta a ponta no PostgreSQL (S2-12), com administradores cadastrados: autoria gravada,
 * atomicidade com a trilha, resoluções simultâneas e o lock compartilhado com a recepção de leituras. A ocorrência é
 * montada pelos repositórios, sem Go.
 */
@QuarkusTest
class ResolucaoDeOcorrenciaHttpPostgresTest {
    private static final String RESOLUCAO = "/api/ocorrencias/{id}/resolucao";

    @Inject CamaraRepository camaras;
    @Inject LeituraRepository leituras;
    @Inject OcorrenciaRepository ocorrencias;
    @Inject EntityManager entityManager;

    private UUID ana;
    private UUID bruno;
    private long camaraId;
    private CenarioDeOcorrencia cenario;
    private long ocorrenciaId;

    @BeforeEach
    void preparar() {
        ana = UsuariosDeTeste.cadastrar(entityManager, Perfil.ADMINISTRADOR);
        bruno = UsuariosDeTeste.cadastrar(entityManager, Perfil.ADMINISTRADOR);
        var codigo = "RO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        camaraId = QuarkusTransaction.requiringNew().call(() -> camaras.salvar(Camara.nova(codigo, "Câmara",
                "UBS resolução", 1000, new BigDecimal("2.0"), new BigDecimal("8.0"), EstadoCamara.OPERACIONAL,
                OffsetDateTime.now().minusDays(1))).getId());
        cenario = new CenarioDeOcorrencia(camaras, leituras, ocorrencias, camaraId,
                OffsetDateTime.now().minusHours(1).withNano(0));
        ocorrenciaId = QuarkusTransaction.requiringNew().call(cenario::abrirOcorrencia);
    }

    @AfterEach
    void apagar() {
        executar("drop trigger if exists falhar_trilha_resolucao_teste on eventos_auditoria");
        executar("drop function if exists falhar_trilha_resolucao_teste()");
        QuarkusTransaction.requiringNew().run(() -> {
            // Ocorrências antes da câmara (como na recepção); leituras e versões saem em cascata com ela. Os usuários
            // por último, porque ocorrências e versões os citam.
            entityManager.createNativeQuery("delete from eventos_auditoria where ator_id in (:ids)")
                    .setParameter("ids", List.of(ana, bruno)).executeUpdate();
            entityManager.createNativeQuery("delete from ocorrencias where camara_id = :id")
                    .setParameter("id", camaraId).executeUpdate();
            entityManager.createNativeQuery("delete from camaras where id = :id").setParameter("id", camaraId)
                    .executeUpdate();
        });
        UsuariosDeTeste.apagar(entityManager, ana);
        UsuariosDeTeste.apagar(entityManager, bruno);
    }

    @Test
    void gravaAutorJustificativaLeituraEstadoETrilhaNoBanco() {
        long segura = QuarkusTransaction.requiringNew().call(() -> cenario.segura().getId());

        resolver(ana, "  Porta reparada; lotes avaliados  ").then().statusCode(200)
                .body("resolvidaPor", equalTo(ana.toString()));

        var linha = linhaDaOcorrencia();
        assertEquals(ana.toString(), linha[0]);
        assertEquals("Porta reparada; lotes avaliados", linha[1]);
        assertEquals(segura, ((Number) linha[2]).longValue());
        assertTrue(linha[3] != null, "resolvida_em gravado");
        assertEquals("NORMAL", estadoTermico());
        assertEquals(List.of(ana.toString()), autoresDaResolucaoNaTrilha());
    }

    @Test
    void falhaAoGravarATrilhaDesfazAResolucaoEOEstado() {
        QuarkusTransaction.requiringNew().run(cenario::segura);
        executar("create or replace function falhar_trilha_resolucao_teste() returns trigger as $$ begin "
                + "if new.recurso = 'OCORRENCIA' and new.recurso_id = " + ocorrenciaId + " then "
                + "raise exception 'falha forçada na trilha'; end if; return new; end $$ language plpgsql");
        executar("create trigger falhar_trilha_resolucao_teste before insert on eventos_auditoria "
                + "for each row execute function falhar_trilha_resolucao_teste()");

        resolver(ana, "Porta reparada").then().statusCode(500);

        assertNull(linhaDaOcorrencia()[3], "a resolução foi desfeita junto");
        assertEquals("EM_ALERTA", estadoTermico());
        executar("drop trigger falhar_trilha_resolucao_teste on eventos_auditoria");

        // Sem a falha, o mesmo pedido resolve: nada ficou pela metade.
        resolver(ana, "Porta reparada").then().statusCode(200);
        assertEquals(List.of(ana.toString()), autoresDaResolucaoNaTrilha());
    }

    @Test
    void duasResolucoesSimultaneasComJustificativasDiferentesGravamSoUma() throws Exception {
        QuarkusTransaction.requiringNew().run(cenario::segura);

        var respostas = simultaneamente(() -> resolver(ana, "Porta reparada"),
                () -> resolver(bruno, "Compressor substituído"));

        var status = respostas.stream().map(Response::statusCode).sorted().toList();
        assertEquals(List.of(200, 409), status);
        var vencedora = respostas.stream().filter(r -> r.statusCode() == 200).findFirst().orElseThrow();
        var autor = vencedora.jsonPath().getString("resolvidaPor");
        assertEquals(autor, linhaDaOcorrencia()[0]);
        assertEquals(List.of(autor), autoresDaResolucaoNaTrilha());
        assertEquals("/problemas/resolucao-conflitante", respostas.stream().filter(r -> r.statusCode() == 409)
                .findFirst().orElseThrow().jsonPath().getString("type"));
    }

    @Test
    void reenviosIdenticosSimultaneosDevolvemOMesmoTratamentoComUmEvento() throws Exception {
        QuarkusTransaction.requiringNew().run(cenario::segura);

        var respostas = simultaneamente(() -> resolver(ana, "Porta reparada"),
                () -> resolver(ana, "Porta reparada"));

        assertTrue(respostas.stream().allMatch(r -> r.statusCode() == 200), () -> respostas.stream()
                .map(Response::asString).toList().toString());
        assertEquals(1, respostas.stream().map(r -> r.jsonPath().getString("resolvidaEm")).distinct().count());
        assertEquals(List.of(ana.toString()), autoresDaResolucaoNaTrilha());
    }

    @Test
    void resolucaoEsperaOLockDaCamaraEVeAInseguraConfirmadaAntesDela() throws Exception {
        QuarkusTransaction.requiringNew().run(cenario::segura);
        var bloqueada = new CountDownLatch(1);
        Future<?> leitura;
        try (var executor = Executors.newSingleThreadExecutor()) {
            // Simula a confirmação de uma leitura insegura: segura o lock da câmara enquanto grava a leitura.
            leitura = executor.submit(() -> QuarkusTransaction.requiringNew().run(() -> {
                camaras.buscarPorIdParaAlteracao(camaraId).orElseThrow();
                cenario.insegura(1, false);
                bloqueada.countDown();
                dormir(1500);
            }));
            assertTrue(bloqueada.await(10, TimeUnit.SECONDS));

            // Sem esperar o lock, a resolução veria a segura como última e resolveria.
            resolver(ana, "Porta reparada").then().statusCode(409)
                    .body("type", equalTo("/problemas/resolucao-nao-permitida"));
            leitura.get(10, TimeUnit.SECONDS);
        }

        assertNull(linhaDaOcorrencia()[3]);
        assertEquals("EM_ALERTA", estadoTermico());
        assertTrue(autoresDaResolucaoNaTrilha().isEmpty());
    }

    @Test
    void novaExcursaoAbreOutraOcorrenciaSemAlterarAResolvida() {
        QuarkusTransaction.requiringNew().run(cenario::segura);
        resolver(ana, "Porta reparada").then().statusCode(200);
        var antes = linhaDaOcorrencia();

        long nova = QuarkusTransaction.requiringNew().call(cenario::abrirOcorrencia);

        assertTrue(nova != ocorrenciaId);
        var depois = linhaDaOcorrencia();
        for (int i = 0; i < antes.length; i++) {
            assertEquals(antes[i], depois[i], "coluna " + i + " da resolvida mudou");
        }
        assertEquals(2L, ((Number) QuarkusTransaction.requiringNew().call(() -> entityManager
                .createNativeQuery("select count(*) from ocorrencias where camara_id = :id")
                .setParameter("id", camaraId).getSingleResult())).longValue());
    }

    private Response resolver(UUID admin, String justificativa) {
        return AutenticacaoHttpSupport.como(admin, UsuariosDeTeste.login(admin), Perfil.ADMINISTRADOR)
                .contentType(ContentType.JSON).body(Map.of("justificativa", justificativa))
                .post(RESOLUCAO, ocorrenciaId);
    }

    /** resolvida_por (texto), justificativa, leitura_resolucao_id, resolvida_em. */
    private Object[] linhaDaOcorrencia() {
        return QuarkusTransaction.requiringNew().call(() -> (Object[]) entityManager.createNativeQuery(
                        "select cast(resolvida_por as text), justificativa, leitura_resolucao_id, resolvida_em "
                                + "from ocorrencias where id = :id")
                .setParameter("id", ocorrenciaId).getSingleResult());
    }

    private String estadoTermico() {
        return QuarkusTransaction.requiringNew().call(() -> (String) entityManager
                .createNativeQuery("select estado_termico from camaras where id = :id")
                .setParameter("id", camaraId).getSingleResult());
    }

    @SuppressWarnings("unchecked")
    private List<String> autoresDaResolucaoNaTrilha() {
        return QuarkusTransaction.requiringNew().call(() -> (List<String>) entityManager.createNativeQuery(
                        "select cast(ator_id as text) from eventos_auditoria where recurso = 'OCORRENCIA' "
                                + "and operacao = 'RESOLUCAO_DE_OCORRENCIA' and recurso_id = :id order by id")
                .setParameter("id", ocorrenciaId).getResultList());
    }

    private void executar(String sql) {
        QuarkusTransaction.requiringNew().run(() -> entityManager.createNativeQuery(sql).executeUpdate());
    }

    @SafeVarargs
    private static List<Response> simultaneamente(Callable<Response>... tarefas) throws Exception {
        var largada = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(tarefas.length)) {
            var futuros = new ArrayList<Future<Response>>();
            for (var tarefa : tarefas) {
                futuros.add(executor.submit(() -> {
                    largada.await(10, TimeUnit.SECONDS);
                    return tarefa.call();
                }));
            }
            largada.countDown();
            var respostas = new ArrayList<Response>();
            for (var futuro : futuros) {
                respostas.add(futuro.get(30, TimeUnit.SECONDS));
            }
            return respostas;
        }
    }

    private static void dormir(long milissegundos) {
        try {
            Thread.sleep(milissegundos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}

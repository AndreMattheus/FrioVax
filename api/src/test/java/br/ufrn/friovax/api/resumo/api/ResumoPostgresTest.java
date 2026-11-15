package br.ufrn.friovax.api.resumo.api;

import br.ufrn.friovax.api.camara.aplicacao.*;
import br.ufrn.friovax.api.camara.dominio.*;
import br.ufrn.friovax.api.compartilhado.aplicacao.MudancaConfirmada;
import br.ufrn.friovax.api.lote.aplicacao.*;
import br.ufrn.friovax.api.lote.dominio.*;
import br.ufrn.friovax.api.resumo.aplicacao.ConsultarResumo;
import br.ufrn.friovax.api.resumo.dominio.TotaisResumo;
import br.ufrn.friovax.api.telemetria.dominio.*;
import br.ufrn.friovax.api.telemetria.persistencia.*;
import br.ufrn.friovax.api.autenticacao.dominio.Perfil;
import br.ufrn.friovax.api.suporte.AutenticacaoHttpSupport;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class ResumoPostgresTest {
    @Inject ConsultarResumo resumo;
    @Inject CamaraService cadastrarCamara;
    @Inject CadastrarLote cadastrarLote;
    @Inject RegistrarBaixaLote baixar;
    @Inject DescartarLote descartar;
    @Inject InativarLote inativar;
    @Inject AtualizarCamara atualizarCamara;
    @Inject InativarCamara inativarCamara;
    @Inject OcorrenciaRepositoryPostgres ocorrencias;
    @Inject LeituraRepositoryPostgres leituras;
    @Inject EntityManager em;
    @Inject MeterRegistry metricas;
    private final List<Long> camaras = new ArrayList<>();
    private final OffsetDateTime agora = OffsetDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);

    @BeforeEach
    void preparar() {
        resumo.invalidar(new MudancaConfirmada("camara", 1, "Limpeza de teste", null, agora));
    }

    @AfterEach
    void limpar() {
        QuarkusTransaction.requiringNew().run(() -> {
            for (var id : camaras) em.createNativeQuery("delete from ocorrencias where camara_id = :id")
                    .setParameter("id", id).executeUpdate();
            for (var id : camaras) em.createNativeQuery("delete from lotes where camara_id = :id")
                    .setParameter("id", id).executeUpdate();
            for (var id : camaras) em.createNativeQuery("delete from camaras where id = :id")
                    .setParameter("id", id).executeUpdate();
        });
        preparar();
    }

    @Test
    void totaisConferemComBancoSemDuplicarLoteEntreEpisodios() {
        long c1 = camara(), c2 = camara();
        var lote = lote(c1, 100);
        var descartado = lote(c1, 20);
        descartar.descartar(descartado.getId());
        var inativo = lote(c1, 30);
        inativar.inativar(inativo.getId());
        var anterior = resumo.consultar();
        abrir(c1, lote);
        abrir(c2, lote); // O mesmo lote pode integrar fotografias de episódios distintos.
        var atual = resumo.consultar();
        assertNotSame(anterior, atual);
        assertEquals(2, atual.totais().episodiosAbertos());
        assertEquals(1, atual.totais().lotesDistintosAfetados());
        assertEquals(new TotaisResumo.Estoque(1, 100), atual.totais().lotesPorEstado().get("DISPONIVEL"));
        assertEquals(new TotaisResumo.Estoque(1, 20), atual.totais().lotesPorEstado().get("DESCARTADO"));
        assertEquals(2L, atual.totais().camarasPorEstadoOperacional().get("OPERACIONAL"));
        assertEquals(2L, atual.totais().camarasPorEstadoTermico().get("SEM_LEITURAS"));
        assertEquals(0L, atual.totais().camarasPorEstadoTermico().get("EM_ALERTA"));
        QuarkusTransaction.requiringNew().run(() -> {
            long doses = ((Number) em.createNativeQuery("select sum(quantidade) from lotes where ativo")
                    .getSingleResult()).longValue();
            assertEquals(doses, atual.totais().lotesPorEstado().values().stream()
                    .mapToLong(TotaisResumo.Estoque::doses).sum());
        });
    }

    @Test
    void commitInvalidaERollbackPreservaResultadoSemExporDadosPendentes() {
        long id = camara();
        var antes = resumo.consultar();
        assertThrows(IllegalStateException.class, () -> QuarkusTransaction.requiringNew().run(() -> {
            lote(id, 40);
            assertSame(antes, resumo.consultar());
            throw new IllegalStateException("Rollback intencional");
        }));
        assertSame(antes, resumo.consultar());
        var salvo = lote(id, 40);
        var depois = resumo.consultar();
        assertNotSame(antes, depois);
        assertEquals(new TotaisResumo.Estoque(1, 40), depois.totais().lotesPorEstado().get("DISPONIVEL"));
        baixar.registrar(salvo.getId(), 10, MotivoBaixa.ADMINISTRADA);
        assertEquals(30, resumo.consultar().totais().lotesPorEstado().get("DISPONIVEL").doses());
    }

    @Test
    void separaEstadoOperacionalDoTermicoEExcluiCamaraInativa() {
        long id = camara();
        var antes = resumo.consultar();
        atualizarCamara.atualizar(id, new AtualizarCamaraDTO("Resumo", "UBS", 1000,
                new BigDecimal("2.0"), new BigDecimal("8.0"), EstadoCamara.MANUTENCAO));
        var depois = resumo.consultar();
        assertNotSame(antes, depois);
        assertEquals(1L, depois.totais().camarasPorEstadoOperacional().get("MANUTENCAO"));
        assertEquals(0L, depois.totais().camarasPorEstadoOperacional().get("OPERACIONAL"));
        assertEquals(1L, depois.totais().camarasPorEstadoTermico().get("SEM_LEITURAS"));
        inativarCamara.inativar(id);
        assertEquals(0, resumo.consultar().totais().camarasPorEstadoOperacional().values().stream()
                .mapToLong(Long::longValue).sum());
    }

    @Test
    void httpCompartilhaCacheEntrePerfisSemGoEProtegeMetricas() {
        camara();
        double misses = contador("misses"), hits = contador("hits");
        given().get("/api/resumo").then().statusCode(401);
        given().auth().oauth2(AutenticacaoHttpSupport.token(Set.of("DESCONHECIDO")))
                .get("/api/resumo").then().statusCode(403);
        assertEquals(misses, contador("misses"));
        for (var perfil : Perfil.values()) {
            AutenticacaoHttpSupport.autenticado(perfil).get("/api/resumo").then().statusCode(200)
                    .body("geradoEm", notNullValue()).body("validade", notNullValue())
                    .body("totais.camarasPorEstadoTermico.SEM_LEITURAS", equalTo(1));
        }
        assertEquals(misses + 1, contador("misses"));
        assertEquals(hits + 2, contador("hits"));
        given().get("/q/metrics").then().statusCode(401);
        AutenticacaoHttpSupport.autenticado(Perfil.OPERADOR).get("/q/metrics").then().statusCode(403);
        for (var perfil : List.of(Perfil.ADMINISTRADOR, Perfil.AUDITOR)) {
            AutenticacaoHttpSupport.autenticado(perfil).accept("text/plain").get("/q/metrics")
                    .then().statusCode(200).body(containsString("friovax_resumo_cache_hits_total"))
                    .body(containsString("friovax_resumo_cache_misses_total " + (misses + 1)))
                    .body(containsString("friovax_resumo_cache_hits_total " + (hits + 2)));
        }
    }

    private double contador(String tipo) {
        return metricas.get("friovax_resumo_cache_" + tipo + "_total").counter().count();
    }

    private long camara() {
        long id = cadastrarCamara.cadastrar(new CadastrarCamaraDTO(codigo(), "Resumo", "UBS", 1000,
                new BigDecimal("2.0"), new BigDecimal("8.0"), EstadoCamara.OPERACIONAL)).id();
        camaras.add(id);
        return id;
    }

    private Lote lote(long camara, int doses) {
        return cadastrarLote.cadastrar(codigo(), "Vacina", "Fabricante", LocalDate.now().plusYears(1), doses, camara);
    }

    private void abrir(long camara, Lote lote) {
        QuarkusTransaction.requiringNew().run(() -> {
            var leitura = leituras.salvar(Leitura.nova(camara, codigo(), agora, new BigDecimal("9.0"),
                    "sensor", 1, ResultadoTermico.insegura(3, true), agora));
            ocorrencias.salvar(Ocorrencia.abrir(camara, 1, leitura.getId(), List.of(leitura.getId()),
                    List.of(new LoteAfetado(lote.getId(), lote.getCodigo(), lote.getImunobiologico(),
                            lote.getQuantidade())), false, agora));
        });
    }

    private String codigo() { return "R-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(); }
}

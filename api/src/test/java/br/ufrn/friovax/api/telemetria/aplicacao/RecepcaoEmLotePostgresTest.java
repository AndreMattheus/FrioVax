package br.ufrn.friovax.api.telemetria.aplicacao;

import br.ufrn.friovax.api.autenticacao.dominio.Perfil;
import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.CamaraRepository;
import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import br.ufrn.friovax.api.compartilhado.dominio.Paginacao;
import br.ufrn.friovax.api.lote.dominio.Lote;
import br.ufrn.friovax.api.lote.dominio.LoteRepository;
import br.ufrn.friovax.api.telemetria.dominio.LeituraRepository;
import br.ufrn.friovax.api.telemetria.dominio.LoteAfetado;
import br.ufrn.friovax.api.telemetria.dominio.NovaLeitura;
import br.ufrn.friovax.api.telemetria.dominio.OcorrenciaRepository;
import br.ufrn.friovax.api.telemetria.dominio.ResultadoTermico;
import br.ufrn.friovax.api.telemetria.integracao.ServidorTelemetriaDeTeste;
import br.ufrn.friovax.api.telemetria.integracao.ServidorTelemetriaResource;
import br.ufrn.friovax.api.telemetria.integracao.TelemetriaGrpcPostgresProfile;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static br.ufrn.friovax.api.suporte.AutenticacaoHttpSupport.autenticado;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(TelemetriaGrpcPostgresProfile.class)
class RecepcaoEmLotePostgresTest {

    private static final String RESTRICAO_DE_TESTE = "chk_leitura_teste_rollback_do_grupo";

    @Inject CamaraRepository camaras;
    @Inject LeituraRepository leituras;
    @Inject OcorrenciaRepository ocorrencias;
    @Inject LoteRepository lotes;
    @Inject EntityManager entityManager;

    private final List<Long> camarasCriadas = new ArrayList<>();
    private ServidorTelemetriaDeTeste go;
    private OffsetDateTime inicio;
    private String correlacao;

    @BeforeEach
    void preparar() {
        go = ServidorTelemetriaResource.servidor;
        go.reiniciarRegistros();
        inicio = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1).withNano(0);
        correlacao = "lote-" + UUID.randomUUID();
    }

    @AfterEach
    void limpar() {
        go.reiniciarRegistros();
        if (camarasCriadas.isEmpty()) {
            return;
        }
        QuarkusTransaction.requiringNew().run(() -> {
            entityManager.createNativeQuery("delete from eventos_auditoria where correlacao_id = :correlacao")
                    .setParameter("correlacao", correlacao).executeUpdate();
            entityManager.createNativeQuery("delete from ocorrencias where camara_id in (:ids)")
                    .setParameter("ids", camarasCriadas).executeUpdate();
            entityManager.createNativeQuery("delete from lotes where camara_id in (:ids)")
                    .setParameter("ids", camarasCriadas).executeUpdate();
            entityManager.createNativeQuery("delete from camaras where id in (:ids)")
                    .setParameter("ids", camarasCriadas).executeUpdate();
        });
    }

    @Test
    void falhaAposAberturaReverteCamaraInteiraEPreservaOutraCamaraEReenvios() {
        long valida = novaCamara();
        long comFalha = novaCamara();
        var lote = novoLote(comFalha);
        var a = leitura(comFalha, "a", 0, 9);
        var primeiroId = enviar(List.of(a)).statusCode(200).extract().jsonPath().getLong("[0].id");
        var entradas = List.of(leitura(valida, "outra", 0, 4), a, leitura(comFalha, "b", 1, 9),
                leitura(comFalha, "c", 2, 9), leitura(comFalha, "d", 3, 4));

        sql("""
                alter table leituras add constraint %s check (camara_id <> %d or id_externo <> 'd') not valid
                """.formatted(RESTRICAO_DE_TESTE, comFalha));
        long idValido;
        try {
            var parcial = enviar(entradas).statusCode(200)
                    .body("codigo", contains(201, 200, 500, 500, 500),
                            "[2].motivo", equalTo("FALHA_TRANSACIONAL"))
                    .extract().jsonPath();
            assertEquals(primeiroId, parcial.getLong("[1].id"));
            idValido = parcial.getLong("[0].id");
            assertEquals(2, go.chamadas.get());
            assertEquals(1, total(valida));
            assertEquals(1, total(comFalha));
            conferirEstadoTermico(comFalha, "ATENCAO");
            assertTrue(ocorrencias.abertaDaCamara(comFalha).isEmpty());
            assertEquals(0, aberturasAuditadas());
            assertEquals(0, quantidadeDeVinculos(comFalha));
        } finally {
            sql("alter table leituras drop constraint " + RESTRICAO_DE_TESTE);
        }

        var retry = enviar(entradas).statusCode(200)
                .body("codigo", contains(200, 200, 201, 201, 201),
                        "[3].sequencia", equalTo(3), "[4].sequencia", equalTo(0))
                .extract().jsonPath();
        assertEquals(idValido, retry.getLong("[0].id"));
        assertEquals(primeiroId, retry.getLong("[1].id"));
        var aberta = ocorrencias.abertaDaCamara(comFalha).orElseThrow();
        assertEquals(List.of(primeiroId, retry.getLong("[2].id"), retry.getLong("[3].id")), aberta.getEvidencias());
        assertEquals(retry.getLong("[3].ocorrenciaId"), aberta.getId());
        assertEquals(List.of(new LoteAfetado(lote.getId(), lote.getCodigo(), lote.getImunobiologico(), 25)),
                aberta.getLotesAfetados());
        conferirEstadoTermico(comFalha, "EM_ALERTA");
        assertEquals(3, go.chamadas.get());
        assertEquals(1, go.ultimaRequisicao().getWindowsCount());
        assertEquals(4, total(comFalha));
        assertEquals(1, aberturasAuditadas());

        var repetidas = enviar(entradas).statusCode(200).body("codigo", contains(200, 200, 200, 200, 200))
                .extract().jsonPath();
        assertEquals(aberta.getId(), repetidas.getLong("[3].ocorrenciaId"));
        assertEquals(3, go.chamadas.get());
        assertEquals(1, aberturasAuditadas());
    }

    @Test
    void alertaAbertoPeloLoteBloqueiaEntradaAumentoEAdministracaoDeDoses() {
        long emAlerta = novaCamara();
        long normal = novaCamara();
        var alocado = novoLote(emAlerta);
        var paraRetirada = novoLote(emAlerta);
        var fora = novoLote(normal);

        enviar(List.of(leitura(emAlerta, "a", 0, 9), leitura(emAlerta, "b", 1, 9),
                leitura(emAlerta, "c", 2, 9), leitura(emAlerta, "d", 3, 4)))
                .statusCode(200).body("codigo", contains(201, 201, 201, 201),
                        "[2].ocorrenciaId", notNullValue(), "[3].classificacao", equalTo("SEGURA"));
        conferirEstadoTermico(emAlerta, "EM_ALERTA");
        var aberta = ocorrencias.abertaDaCamara(emAlerta).orElseThrow();
        assertEquals(2, aberta.getLotesAfetados().size());

        var novo = new HashMap<>(edicaoLote(emAlerta, 10));
        var codigoNovo = codigo();
        novo.put("codigo", codigoNovo);
        conferirBloqueio(autenticado(Perfil.ADMINISTRADOR).contentType(ContentType.JSON)
                .header("X-Correlation-Id", correlacao).body(novo).post("/api/lotes").then());
        conferirBloqueio(editarLote(alocado.getId(), emAlerta, 30));
        conferirBloqueio(editarLote(fora.getId(), emAlerta, 25));
        conferirBloqueio(baixar(alocado.getId(), "ADMINISTRADA"));

        assertFalse(lotes.existePorCodigo(codigoNovo));
        assertEquals(25, lotes.buscarPorId(alocado.getId()).orElseThrow().getQuantidade());
        assertEquals(normal, lotes.buscarPorId(fora.getId()).orElseThrow().getCamaraId());

        baixar(alocado.getId(), "PERDA").statusCode(200).body("quantidade", equalTo(20));
        autenticado(Perfil.ADMINISTRADOR).header("X-Correlation-Id", correlacao)
                .post("/api/lotes/{id}/descarte", alocado.getId()).then().statusCode(200)
                .body("estado", equalTo("DESCARTADO"));
        editarLote(paraRetirada.getId(), normal, 25).statusCode(200).body("camaraId", equalTo((int) normal));
        assertEquals(aberta.getLotesAfetados(), ocorrencias.abertaDaCamara(emAlerta).orElseThrow().getLotesAfetados());
    }

    @Test
    void alteracoesDuranteARpcRejeitamSomenteCamarasAlteradasSemOutraAnalise() {
        long politica = novaCamara();
        long inativada = novaCamara();
        long mesmaChave = novaCamara();
        long outraChave = novaCamara();
        long intacta = novaCamara();
        go.comportarSe((requisicao, resposta) -> {
            QuarkusTransaction.requiringNew().run(() -> {
                var camara = camaras.buscarPorIdParaAlteracao(politica).orElseThrow();
                camara.alterarPolitica(new BigDecimal("2.0"), new BigDecimal("10.0"), 3, null, inicio);
                camaras.salvar(camara);
                var desativada = camaras.buscarPorIdParaAlteracao(inativada).orElseThrow();
                desativada.inativar(false, inicio);
                camaras.salvar(desativada);
                confirmarEmParalelo(mesmaChave, "a", inicio);
                confirmarEmParalelo(outraChave, "outra", inicio.minusMinutes(1));
            });
            resposta.onNext(ServidorTelemetriaDeTeste.analisar(requisicao));
            resposta.onCompleted();
        });
        var entradas = List.of(leitura(politica, "a", 0, 9), leitura(inativada, "a", 0, 4),
                leitura(mesmaChave, "a", 0, 4), leitura(intacta, "a", 0, 4), leitura(mesmaChave, "b", 1, 4),
                leitura(outraChave, "a", 0, 4));
        enviar(entradas).statusCode(200).body("codigo", contains(409, 409, 409, 201, 409, 409),
                "[0].motivo", equalTo("SNAPSHOT_ALTERADO"), "[1].motivo", equalTo("SNAPSHOT_ALTERADO"),
                "[2].motivo", equalTo("SNAPSHOT_ALTERADO"), "[4].motivo", equalTo("SNAPSHOT_ALTERADO"),
                "[5].motivo", equalTo("SNAPSHOT_ALTERADO"));
        assertEquals(1, go.chamadas.get());
        assertEquals(0, total(politica));
        assertEquals(0, total(inativada));
        assertEquals(1, total(mesmaChave));
        assertEquals(1, total(outraChave));
        assertEquals(1, total(intacta));
        assertTrue(leituras.buscarPorChave(mesmaChave, "b").isEmpty());

        go.comportarSe(ServidorTelemetriaDeTeste.analisando(resposta -> resposta));
        enviar(entradas).statusCode(200).body("codigo", contains(201, 409, 200, 200, 201, 201),
                "[0].classificacao", equalTo("SEGURA"), "[1].motivo", equalTo("CAMARA_INAPTA"));
        assertEquals(2, go.chamadas.get());
    }

    private void confirmarEmParalelo(long camaraId, String idExterno, OffsetDateTime medidaEm) {
        camaras.buscarPorIdParaAlteracao(camaraId).orElseThrow();
        var disputada = new NovaLeitura(camaraId, idExterno, medidaEm, new BigDecimal("4"), "sensor");
        leituras.salvar(disputada.aceitar(1, ResultadoTermico.segura(), inicio.plusMinutes(10)));
    }

    private long novaCamara() {
        long id = QuarkusTransaction.requiringNew().call(() -> camaras.salvar(Camara.nova(codigo(), "Câmara de teste",
                "UBS", 100, new BigDecimal("2.0"), new BigDecimal("8.0"), EstadoCamara.OPERACIONAL, inicio)).getId());
        camarasCriadas.add(id);
        return id;
    }

    private Lote novoLote(long camaraId) {
        return QuarkusTransaction.requiringNew().call(() -> lotes.salvar(Lote.novo(codigo(), "Vacina de teste",
                "Laboratório", inicio.toLocalDate().plusDays(30), 25, camaraId, inicio.toLocalDate(), inicio)));
    }

    private Map<String, Object> edicaoLote(long camaraId, int quantidade) {
        return Map.of("imunobiologico", "Vacina de teste", "fabricante", "Laboratório",
                "validade", inicio.toLocalDate().plusDays(30).toString(), "camaraId", camaraId, "quantidade", quantidade);
    }

    private ValidatableResponse editarLote(long loteId, long camaraId, int quantidade) {
        return autenticado(Perfil.ADMINISTRADOR).contentType(ContentType.JSON).header("X-Correlation-Id", correlacao)
                .body(edicaoLote(camaraId, quantidade)).put("/api/lotes/{id}", loteId).then();
    }

    private ValidatableResponse baixar(long loteId, String motivo) {
        return autenticado(Perfil.OPERADOR).contentType(ContentType.JSON).header("X-Correlation-Id", correlacao)
                .body(Map.of("quantidade", 5, "motivo", motivo)).post("/api/lotes/{id}/baixas", loteId).then();
    }

    private static void conferirBloqueio(ValidatableResponse resposta) {
        resposta.statusCode(409).contentType("application/problem+json")
                .body("type", equalTo("/problemas/estado-incompativel"));
    }

    private static String codigo() {
        return "TL-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private Map<String, Object> leitura(long camaraId, String idExterno, int minuto, int temperatura) {
        return Map.of("camaraId", camaraId, "idExterno", idExterno, "temperatura", temperatura,
                "medidaEm", inicio.plusMinutes(minuto).toString(), "origem", "sensor");
    }

    private ValidatableResponse enviar(List<Map<String, Object>> entradas) {
        return autenticado(Perfil.OPERADOR).contentType(ContentType.JSON).header("X-Correlation-Id", correlacao)
                .body(entradas).post("/api/leituras/lote").then();
    }

    private long total(long camaraId) {
        return leituras.listarDaCamara(camaraId, Paginacao.padrao()).totalElementos();
    }

    private void sql(String comando) {
        QuarkusTransaction.requiringNew().run(() -> entityManager.createNativeQuery(comando).executeUpdate());
    }

    private static void conferirEstadoTermico(long camaraId, String estado) {
        autenticado(Perfil.AUDITOR).get("/api/camaras/{id}", camaraId).then().statusCode(200)
                .body("estadoTermico", equalTo(estado));
    }

    private long aberturasAuditadas() {
        return ((Number) entityManager.createNativeQuery("""
                select count(*) from eventos_auditoria where correlacao_id = :correlacao
                    and recurso = 'OCORRENCIA' and operacao = 'ABERTURA_DE_OCORRENCIA'
                """).setParameter("correlacao", correlacao).getSingleResult()).longValue();
    }

    private long quantidadeDeVinculos(long camaraId) {
        return ((Number) entityManager.createNativeQuery("""
                select count(*) from ocorrencia_lotes v join lotes l on l.id = v.lote_id where l.camara_id = :id
                """).setParameter("id", camaraId).getSingleResult()).longValue();
    }
}

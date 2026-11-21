package br.ufrn.friovax.api.auditoria.api;

import br.ufrn.friovax.api.autenticacao.dominio.Perfil;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.path.json.JsonPath;
import io.restassured.specification.RequestSpecification;
import io.smallrye.jwt.build.Jwt;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static br.ufrn.friovax.api.suporte.AutenticacaoHttpSupport.autenticado;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Trilha de auditoria (US10) de ponta a ponta, com PostgreSQL: requisição real com JWT, gravação na mesma transação
 * da alteração e consulta por {@code GET /api/auditoria}. Cada teste usa usuários próprios (UUID aleatório) e filtra
 * a trilha por eles, então não depende do que outros testes gravaram.
 */
@QuarkusTest
class AuditoriaHttpPostgresTest {
    private static final String TRILHA = "/api/auditoria";

    @Inject EntityManager entityManager;

    private final UUID adminId = UUID.randomUUID();
    private final UUID operadorId = UUID.randomUUID();
    private final List<Long> camarasCriadas = new ArrayList<>();

    @AfterEach
    void apagar() {
        QuarkusTransaction.requiringNew().run(() -> {
            for (long camaraId : camarasCriadas) {
                entityManager.createNativeQuery("delete from lotes where camara_id = :id")
                        .setParameter("id", camaraId).executeUpdate();
                entityManager.createNativeQuery("delete from camaras where id = :id")
                        .setParameter("id", camaraId).executeUpdate();
            }
            entityManager.createNativeQuery("delete from eventos_auditoria where ator_id in (:ids)")
                    .setParameter("ids", List.of(adminId, operadorId)).executeUpdate();
        });
    }

    @Test
    void cicloDoLoteFicaNaTrilhaEmOrdemComAtorInstanteECorrelacao() {
        var antes = OffsetDateTime.now();
        long camaraId = criarCamara(1000);
        String codigoLote = codigo("L");
        long loteId = admin().header("X-Correlation-Id", "lote-cadastro-1")
                .body(lote(codigoLote, 10, camaraId)).post("/api/lotes")
                .then().statusCode(201).header("X-Correlation-Id", "lote-cadastro-1")
                .extract().jsonPath().getLong("id");

        var edicao = Map.of("imunobiologico", "Vacina A", "fabricante", "Fabricante", "validade", "2099-12-31",
                "quantidade", 15, "camaraId", camaraId);
        admin().body(edicao).put("/api/lotes/{id}", loteId).then().statusCode(200);
        admin().body(edicao).put("/api/lotes/{id}", loteId).then().statusCode(200); // sem mudança: sem evento
        como(operadorId, "operador-trilha", Perfil.OPERADOR).header("X-Correlation-Id", "baixa-1")
                .body(Map.of("quantidade", 5, "motivo", "ADMINISTRADA")).post("/api/lotes/{id}/baixas", loteId)
                .then().statusCode(200);
        admin().post("/api/lotes/{id}/descarte", loteId).then().statusCode(200);
        admin().post("/api/lotes/{id}/descarte", loteId).then().statusCode(200); // repetido: sem evento
        admin().delete("/api/lotes/{id}", loteId).then().statusCode(204);
        admin().delete("/api/lotes/{id}", loteId).then().statusCode(204); // já inativo: sem evento
        var depois = OffsetDateTime.now();

        JsonPath trilha = autenticado(Perfil.AUDITOR).queryParam("recurso", "LOTE").queryParam("recursoId", loteId)
                .get(TRILHA).then().statusCode(200)
                .body("items.operacao", contains("CADASTRO", "ATUALIZACAO", "BAIXA", "DESCARTE", "INATIVACAO"),
                        "items.ator.perfil", contains("ADMINISTRADOR", "ADMINISTRADOR", "OPERADOR",
                                "ADMINISTRADOR", "ADMINISTRADOR"),
                        "items[0].correlacaoId", equalTo("lote-cadastro-1"),
                        "items[1].descricao", equalTo("Lote " + codigoLote + " atualizado: quantidade 10 → 15"),
                        "items[2].ator.id", equalTo(operadorId.toString()),
                        "items[2].ator.login", equalTo("operador-trilha"),
                        "items[2].correlacaoId", equalTo("baixa-1"))
                .extract().jsonPath();

        List<Long> ids = trilha.getList("items.id", Long.class);
        for (int i = 1; i < ids.size(); i++) {
            assertTrue(ids.get(i) > ids.get(i - 1), "ids crescentes na ordem das alterações: " + ids);
        }
        for (String instante : trilha.getList("items.instante", String.class)) {
            var quando = OffsetDateTime.parse(instante);
            assertFalse(quando.isBefore(antes.minusSeconds(1)) || quando.isAfter(depois.plusSeconds(1)), instante);
        }
    }

    @Test
    void alteracaoRecusadaNaoDeixaEvento() {
        long camaraId = criarCamara(100);
        var outroAdmin = UUID.randomUUID();
        try {
            var recusado = como(outroAdmin, "admin-recusado", Perfil.ADMINISTRADOR);
            recusado.body(lote(codigo("L"), 500, camaraId)).post("/api/lotes").then().statusCode(409);
            como(outroAdmin, "admin-recusado", Perfil.ADMINISTRADOR)
                    .body(Map.of("nome", "", "unidade", "UBS", "capacidade", 100, "temperaturaMinima", 2,
                            "temperaturaMaxima", 8, "estado", "OPERACIONAL"))
                    .put("/api/camaras/{id}", camaraId).then().statusCode(422);
            como(outroAdmin, "admin-recusado", Perfil.ADMINISTRADOR).delete("/api/camaras/{id}", 999_999_999L)
                    .then().statusCode(404);

            autenticado(Perfil.AUDITOR).queryParam("usuarioId", outroAdmin.toString()).get(TRILHA).then()
                    .statusCode(200).body("totalElements", equalTo(0));
            autenticado(Perfil.AUDITOR).queryParam("recurso", "CAMARA").queryParam("recursoId", camaraId)
                    .get(TRILHA).then().body("items.operacao", contains("CADASTRO"));
        } finally {
            QuarkusTransaction.requiringNew().run(() -> entityManager
                    .createNativeQuery("delete from eventos_auditoria where ator_id = :id")
                    .setParameter("id", outroAdmin).executeUpdate());
        }
    }

    @Test
    void tokenNaoVaiParaATrilha() {
        var agora = Instant.now();
        String token = Jwt.issuer("friovax-api").subject(adminId.toString()).upn("admin-token")
                .groups(Set.of("ADMINISTRADOR")).issuedAt(agora).expiresAt(agora.plusSeconds(900)).sign();
        var corpo = Map.of("codigo", codigo("T"), "nome", "Câmara", "unidade", "UBS teste", "capacidade", 100,
                "temperaturaMinima", 2, "temperaturaMaxima", 8, "estado", "OPERACIONAL");
        camarasCriadas.add(RestAssured.given().contentType("application/json").auth().oauth2(token)
                .body(corpo).post("/api/camaras").then().statusCode(201).extract().jsonPath().getLong("id"));

        List<?> linhas = QuarkusTransaction.requiringNew().call(() -> entityManager.createNativeQuery(
                        "select concat_ws('|', ator_login, ator_perfil, recurso, operacao, descricao, correlacao_id) "
                                + "from eventos_auditoria where ator_id = :id")
                .setParameter("id", adminId).getResultList());
        assertEquals(1, linhas.size());
        String gravado = linhas.get(0).toString();
        String assinatura = token.substring(token.lastIndexOf('.') + 1);
        assertFalse(gravado.contains(assinatura), gravado);
        assertFalse(gravado.contains("eyJ"), "nenhuma parte do JWT na trilha: " + gravado);
        assertFalse(gravado.toLowerCase().contains("bearer"), gravado);
    }

    @Test
    void periodoFiltraPeloInstanteGravado() {
        var antes = OffsetDateTime.now().minusSeconds(1);
        long camaraId = criarCamara(100);
        var depois = OffsetDateTime.now().plusSeconds(1);

        autenticado(Perfil.AUDITOR).queryParam("usuarioId", adminId.toString())
                .queryParam("de", antes.toString()).queryParam("ate", depois.toString()).get(TRILHA).then()
                .statusCode(200).body("items.recursoId", contains((int) camaraId));
        autenticado(Perfil.AUDITOR).queryParam("usuarioId", adminId.toString())
                .queryParam("de", depois.toString()).get(TRILHA).then().body("totalElements", equalTo(0));
        autenticado(Perfil.AUDITOR).queryParam("usuarioId", adminId.toString())
                .queryParam("ate", antes.toString()).get(TRILHA).then().body("totalElements", equalTo(0));
    }

    private long criarCamara(int capacidade) {
        var corpo = Map.of("codigo", codigo("A"), "nome", "Câmara", "unidade", "UBS teste",
                "capacidade", capacidade, "temperaturaMinima", 2, "temperaturaMaxima", 8, "estado", "OPERACIONAL");
        long id = admin().body(corpo).post("/api/camaras").then().statusCode(201).extract().jsonPath().getLong("id");
        camarasCriadas.add(id);
        return id;
    }

    private RequestSpecification admin() {
        return como(adminId, "admin-trilha", Perfil.ADMINISTRADOR);
    }

    private static RequestSpecification como(UUID id, String login, Perfil perfil) {
        var agora = Instant.now();
        return RestAssured.given().contentType("application/json").auth().oauth2(Jwt.issuer("friovax-api")
                .subject(id.toString()).upn(login).groups(Set.of(perfil.name()))
                .issuedAt(agora).expiresAt(agora.plusSeconds(900)).sign());
    }

    private static Map<String, Object> lote(String codigo, int quantidade, long camaraId) {
        return Map.of("codigo", codigo, "imunobiologico", "Vacina A", "fabricante", "Fabricante",
                "validade", "2099-12-31", "quantidade", quantidade, "camaraId", camaraId);
    }

    private static String codigo(String prefixo) {
        return "AU" + prefixo + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}

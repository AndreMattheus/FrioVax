package br.ufrn.friovax.api.camara.api;

import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.CamaraRepository;
import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import br.ufrn.friovax.api.lote.dominio.Lote;
import br.ufrn.friovax.api.lote.dominio.LoteRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class CamaraListagemHttpTest {
    @Inject CamaraRepository camaras;
    @Inject LoteRepository lotes;
    @Inject EntityManager entityManager;

    @Test
    void filtraAntesDePaginarComTotalOrdenacaoEOcupacaoCorretos() {
        String unidade = "UBS " + UUID.randomUUID();
        List<Long> ids = new ArrayList<>();
        try {
            QuarkusTransaction.requiringNew().run(() -> {
                ids.add(criar(unidade, EstadoCamara.MANUTENCAO, true));
                ids.add(criar(unidade + " outra", EstadoCamara.OPERACIONAL, true));
                ids.add(criar(unidade, EstadoCamara.OPERACIONAL, false));
                ids.add(criar(unidade, EstadoCamara.OPERACIONAL, true));
                ids.add(criar(unidade, EstadoCamara.OPERACIONAL, true));
                var hoje = LocalDate.now();
                var agora = OffsetDateTime.now();
                lotes.salvar(Lote.novo("L-" + UUID.randomUUID(), "Vacina", "Fabricante",
                        hoje.plusYears(1), 15, ids.get(3), hoje, agora));
                var inativo = lotes.salvar(Lote.novo("L-" + UUID.randomUUID(), "Vacina", "Fabricante",
                        hoje.plusYears(1), 7, ids.get(3), hoje, agora));
                inativo.inativar(agora.plusSeconds(1));
                lotes.salvar(inativo);
            });
            for (int page = 0; page < 2; page++) {
                given().queryParam("unidade", "  " + unidade.toLowerCase() + "  ")
                        .queryParam("estado", "OPERACIONAL").queryParam("size", 1).queryParam("page", page)
                        .when().get("/api/camaras").then().statusCode(200)
                        .body("items.size()", equalTo(1), "items[0].id", equalTo(ids.get(3 + page).intValue()),
                                "items[0].ocupacao", equalTo(page == 0 ? 15 : 0),
                                "page", equalTo(page), "size", equalTo(1),
                                "totalElements", equalTo(2), "totalPages", equalTo(2));
            }
            for (int page : new int[]{2, Integer.MAX_VALUE}) {
                given().queryParam("unidade", unidade).queryParam("estado", "OPERACIONAL")
                        .queryParam("page", page).queryParam("size", 100)
                        .when().get("/api/camaras").then().statusCode(200)
                        .body("items", empty(), "page", equalTo(page), "size", equalTo(100),
                                "totalElements", equalTo(2), "totalPages", equalTo(1));
            }
            given().queryParam("unidade", unidade).queryParam("ativo", false)
                    .when().get("/api/camaras").then().statusCode(200)
                    .body("items[0].id", equalTo(ids.get(2).intValue()), "items[0].ativo", equalTo(false),
                            "page", equalTo(0), "size", equalTo(20), "totalElements", equalTo(1), "totalPages", equalTo(1));
            given().queryParam("unidade", unidade).queryParam("estado", "MANUTENCAO")
                    .when().get("/api/camaras").then().statusCode(200)
                    .body("items[0].id", equalTo(ids.get(0).intValue()), "totalElements", equalTo(1));
            given().queryParam("unidade", unidade + " inexistente")
                    .when().get("/api/camaras").then().statusCode(200)
                    .body("items", empty(), "totalElements", equalTo(0), "totalPages", equalTo(0));
        } finally {
            QuarkusTransaction.requiringNew().run(() -> {
                for (Long id : ids) {
                    entityManager.createNativeQuery("delete from lotes where camara_id = :id")
                            .setParameter("id", id).executeUpdate();
                    entityManager.createNativeQuery("delete from camaras where id = :id")
                            .setParameter("id", id).executeUpdate();
                }
            });
        }
    }

    @Test
    void parametrosInvalidosRetornamProblemDetails() {
        for (String[] parametro : new String[][]{
                {"page", "-1"}, {"page", "abc"}, {"page", "1.5"}, {"page", "2147483648"},
                {"page", ""}, {"size", "0"}, {"size", "101"}, {"size", "abc"},
                {"size", ""}, {"estado", "INVALIDO"}, {"estado", "0"},
                {"ativo", "sim"}, {"ativo", "1"}, {"ativo", ""}}) {
            given().queryParam(parametro[0], parametro[1]).when().get("/api/camaras").then()
                    .statusCode(400).contentType("application/problem+json")
                    .body("type", equalTo("/problemas/requisicao-invalida"), "status", equalTo(400),
                            "title", notNullValue(), "detail", notNullValue(), "instance", equalTo("/api/camaras"));
        }
    }

    private long criar(String unidade, EstadoCamara estado, boolean ativo) {
        var agora = OffsetDateTime.now();
        var camara = camaras.salvar(Camara.nova("C-" + UUID.randomUUID().toString().substring(0, 8),
                "Mesmo nome", unidade, 100, new BigDecimal("2.0"), new BigDecimal("8.0"), estado, agora));
        if (!ativo) {
            camara.inativar(false, agora.plusSeconds(1));
            camaras.salvar(camara);
        }
        return camara.getId();
    }
}

package br.ufrn.friovax.api.camara.api;

import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.CamaraRepository;
import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import br.ufrn.friovax.api.camara.dominio.PoliticaTermica;
import br.ufrn.friovax.api.suporte.ColetorDeMudancas;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PUT de câmara de ponta a ponta com a política versionada no PostgreSQL (S2-05). */
@QuarkusTest
class CamaraPoliticaTermicaHttpTest {
    private static final String ROTA = "/api/camaras/{id}";

    @Inject
    CamaraRepository camaras;

    @Inject
    ColetorDeMudancas coletor;

    @Inject
    EntityManager entityManager;

    private long id;

    @BeforeEach
    void criarCamara() {
        var codigo = "H-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        id = QuarkusTransaction.requiringNew().call(() -> camaras.salvar(Camara.nova(codigo, "Câmara", "UBS teste",
                1000, new BigDecimal("2.0"), new BigDecimal("8.0"), EstadoCamara.OPERACIONAL,
                OffsetDateTime.now())).getId());
    }

    @AfterEach
    void apagarCamara() {
        QuarkusTransaction.requiringNew().run(() -> entityManager
                .createNativeQuery("delete from camaras where id = :id").setParameter("id", id).executeUpdate());
    }

    @Test
    void novaFaixaCriaVersaoPreservaAAnteriorEAvisaDepoisDoCommit() {
        given().contentType("application/json").body(corpo("3.0", "7.0"))
                .when().put(ROTA, id).then()
                .statusCode(200)
                .body("temperaturaMinima", equalTo(3.0f),
                        "temperaturaMaxima", equalTo(7.0f),
                        "estadoTermico", equalTo("SEM_LEITURAS"));

        var historico = historico();
        assertEquals(2, historico.size());
        assertEquals(new BigDecimal("2.0"), historico.get(0).getTemperaturaMinima(), "a versão 1 continua igual");
        assertEquals(new BigDecimal("3.0"), historico.get(1).getTemperaturaMinima());
        assertEquals(PoliticaTermica.LIMIAR_INICIAL, historico.get(1).getLimiar(), "o PUT preserva o limiar");
        assertEquals(1, coletor.recebidasDe("camara", id).size());
    }

    @Test
    void mesmaFaixaNaoCriaVersao() {
        given().contentType("application/json").body(corpo("2.0", "8.00"))
                .when().put(ROTA, id).then().statusCode(200);

        assertEquals(1, historico().size());
    }

    @Test
    void faixaInvalidaNaoCriaVersaoNemAvisa() {
        given().contentType("application/json").body(corpo("8.0", "2.0"))
                .when().put(ROTA, id).then()
                .statusCode(422)
                .contentType("application/problem+json");

        assertEquals(1, historico().size());
        assertTrue(coletor.recebidasDe("camara", id).isEmpty());
    }

    private List<PoliticaTermica> historico() {
        return QuarkusTransaction.requiringNew().call(() -> camaras.historicoDePoliticas(id));
    }

    private static String corpo(String minima, String maxima) {
        return """
                {
                  "nome": "Câmara",
                  "unidade": "UBS teste",
                  "capacidade": 1000,
                  "temperaturaMinima": %s,
                  "temperaturaMaxima": %s,
                  "estado": "OPERACIONAL"
                }
                """.formatted(minima, maxima);
    }
}

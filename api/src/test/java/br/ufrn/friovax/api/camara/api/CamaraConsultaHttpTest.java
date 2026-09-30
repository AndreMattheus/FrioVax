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
import java.time.ZoneOffset;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class CamaraConsultaHttpTest {
    @Inject
    CamaraRepository camaras;

    @Inject
    LoteRepository lotes;

    @Inject
    EntityManager entityManager;

    @Test
    void retorna200ComDtoEOcupacaoDosLotesAtivos() {
        long id = criarCamaraComLotes(false);
        try {
            given().when().get("/api/camaras/{id}", id).then()
                    .statusCode(200)
                    .contentType("application/json")
                    .body("id", equalTo((int) id),
                            "codigo", notNullValue(),
                            "nome", equalTo("Câmara de teste"),
                            "unidade", equalTo("UBS Centro"),
                            "capacidade", equalTo(5000),
                            "temperaturaMinima", equalTo(2.0f),
                            "temperaturaMaxima", equalTo(8.0f),
                            "estado", equalTo("OPERACIONAL"),
                            "ocupacao", equalTo(1200),
                            "ativo", equalTo(true),
                            "criadoEm", notNullValue(),
                            "atualizadoEm", notNullValue());
        } finally {
            apagarCamara(id);
        }
    }

    @Test
    void retorna200ParaCamaraInativa() {
        long id = criarCamaraComLotes(true);
        try {
            given().when().get("/api/camaras/{id}", id).then()
                    .statusCode(200)
                    .body("ativo", equalTo(false), "ocupacao", equalTo(0));
        } finally {
            apagarCamara(id);
        }
    }

    @Test
    void retorna404ParaIdInexistente() {
        given().when().get("/api/camaras/{id}", Long.MAX_VALUE).then()
                .statusCode(404)
                .contentType("application/problem+json")
                .body("type", equalTo("/problemas/recurso-nao-encontrado"),
                        "instance", equalTo("/api/camaras/" + Long.MAX_VALUE));
    }

    private long criarCamaraComLotes(boolean inativa) {
        return QuarkusTransaction.requiringNew().call(() -> {
            var agora = OffsetDateTime.now(ZoneOffset.ofHours(-3));
            var codigo = "C-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            var camara = camaras.salvar(Camara.nova(codigo, "Câmara de teste", "UBS Centro", 5000,
                    new BigDecimal("2.0"), new BigDecimal("8.0"), EstadoCamara.OPERACIONAL, agora));
            if (inativa) {
                camara.inativar(false, agora.plusSeconds(1));
                camaras.salvar(camara);
            } else {
                lotes.salvar(Lote.novo("L-" + UUID.randomUUID().toString().substring(0, 8),
                        "Vacina A", "Fabricante", LocalDate.now().plusYears(1), 1200,
                        camara.getId(), LocalDate.now(), agora));
                var inativo = lotes.salvar(Lote.novo("L-" + UUID.randomUUID().toString().substring(0, 8),
                        "Vacina B", "Fabricante", LocalDate.now().plusYears(1), 300,
                        camara.getId(), LocalDate.now(), agora));
                inativo.inativar(agora.plusSeconds(1));
                lotes.salvar(inativo);
            }
            return camara.getId();
        });
    }

    private void apagarCamara(long id) {
        QuarkusTransaction.requiringNew().run(() -> {
            entityManager.createNativeQuery("delete from lotes where camara_id = :id")
                    .setParameter("id", id).executeUpdate();
            entityManager.createNativeQuery("delete from camaras where id = :id")
                    .setParameter("id", id).executeUpdate();
        });
    }
}

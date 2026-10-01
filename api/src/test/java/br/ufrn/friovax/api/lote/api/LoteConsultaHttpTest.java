package br.ufrn.friovax.api.lote.api;

import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.CamaraRepository;
import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import br.ufrn.friovax.api.lote.dominio.Lote;
import br.ufrn.friovax.api.lote.dominio.LoteRepository;
import br.ufrn.friovax.api.lote.dominio.MotivoBaixa;
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
import java.util.function.Consumer;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class LoteConsultaHttpTest {
    private static final String ROTA = "/api/lotes/{id}";
    private static final LocalDate VALIDADE = LocalDate.now().plusYears(1);

    @Inject
    CamaraRepository camaras;

    @Inject
    LoteRepository lotes;

    @Inject
    EntityManager entityManager;

    @Test
    void retorna200ComDtoECamaraDoLote() {
        var lote = criarLote(l -> { });
        try {
            given().when().get(ROTA, lote.getId()).then()
                    .statusCode(200)
                    .contentType("application/json")
                    .body("id", equalTo(lote.getId().intValue()),
                            "codigo", equalTo(lote.getCodigo()),
                            "imunobiologico", equalTo("Febre amarela"),
                            "fabricante", equalTo("Bio-Manguinhos"),
                            "validade", equalTo(VALIDADE.toString()),
                            "quantidade", equalTo(1200),
                            "camaraId", equalTo((int) lote.getCamaraId()),
                            "estado", equalTo("DISPONIVEL"),
                            "ativo", equalTo(true),
                            "criadoEm", notNullValue(),
                            "atualizadoEm", notNullValue());
        } finally {
            apagar(lote);
        }
    }

    @Test
    void retorna200ParaLoteInativo() {
        var lote = criarLote(l -> l.inativar(agora().plusSeconds(1)));
        try {
            given().when().get(ROTA, lote.getId()).then()
                    .statusCode(200)
                    .body("ativo", equalTo(false), "quantidade", equalTo(1200));
        } finally {
            apagar(lote);
        }
    }

    @Test
    void retorna200ComEstadoDoLoteEsgotado() {
        var lote = criarLote(l -> l.darBaixa(1200, MotivoBaixa.ADMINISTRADA, agora().plusSeconds(1)));
        try {
            given().when().get(ROTA, lote.getId()).then()
                    .statusCode(200)
                    .body("estado", equalTo("ESGOTADO"), "quantidade", equalTo(0), "ativo", equalTo(true));
        } finally {
            apagar(lote);
        }
    }

    @Test
    void retorna404ParaIdInexistente() {
        given().when().get(ROTA, Long.MAX_VALUE).then()
                .statusCode(404)
                .contentType("application/problem+json")
                .body("type", equalTo("/problemas/recurso-nao-encontrado"),
                        "instance", equalTo("/api/lotes/" + Long.MAX_VALUE));
    }

    private Lote criarLote(Consumer<Lote> ajuste) {
        return QuarkusTransaction.requiringNew().call(() -> {
            var sufixo = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            var camara = camaras.salvar(Camara.nova("C-" + sufixo, "Câmara de teste", "UBS Centro", 5000,
                    new BigDecimal("2.0"), new BigDecimal("8.0"), EstadoCamara.OPERACIONAL, agora()));
            var lote = lotes.salvar(Lote.novo("L-" + sufixo, "Febre amarela", "Bio-Manguinhos", VALIDADE, 1200,
                    camara.getId(), LocalDate.now(), agora()));
            ajuste.accept(lote);
            return lotes.salvar(lote);
        });
    }

    private static OffsetDateTime agora() {
        return OffsetDateTime.now(ZoneOffset.ofHours(-3));
    }

    private void apagar(Lote lote) {
        QuarkusTransaction.requiringNew().run(() -> {
            entityManager.createNativeQuery("delete from lotes where id = :id")
                    .setParameter("id", lote.getId()).executeUpdate();
            entityManager.createNativeQuery("delete from camaras where id = :id")
                    .setParameter("id", lote.getCamaraId()).executeUpdate();
        });
    }
}

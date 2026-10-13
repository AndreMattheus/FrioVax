package br.ufrn.friovax.api.lote.api;

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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.empty;

@QuarkusTest
class LoteConsultaPostgresTest {
    private static final LocalDate VALIDADE = LocalDate.now().plusYears(1);

    @Inject
    CamaraRepository camaras;

    @Inject
    LoteRepository lotes;

    @Inject
    EntityManager entityManager;

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

    @Test
    void listaComFiltrosCombinadosOrdenacaoEPaginacaoNoBanco() {
        List<Lote> criados = new ArrayList<>();
        try {
            var primeiro = criarLote(l -> { });
            criados.add(primeiro);
            QuarkusTransaction.requiringNew().run(() -> {
                criados.add(criarNaCamara(primeiro, "Vacina FEBRE tifoide", VALIDADE, l -> { }));
                criados.add(criarNaCamara(primeiro, "Hepatite B", VALIDADE, l -> { }));
                criados.add(criarNaCamara(primeiro, "Febre amarela", VALIDADE.plusDays(1), l -> { }));
                criados.add(criarNaCamara(primeiro, "Febre amarela", VALIDADE,
                        l -> l.inativar(agora().plusSeconds(1))));
                criados.add(criarNaCamara(primeiro, "Febre amarela", VALIDADE,
                        l -> l.descartar(agora().plusSeconds(1))));
            });
            criados.add(criarLote(l -> { }));
            for (int page = 0; page < 2; page++) {
                given().queryParam("camaraId", primeiro.getCamaraId())
                        .queryParam("imunobiologico", "  fEbRe  ")
                        .queryParam("validadeDe", VALIDADE.toString())
                        .queryParam("validadeAte", VALIDADE.toString())
                        .queryParam("estado", " DISPONIVEL ").queryParam("page", page).queryParam("size", 1)
                        .when().get("/api/lotes").then().statusCode(200).contentType("application/json")
                        .body("items.size()", equalTo(1), "items[0].id", equalTo(criados.get(page).getId().intValue()),
                                "items[0].camaraId", equalTo((int) primeiro.getCamaraId()),
                                "items[0].ativo", equalTo(true), "page", equalTo(page), "size", equalTo(1),
                                "totalElements", equalTo(2), "totalPages", equalTo(2));
            }
            for (int page : new int[]{2, Integer.MAX_VALUE}) {
                given().queryParam("camaraId", primeiro.getCamaraId()).queryParam("estado", "DISPONIVEL")
                        .queryParam("imunobiologico", "febre").queryParam("validadeAte", VALIDADE.toString())
                        .queryParam("page", page).queryParam("size", page == 2 ? 1 : 100)
                        .get("/api/lotes").then().statusCode(200)
                        .body("items", empty(), "totalElements", equalTo(2), "page", equalTo(page),
                                "totalPages", equalTo(page == 2 ? 2 : 1));
            }
            given().queryParam("camaraId", primeiro.getCamaraId()).queryParam("ativo", false)
                    .get("/api/lotes").then().statusCode(200)
                    .body("items[0].id", equalTo(criados.get(4).getId().intValue()), "items[0].ativo", equalTo(false),
                            "totalElements", equalTo(1), "totalPages", equalTo(1));
            given().queryParam("camaraId", primeiro.getCamaraId()).queryParam("estado", "DESCARTADO")
                    .get("/api/lotes").then().statusCode(200)
                    .body("items[0].id", equalTo(criados.get(5).getId().intValue()), "totalElements", equalTo(1));
            given().queryParam("camaraId", primeiro.getCamaraId()).queryParam("validadeDe", VALIDADE.plusDays(1).toString())
                    .get("/api/lotes").then().statusCode(200)
                    .body("items[0].id", equalTo(criados.get(3).getId().intValue()), "totalElements", equalTo(1));
        } finally {
            apagarTodos(criados);
        }
    }

    @Test
    void buscaTrataCuringasComoTextoLiteral() {
        List<Lote> criados = new ArrayList<>();
        try {
            var referencia = criarLote(l -> { });
            criados.add(referencia);
            QuarkusTransaction.requiringNew().run(() -> {
                criados.add(criarNaCamara(referencia, "Influenza 100%", VALIDADE, l -> { }));
                criados.add(criarNaCamara(referencia, "Hepatite_B", VALIDADE, l -> { }));
                criados.add(criarNaCamara(referencia, "Vacina!", VALIDADE, l -> { }));
            });
            var termos = List.of("%", "_", "!");
            for (int i = 0; i < termos.size(); i++) {
                given().queryParam("camaraId", referencia.getCamaraId()).queryParam("imunobiologico", termos.get(i))
                        .get("/api/lotes").then().statusCode(200)
                        .body("items[0].id", equalTo(criados.get(i + 1).getId().intValue()),
                                "items.size()", equalTo(1), "totalElements", equalTo(1), "totalPages", equalTo(1));
            }
        } finally {
            apagarTodos(criados);
        }
    }

    private Lote criarNaCamara(Lote referencia, String imunobiologico, LocalDate validade, Consumer<Lote> ajuste) {
        var lote = lotes.salvar(Lote.novo("L-" + UUID.randomUUID(), imunobiologico, "Fabricante", validade,
                10, referencia.getCamaraId(), LocalDate.now(), agora()));
        ajuste.accept(lote);
        return lotes.salvar(lote);
    }

    private void apagarTodos(List<Lote> criados) {
        QuarkusTransaction.requiringNew().run(() -> {
            for (Lote lote : criados) {
                entityManager.createNativeQuery("delete from lotes where id = :id")
                        .setParameter("id", lote.getId()).executeUpdate();
            }
            for (Long camaraId : criados.stream().map(Lote::getCamaraId).distinct().toList()) {
                entityManager.createNativeQuery("delete from camaras where id = :id")
                        .setParameter("id", camaraId).executeUpdate();
            }
        });
    }

    private static OffsetDateTime agora() {
        return OffsetDateTime.now(ZoneOffset.ofHours(-3));
    }

}

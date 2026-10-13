package br.ufrn.friovax.api.lote.api;

import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import br.ufrn.friovax.api.lote.dominio.Lote;
import br.ufrn.friovax.api.lote.dominio.MotivoBaixa;
import br.ufrn.friovax.api.suporte.HttpEmMemoriaProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
@TestProfile(HttpEmMemoriaProfile.class)
class LoteConsultaHttpMemoriaTest extends LoteHttpMemoriaSupport {
    private static final String ROTA = "/api/lotes/{id}";
    private static final LocalDate VALIDADE = LocalDate.now(HttpEmMemoriaProfile.RELOGIO).plusYears(1);

    @Test
    void retorna200ComDtoECamaraDoLote() {
        var lote = criarLote(l -> { });
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
    }

    @Test
    void retorna200ParaLoteInativo() {
        var lote = criarLote(l -> l.inativar(agora().plusSeconds(1)));
        given().when().get(ROTA, lote.getId()).then()
                .statusCode(200)
                .body("ativo", equalTo(false), "quantidade", equalTo(1200));
    }

    @Test
    void retorna200ComEstadoDoLoteEsgotado() {
        var lote = criarLote(l -> l.darBaixa(1200, MotivoBaixa.ADMINISTRADA, agora().plusSeconds(1)));
        given().when().get(ROTA, lote.getId()).then()
                .statusCode(200)
                .body("estado", equalTo("ESGOTADO"), "quantidade", equalTo(0), "ativo", equalTo(true));
    }

    @Test
    void retorna404ParaIdInexistente() {
        problemaHttp(given().queryParam("origem", "teste").get(ROTA, Long.MAX_VALUE),
                404, "recurso-nao-encontrado", "/api/lotes/" + Long.MAX_VALUE);
    }

    @Test
    void verboNaoSuportadoRetorna405ComProblemDetails() {
        problemaHttp(given().queryParam("origem", "teste").patch("/api/lotes"),
                405, "metodo-nao-permitido", "/api/lotes");
    }

    private Lote criarLote(Consumer<Lote> ajuste) {
        var sufixo = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        var camara = camaras.salvar(Camara.nova("C-" + sufixo, "Câmara de teste", "UBS Centro", 5000,
                new BigDecimal("2.0"), new BigDecimal("8.0"), EstadoCamara.OPERACIONAL, agora()));
        var lote = lotes.salvar(Lote.novo("L-" + sufixo, "Febre amarela", "Bio-Manguinhos", VALIDADE, 1200,
                camara.getId(), LocalDate.now(HttpEmMemoriaProfile.RELOGIO), agora()));
        ajuste.accept(lote);
        return lotes.salvar(lote);
    }

    @Test
    void listaComFiltrosCombinadosOrdenacaoEPaginacao() {
        List<Lote> criados = new ArrayList<>();
        var primeiro = criarLote(l -> { });
        criados.add(primeiro);
        criados.add(criarNaCamara(primeiro, "Vacina FEBRE tifoide", VALIDADE, l -> { }));
        criados.add(criarNaCamara(primeiro, "Hepatite B", VALIDADE, l -> { }));
        criados.add(criarNaCamara(primeiro, "Febre amarela", VALIDADE.plusDays(1), l -> { }));
        criados.add(criarNaCamara(primeiro, "Febre amarela", VALIDADE,
                l -> l.inativar(agora().plusSeconds(1))));
        criados.add(criarNaCamara(primeiro, "Febre amarela", VALIDADE,
                l -> l.descartar(agora().plusSeconds(1))));
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
    }

    @Test
    void listaComPadroesDtoCompletoEFiltrosOpcionaisEmBranco() {
        var lote = criarLote(l -> { });
        given().get("/api/lotes").then().statusCode(200)
                .body("page", equalTo(0), "size", equalTo(20), "items.ativo", everyItem(equalTo(true)));
        given().queryParam("camaraId", lote.getCamaraId()).queryParam("imunobiologico", "  ")
                .queryParam("estado", " ").get("/api/lotes").then().statusCode(200)
                .body("page", equalTo(0), "size", equalTo(20), "totalElements", equalTo(1), "totalPages", equalTo(1),
                        "items[0].id", equalTo(lote.getId().intValue()), "items[0].codigo", equalTo(lote.getCodigo()),
                        "items[0].imunobiologico", equalTo(lote.getImunobiologico()),
                        "items[0].fabricante", equalTo(lote.getFabricante()), "items[0].validade", equalTo(VALIDADE.toString()),
                        "items[0].quantidade", equalTo(1200), "items[0].camaraId", equalTo((int) lote.getCamaraId()),
                        "items[0].estado", equalTo("DISPONIVEL"), "items[0].ativo", equalTo(true),
                        "items[0].criadoEm", notNullValue(), "items[0].atualizadoEm", notNullValue());
        given().queryParam("camaraId", lote.getCamaraId()).queryParam("size", 100).get("/api/lotes").then()
                .statusCode(200).body("size", equalTo(100), "totalElements", equalTo(1));
    }

    @Test
    void buscaTrataCuringasComoTextoLiteral() {
        List<Lote> criados = new ArrayList<>();
        var referencia = criarLote(l -> { });
        criados.add(referencia);
        criados.add(criarNaCamara(referencia, "Influenza 100%", VALIDADE, l -> { }));
        criados.add(criarNaCamara(referencia, "Hepatite_B", VALIDADE, l -> { }));
        criados.add(criarNaCamara(referencia, "Vacina!", VALIDADE, l -> { }));
        var termos = List.of("%", "_", "!");
        for (int i = 0; i < termos.size(); i++) {
            given().queryParam("camaraId", referencia.getCamaraId()).queryParam("imunobiologico", termos.get(i))
                    .get("/api/lotes").then().statusCode(200)
                    .body("items[0].id", equalTo(criados.get(i + 1).getId().intValue()),
                            "items.size()", equalTo(1), "totalElements", equalTo(1), "totalPages", equalTo(1));
        }
    }

    @Test
    void listaEstadoEsgotado() {
        var lote = criarLote(l -> l.darBaixa(1200, MotivoBaixa.ADMINISTRADA, agora().plusSeconds(1)));
        given().queryParam("camaraId", lote.getCamaraId()).queryParam("estado", "ESGOTADO")
                .get("/api/lotes").then().statusCode(200)
                .body("items[0].id", equalTo(lote.getId().intValue()), "items[0].quantidade", equalTo(0),
                        "totalElements", equalTo(1));
    }

    @Test
    void listaVaziaComMetadadosParaCamaraInexistenteOuFiltroSemResultado() {
        given().queryParam("camaraId", Long.MAX_VALUE).get("/api/lotes").then().statusCode(200)
                .body("items", empty(), "page", equalTo(0), "size", equalTo(20),
                        "totalElements", equalTo(0), "totalPages", equalTo(0));
        given().queryParam("imunobiologico", UUID.randomUUID().toString()).get("/api/lotes").then().statusCode(200)
                .body("items", empty(), "totalElements", equalTo(0), "totalPages", equalTo(0));
    }

    @Test
    void listagemRejeitaParametrosInvalidosComProblemDetails() {
        for (String[] parametro : new String[][]{
                {"page", "-1"}, {"page", "abc"}, {"page", "1.5"}, {"page", "2147483648"}, {"page", ""},
                {"size", "0"}, {"size", "101"}, {"size", "abc"}, {"size", "1.5"}, {"size", ""},
                {"camaraId", "abc"}, {"camaraId", "1.5"}, {"camaraId", "9223372036854775808"}, {"camaraId", ""},
                {"estado", "INVALIDO"}, {"estado", "0"}, {"ativo", "sim"}, {"ativo", "1"}, {"ativo", "TRUE"}, {"ativo", ""},
                {"validadeDe", ""}, {"validadeAte", ""}, {"validadeDe", "2027-02-30"},
                {"validadeAte", "31/12/2027"}, {"validadeDe", "2027-01-01T12:00:00"}}) {
            given().queryParam(parametro[0], parametro[1]).get("/api/lotes").then().statusCode(400)
                    .contentType("application/problem+json")
                    .body("type", equalTo("/problemas/requisicao-invalida"), "status", equalTo(400),
                            "title", notNullValue(), "detail", notNullValue(), "instance", equalTo("/api/lotes"));
        }
        for (String parametro : List.of("validadeDe", "validadeAte", "camaraId", "ativo", "page", "size")) {
            given().queryParam(parametro, "  ").get("/api/lotes").then().statusCode(400)
                    .contentType("application/problem+json");
        }
        given().queryParam("validadeDe", "2027-12-31").queryParam("validadeAte", "2027-01-01")
                .get("/api/lotes").then().statusCode(400).contentType("application/problem+json")
                .body("type", equalTo("/problemas/requisicao-invalida"), "status", equalTo(400),
                        "instance", equalTo("/api/lotes"));
    }

    private Lote criarNaCamara(Lote referencia, String imunobiologico, LocalDate validade, Consumer<Lote> ajuste) {
        var lote = lotes.salvar(Lote.novo("L-" + UUID.randomUUID(), imunobiologico, "Fabricante", validade,
                10, referencia.getCamaraId(), LocalDate.now(HttpEmMemoriaProfile.RELOGIO), agora()));
        ajuste.accept(lote);
        return lotes.salvar(lote);
    }

    private static OffsetDateTime agora() {
        return OffsetDateTime.now(HttpEmMemoriaProfile.RELOGIO);
    }

}

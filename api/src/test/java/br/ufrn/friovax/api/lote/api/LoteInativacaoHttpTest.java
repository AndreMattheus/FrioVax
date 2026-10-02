package br.ufrn.friovax.api.lote.api;

import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.CamaraRepository;
import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import br.ufrn.friovax.api.compartilhado.dominio.Paginacao;
import br.ufrn.friovax.api.lote.dominio.Lote;
import br.ufrn.friovax.api.lote.dominio.LoteFiltro;
import br.ufrn.friovax.api.lote.dominio.LoteRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class LoteInativacaoHttpTest {
    @Inject CamaraRepository camaras;
    @Inject LoteRepository lotes;
    @Inject EntityManager entityManager;

    @Test
    void preservaRegistroEVinculoELiberaOcupacaoUmaUnicaVez() {
        long camaraId = QuarkusTransaction.requiringNew().call(() -> camaras.salvar(Camara.nova(
                "C-" + UUID.randomUUID().toString().substring(0, 8), "Câmara", "UBS teste", 1000,
                new BigDecimal("2.0"), new BigDecimal("8.0"), EstadoCamara.OPERACIONAL,
                OffsetDateTime.now())).getId());
        try {
            long loteId = QuarkusTransaction.requiringNew().call(() -> criarLote(camaraId, 120).getId());
            var original = QuarkusTransaction.requiringNew().call(() -> lotes.buscarPorId(loteId).orElseThrow());
            QuarkusTransaction.requiringNew().run(() -> criarLote(camaraId, 30));
            given().get("/api/camaras/{id}", camaraId).then().statusCode(200)
                    .body("ocupacao", equalTo(150));
            given().delete("/api/lotes/{id}", original.getId()).then().statusCode(204)
                    .body(equalTo(""));
            var primeiraAtualizacao = QuarkusTransaction.requiringNew().call(() -> {
                var inativo = lotes.buscarPorId(original.getId()).orElseThrow();
                assertFalse(inativo.isAtivo());
                assertEquals(original.getQuantidade(), inativo.getQuantidade());
                assertEquals(original.getCamaraId(), inativo.getCamaraId());
                assertEquals(original.getCodigo(), inativo.getCodigo());
                assertEquals(original.getEstado(), inativo.getEstado());
                assertEquals(original.getValidade(), inativo.getValidade());
                assertEquals(original.getFabricante(), inativo.getFabricante());
                assertEquals(original.getImunobiologico(), inativo.getImunobiologico());
                assertEquals(original.getCriadoEm().toInstant(), inativo.getCriadoEm().toInstant());
                assertTrue(lotes.existePorCodigo(original.getCodigo()));
                assertEquals(30, lotes.ocupacaoDaCamara(camaraId));
                var ativos = lotes.listar(new LoteFiltro(null, null, null, camaraId, null, true), Paginacao.padrao());
                assertEquals(1, ativos.totalElementos());
                assertNotEquals(original.getId(), ativos.itens().getFirst().getId());
                var inativos = lotes.listar(new LoteFiltro(null, null, null, camaraId, null, false), Paginacao.padrao());
                assertEquals(1, inativos.totalElementos());
                assertEquals(original.getId(), inativos.itens().getFirst().getId());
                return inativo.getAtualizadoEm();
            });
            given().delete("/api/lotes/{id}", original.getId()).then().statusCode(204).body(equalTo(""));
            QuarkusTransaction.requiringNew().run(() -> {
                assertEquals(primeiraAtualizacao, lotes.buscarPorId(original.getId()).orElseThrow().getAtualizadoEm());
                assertEquals(30, lotes.ocupacaoDaCamara(camaraId));
                assertEquals(30L, lotes.ocupacaoPorCamara(java.util.List.of(camaraId)).get(camaraId));
            });
            given().get("/api/camaras/{id}", camaraId).then().statusCode(200)
                    .body("ocupacao", equalTo(30));
        } finally {
            QuarkusTransaction.requiringNew().run(() -> {
                entityManager.createNativeQuery("delete from lotes where camara_id = :id")
                        .setParameter("id", camaraId).executeUpdate();
                entityManager.createNativeQuery("delete from camaras where id = :id")
                        .setParameter("id", camaraId).executeUpdate();
            });
        }
    }

    @Test
    void inexistenteRetorna404ComProblemDetails() {
        given().delete("/api/lotes/{id}", Long.MAX_VALUE).then().statusCode(404)
                .contentType("application/problem+json")
                .body("type", equalTo("/problemas/recurso-nao-encontrado"), "status", equalTo(404),
                        "instance", equalTo("/api/lotes/" + Long.MAX_VALUE));
    }

    private Lote criarLote(long camaraId, int quantidade) {
        var hoje = LocalDate.now();
        return lotes.salvar(Lote.novo("L-" + UUID.randomUUID(), "Vacina", "Fabricante",
                hoje.plusYears(1), quantidade, camaraId, hoje, OffsetDateTime.now()));
    }
}

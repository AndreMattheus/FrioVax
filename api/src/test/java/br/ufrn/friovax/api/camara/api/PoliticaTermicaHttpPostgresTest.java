package br.ufrn.friovax.api.camara.api;

import br.ufrn.friovax.api.autenticacao.dominio.Perfil;
import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.CamaraRepository;
import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import br.ufrn.friovax.api.suporte.UsuariosDeTeste;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static br.ufrn.friovax.api.suporte.AutenticacaoHttpSupport.autenticado;
import static br.ufrn.friovax.api.suporte.AutenticacaoHttpSupport.como;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Política térmica de ponta a ponta no PostgreSQL (S2-14), com administradores cadastrados: concorrência entre dois
 * administradores, autoria nos dois caminhos de alteração e rastreabilidade pelo histórico e pela trilha.
 */
@QuarkusTest
class PoliticaTermicaHttpPostgresTest {
    private static final String POLITICA = "/api/camaras/{id}/politica-termica";

    @Inject CamaraRepository camaras;
    @Inject EntityManager entityManager;

    private long id;
    private UUID ana;
    private UUID bruno;

    @BeforeEach
    void preparar() {
        ana = UsuariosDeTeste.cadastrar(entityManager, Perfil.ADMINISTRADOR);
        bruno = UsuariosDeTeste.cadastrar(entityManager, Perfil.ADMINISTRADOR);
        var codigo = "PT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        id = QuarkusTransaction.requiringNew().call(() -> camaras.salvar(Camara.nova(codigo, "Câmara",
                "UBS política", 1000, new BigDecimal("2.0"), new BigDecimal("8.0"), EstadoCamara.OPERACIONAL,
                OffsetDateTime.now())).getId());
    }

    @AfterEach
    void apagar() {
        QuarkusTransaction.requiringNew().run(() -> {
            // As versões saem em cascata com a câmara; só depois os usuários, que elas referenciam como autor.
            entityManager.createNativeQuery("delete from camaras where id = :id").setParameter("id", id)
                    .executeUpdate();
            entityManager.createNativeQuery("delete from eventos_auditoria where ator_id in (:ids)")
                    .setParameter("ids", List.of(ana, bruno)).executeUpdate();
        });
        UsuariosDeTeste.apagar(entityManager, ana);
        UsuariosDeTeste.apagar(entityManager, bruno);
    }

    @Test
    void doisAdministradoresNaMesmaVersaoNaoSeSobrescrevem() throws Exception {
        var largada = new CountDownLatch(1);
        Callable<Integer> deAna = () -> {
            largada.await(10, TimeUnit.SECONDS);
            return admin(ana).body(politica(1, 3.0, 7.0, 4)).put(POLITICA, id).then().extract().statusCode();
        };
        Callable<Integer> deBruno = () -> {
            largada.await(10, TimeUnit.SECONDS);
            return admin(bruno).body(politica(1, 1.0, 9.0, 2)).put(POLITICA, id).then().extract().statusCode();
        };
        List<Integer> status;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(deAna);
            var b = executor.submit(deBruno);
            largada.countDown();
            status = List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
        }

        // O lock da câmara serializa os dois PUTs: o segundo vê a versão 2 e recebe 409.
        assertEquals(List.of(200, 409), status.stream().sorted().toList(), "status: " + status);
        var vencedor = status.get(0) == 200 ? ana : bruno;
        var vigente = autenticado(Perfil.AUDITOR).get(POLITICA, id).then().statusCode(200)
                .body("versao", equalTo(2), "autorId", equalTo(vencedor.toString()))
                .extract().jsonPath();
        assertEquals(vencedor == ana ? 4 : 2, vigente.getInt("limiar"));
        autenticado(Perfil.AUDITOR).get(POLITICA + "/versoes", id).then().body("items.versao", contains(2, 1));
        autenticado(Perfil.AUDITOR).queryParam("recurso", "POLITICA_TERMICA").queryParam("recursoId", id)
                .get("/api/auditoria").then().body("items.ator.id", contains(vencedor.toString()));

        // Mais dois envios sobre a versão antiga, já sem disputa: os dois são recusados e nada muda.
        admin(ana).body(politica(1, 4.0, 6.0, 3)).put(POLITICA, id).then().statusCode(409)
                .body("type", equalTo("/problemas/versao-desatualizada"));
        admin(bruno).body(camara(4.0, 6.0, 1)).put("/api/camaras/{id}", id).then().statusCode(409)
                .body("type", equalTo("/problemas/versao-desatualizada"));
        assertEquals(2, versoesGravadas());
        assertEquals(1, eventosDaPolitica());
    }

    @Test
    void autorEhOAdministradorDoTokenNosDoisCaminhos() {
        var forjado = new HashMap<>(politica(1, 3.0, 7.0, 5));
        forjado.put("autorId", bruno.toString());
        admin(ana).body(forjado).put(POLITICA, id).then().statusCode(200)
                .body("versao", equalTo(2), "autorId", equalTo(ana.toString()));

        var corpo = new HashMap<>(camara(2.5, 7.0, 2));
        corpo.put("autorId", ana.toString());
        admin(bruno).body(corpo).put("/api/camaras/{id}", id).then().statusCode(200);

        var autores = QuarkusTransaction.requiringNew().call(() -> camaras.historicoDePoliticas(id)).stream()
                .map(p -> p.getAutorId().orElse(null)).toList();
        assertEquals(Arrays.asList(null, ana, bruno), autores, "a versão 1 continua sem autor");
        assertEquals(5, QuarkusTransaction.requiringNew().call(() -> camaras.buscarPorId(id).orElseThrow())
                .getPolitica().getLimiar(), "o PUT da câmara preserva o limiar");

        autenticado(Perfil.AUDITOR).queryParam("recurso", "POLITICA_TERMICA").queryParam("recursoId", id)
                .get("/api/auditoria").then()
                .body("items.ator.id", contains(ana.toString(), bruno.toString()),
                        "items.operacao", contains("ALTERACAO_DE_POLITICA", "ALTERACAO_DE_POLITICA"),
                        "items[1].descricao", equalTo("Política térmica da câmara " + codigo() + " passou da versão "
                                + "2 para a 3: faixa 3.0 a 7.0 °C → 2.5 a 7.0 °C"));
        // Só a faixa mudou no PUT da câmara: nenhuma atualização da câmara na trilha.
        autenticado(Perfil.AUDITOR).queryParam("recurso", "CAMARA").queryParam("recursoId", id)
                .get("/api/auditoria").then().body("totalElements", equalTo(0));
    }

    @Test
    void envioSemMudancaNaoCriaVersaoNemEventoMasOutrosCamposSaoAuditados() {
        admin(ana).body(politica(1, 2.0, 8.0, 3)).put(POLITICA, id).then().statusCode(200).body("versao", equalTo(1));
        admin(ana).body(camara(2.0, 8.0, 1)).put("/api/camaras/{id}", id).then().statusCode(200);
        assertEquals(1, versoesGravadas());
        assertEquals(0, eventosDaPolitica());

        var renomeada = new HashMap<>(camara(2.0, 8.0, 1));
        renomeada.put("nome", "Câmara renomeada");
        admin(ana).body(renomeada).put("/api/camaras/{id}", id).then().statusCode(200);
        autenticado(Perfil.AUDITOR).queryParam("recurso", "CAMARA").queryParam("recursoId", id)
                .get("/api/auditoria").then()
                .body("items.operacao", contains("ATUALIZACAO"), "items[0].ator.id", equalTo(ana.toString()));
        assertEquals(1, versoesGravadas());
    }

    @Test
    void edicaoAntigaDaCamaraSemVersaoNaoSobrescreveAFaixaNova() {
        // Ana muda a faixa pela rota de política; Bruno ainda está com a tela da câmara aberta na versão 1.
        admin(ana).body(politica(1, 3.0, 7.0, 3)).put(POLITICA, id).then().statusCode(200).body("versao", equalTo(2));

        var antiga = new HashMap<String, Object>(Map.of("nome", "Câmara de Bruno", "unidade", "UBS política",
                "capacidade", 1000, "temperaturaMinima", 2.0, "temperaturaMaxima", 8.0, "estado", "OPERACIONAL"));
        admin(bruno).body(antiga).put("/api/camaras/{id}", id).then().statusCode(422)
                .contentType("application/problem+json")
                .body("erros.campo", contains("versaoEsperada"));
        antiga.put("versaoEsperada", 1);
        admin(bruno).body(antiga).put("/api/camaras/{id}", id).then().statusCode(409)
                .body("type", equalTo("/problemas/versao-desatualizada"));

        // Nada da edição antiga foi gravado: nem a faixa, nem o nome, nem evento.
        autenticado(Perfil.AUDITOR).get("/api/camaras/{id}", id).then()
                .body("nome", equalTo("Câmara"), "temperaturaMinima", equalTo(3.0f), "temperaturaMaxima", equalTo(7.0f));
        assertEquals(2, versoesGravadas());
        assertEquals(1, eventosDaPolitica());
        autenticado(Perfil.AUDITOR).queryParam("recurso", "CAMARA").queryParam("recursoId", id)
                .get("/api/auditoria").then().body("totalElements", equalTo(0));

        // Mantendo a faixa vigente, o PUT da câmara não precisa de versão.
        var semFaixaNova = new HashMap<>(antiga);
        semFaixaNova.remove("versaoEsperada");
        semFaixaNova.put("temperaturaMinima", 3.0);
        semFaixaNova.put("temperaturaMaxima", 7.0);
        admin(bruno).body(semFaixaNova).put("/api/camaras/{id}", id).then().statusCode(200)
                .body("nome", equalTo("Câmara de Bruno"));
        assertEquals(2, versoesGravadas());
    }

    @Test
    void autorQueNaoEhUsuarioNaoGravaVersaoNemEvento() {
        // Token válido de alguém fora de `usuarios`: a chave estrangeira do autor desfaz a transação inteira.
        var desconhecido = UUID.randomUUID();
        admin(desconhecido).body(politica(1, 3.0, 7.0, 3)).put(POLITICA, id).then().statusCode(500);

        assertEquals(1, versoesGravadas());
        assertEquals(0, eventosDaPolitica());
        admin(ana).body(politica(1, 3.0, 7.0, 3)).put(POLITICA, id).then().statusCode(200)
                .body("versao", equalTo(2));
    }

    private static RequestSpecification admin(UUID usuario) {
        return como(usuario, UsuariosDeTeste.login(usuario), Perfil.ADMINISTRADOR).contentType("application/json");
    }

    private static Map<String, Object> politica(int versao, double minima, double maxima, int limiar) {
        return Map.of("versaoEsperada", versao, "temperaturaMinima", minima, "temperaturaMaxima", maxima,
                "limiar", limiar);
    }

    private static Map<String, Object> camara(double minima, double maxima, int versaoEsperada) {
        return Map.of("nome", "Câmara", "unidade", "UBS política", "capacidade", 1000,
                "temperaturaMinima", minima, "temperaturaMaxima", maxima, "estado", "OPERACIONAL",
                "versaoEsperada", versaoEsperada);
    }

    private String codigo() {
        return QuarkusTransaction.requiringNew().call(() -> camaras.buscarPorId(id).orElseThrow().getCodigo());
    }

    private long versoesGravadas() {
        return QuarkusTransaction.requiringNew().call(() -> ((Number) entityManager
                .createNativeQuery("select count(*) from politicas_termicas where camara_id = :id")
                .setParameter("id", id).getSingleResult()).longValue());
    }

    private long eventosDaPolitica() {
        return QuarkusTransaction.requiringNew().call(() -> ((Number) entityManager
                .createNativeQuery("select count(*) from eventos_auditoria where recurso = 'POLITICA_TERMICA' "
                        + "and recurso_id = :id")
                .setParameter("id", id).getSingleResult()).longValue());
    }
}

package br.ufrn.friovax.api.autenticacao.api;

import br.ufrn.friovax.api.autenticacao.dominio.*;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import io.smallrye.jwt.build.Jwt;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static br.ufrn.friovax.api.autenticacao.api.AutenticacaoPostgresProfile.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(AutenticacaoPostgresProfile.class)
class AutenticacaoPostgresTest {
    @Inject EntityManager em;
    @Inject ProtecaoCredenciais protecao;

    @BeforeEach
    void ajustarRelogio() {
        RELOGIO.agora = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    }

    private Response login(String login, String senha) {
        return given().contentType("application/json").body(Map.of("login", login, "senha", senha))
                .post("/api/auth/login");
    }

    private Response refresh(String rota, String token) {
        return given().contentType("application/json").body(Map.of("refreshToken", token))
                .post("/api/auth/" + rota);
    }

    @Test
    void provisionaPerfisComBcryptEIdentidadeInternaSemConsultaAoBanco() {
        for (var perfil : Perfil.values()) {
            var login = perfil.name().toLowerCase(Locale.ROOT);
            var resposta = login(login, SENHA).then().statusCode(200)
                    .header("Cache-Control", "no-store")
                    .body("tokenType", equalTo("Bearer"), "expiresIn", equalTo(900),
                            "identidade.login", equalTo(login), "identidade.perfil", equalTo(perfil.name()))
                    .extract().response();
            var credencial = em.createQuery("select senhaHash from UsuarioEntity where login=:login", String.class)
                    .setParameter("login", login).getSingleResult();
            assertNotEquals(SENHA, credencial);
            assertTrue(protecao.verificarSenha(SENHA, credencial));
            var hash = em.createQuery("select tokenHash from SessaoEntity where tokenHash=:hash", String.class)
                    .setParameter("hash", protecao.hashRefresh(resposta.path("refreshToken"))).getSingleResult();
            assertNotEquals(resposta.path("refreshToken"), hash);
            var stats = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
            long consultas = stats.getPrepareStatementCount();
            given().auth().oauth2(resposta.path("accessToken")).get("/api/auth/identidade").then()
                    .statusCode(200).body("id", equalTo(resposta.path("identidade.id")),
                            "perfil", equalTo(perfil.name()));
            assertEquals(consultas, stats.getPrepareStatementCount());
            var claims = new String(Base64.getUrlDecoder().decode(
                    resposta.<String>path("accessToken").split("[.]")[1]), java.nio.charset.StandardCharsets.UTF_8);
            var json = new io.restassured.path.json.JsonPath(claims);
            assertEquals(RELOGIO.instant().getEpochSecond(), json.getLong("iat"));
            assertEquals(RELOGIO.instant().plusSeconds(900).getEpochSecond(), json.getLong("exp"));
        }
    }

    @Test
    void credenciaisInvalidasSempreRetornam401Generico() {
        var errada = login("operador", "incorreta").then().statusCode(401).extract().asString();
        assertEquals(errada, login("nao-existe", "incorreta").then().statusCode(401).extract().asString());
        assertFalse(errada.contains("operador"));
        assertFalse(errada.contains("incorreta"));
        given().contentType("application/json").body("{}").post("/api/auth/login").then().statusCode(401);
        given().contentType("application/json").post("/api/auth/login").then().statusCode(401);
        login("operador", "á".repeat(37)).then().statusCode(401);
    }

    @Test
    void rotacionaRevogaEAccessContinuaValidoAposLogout() {
        var inicial = login("operador", SENHA).then().statusCode(200).extract().response();
        var renovada = refresh("renovar", inicial.path("refreshToken")).then().statusCode(200).extract().response();
        assertNotEquals(inicial.<String>path("refreshToken"), renovada.<String>path("refreshToken"));
        refresh("renovar", inicial.path("refreshToken")).then().statusCode(401);
        refresh("logout", renovada.path("refreshToken")).then().statusCode(204);
        refresh("renovar", renovada.path("refreshToken")).then().statusCode(401);
        for (var token : List.of(inicial.<String>path("accessToken"), renovada.<String>path("accessToken"))) {
            given().auth().oauth2(token).get("/api/auth/identidade").then().statusCode(200);
        }
        refresh("renovar", "x".repeat(43)).then().statusCode(401);
        refresh("renovar", "invalido").then().statusCode(401);
    }

    @Test
    void refreshExpiraExatamenteEm24HorasComClockControlado() {
        var primeiro = login("operador", SENHA).then().statusCode(200).extract().<String>path("refreshToken");
        var segundo = login("operador", SENHA).then().statusCode(200).extract().<String>path("refreshToken");
        RELOGIO.agora = RELOGIO.agora.plusSeconds(86399);
        refresh("renovar", primeiro).then().statusCode(200);
        RELOGIO.agora = RELOGIO.agora.plusSeconds(1);
        refresh("renovar", segundo).then().statusCode(401);
    }

    @Test
    void apenasUmaRenovacaoConcorrentePodeConsumirORefresh() {
        var token = login("operador", SENHA).then().statusCode(200).extract().<String>path("refreshToken");
        var a = CompletableFuture.supplyAsync(() -> refresh("renovar", token).statusCode());
        var b = CompletableFuture.supplyAsync(() -> refresh("renovar", token).statusCode());
        assertEquals(List.of(200, 401), java.util.stream.Stream.of(a.join(), b.join()).sorted().toList());
    }

    private String assinar(Instant inicio, Instant fim, String emissor, PrivateKey chave) {
        var builder = Jwt.issuer(emissor).subject(UUID.randomUUID().toString()).upn("operador")
                .groups(Set.of("OPERADOR")).issuedAt(inicio).expiresAt(fim);
        return chave == null ? builder.sign() : builder.sign(chave);
    }

    @Test
    void rejeitaAccessExpiradoAssinaturaInvalidaEmissorInvalidoEAusente() throws Exception {
        var agora = RELOGIO.instant();
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var tokens = List.of(
                assinar(agora.minusSeconds(901), agora.minusSeconds(1), "friovax-api", null),
                assinar(agora, agora.plusSeconds(900), "friovax-api", generator.generateKeyPair().getPrivate()),
                assinar(agora, agora.plusSeconds(900), "outro-emissor", null));
        for (var token : tokens) {
            given().auth().oauth2(token).get("/api/auth/identidade").then().statusCode(401);
        }
        given().get("/api/auth/identidade").then().statusCode(401);
    }

    @Test
    void openApiDocumentaRotacaoLogoutEEsquemaBearer() {
        given().accept("application/json").get("/q/openapi").then().statusCode(200)
                .body("components.securitySchemes.bearerAuth.scheme", equalTo("bearer"),
                        "paths.'/api/auth/logout'.post.description", containsString("15 minutos"),
                        "paths.'/api/auth/renovar'.post.description", containsString("única vez"),
                        "paths.'/api/auth/identidade'.get.security.bearerAuth", notNullValue());
    }
}

package br.ufrn.friovax.api.suporte;

import br.ufrn.friovax.api.autenticacao.dominio.Perfil;
import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
import io.smallrye.jwt.build.Jwt;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Usa JWT real e as chaves exclusivas de teste, sem desligar os guards. */
public final class AutenticacaoHttpSupport {
    private AutenticacaoHttpSupport() {
    }

    public static RequestSpecification given() {
        return autenticado(Perfil.ADMINISTRADOR);
    }

    public static RequestSpecification autenticado(Perfil perfil) {
        return RestAssured.given().auth().oauth2(token(Set.of(perfil.name())));
    }

    public static String token(Set<String> perfis) {
        var agora = Instant.now();
        return Jwt.issuer("friovax-api").subject(UUID.randomUUID().toString()).upn("usuario-teste")
                .groups(perfis).issuedAt(agora).expiresAt(agora.plusSeconds(900)).sign();
    }
}

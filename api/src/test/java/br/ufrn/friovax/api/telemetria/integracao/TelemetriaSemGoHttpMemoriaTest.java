package br.ufrn.friovax.api.telemetria.integracao;

import br.ufrn.friovax.api.suporte.HttpEmMemoriaProfile;
import br.ufrn.friovax.api.telemetria.dominio.AnaliseIndisponivel;
import br.ufrn.friovax.api.telemetria.dominio.AnaliseTermica;
import br.ufrn.friovax.api.telemetria.dominio.SolicitacaoDeAnalise;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static br.ufrn.friovax.api.suporte.AutenticacaoHttpSupport.given;
import static br.ufrn.friovax.api.telemetria.integracao.JanelasDeTeste.janela;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** A API inicia, informa saúde e atende operações independentes sem o serviço Go no ar. */
@QuarkusTest
@TestProfile(HttpEmMemoriaProfile.class)
class TelemetriaSemGoHttpMemoriaTest {

    @Inject
    AnaliseTermica analise;

    @Test
    void saudeDoProcessoContinuaUpSemOGo() {
        RestAssured.get("/q/health/live").then().statusCode(200).body("status", equalTo("UP"));
        RestAssured.get("/q/health/ready").then().statusCode(200).body("status", equalTo("UP"));
        RestAssured.get("/api/status").then().statusCode(200).body("status", equalTo("UP"));
    }

    @Test
    void consultaIndependenteNaoDependeDoGo() {
        given().get("/api/camaras").then().statusCode(200);
    }

    @Test
    void analiseSemOGoEhIndisponibilidadeControlada() {
        var falha = assertThrows(AnaliseIndisponivel.class,
                () -> analise.analisar(SolicitacaoDeAnalise.de("sem-go", janela(1, "5"))));

        assertEquals(AnaliseIndisponivel.Motivo.SERVICO_INACESSIVEL, falha.motivo());
    }
}

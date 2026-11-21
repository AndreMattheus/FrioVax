package br.ufrn.friovax.api.telemetria.integracao;

import br.ufrn.friovax.api.autenticacao.dominio.Perfil;
import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import br.ufrn.friovax.api.compartilhado.dominio.Paginacao;
import br.ufrn.friovax.api.suporte.CamaraRepositoryEmMemoria;
import br.ufrn.friovax.api.suporte.HttpEmMemoriaProfile;
import br.ufrn.friovax.api.suporte.LeituraRepositoryEmMemoria;
import br.ufrn.friovax.api.telemetria.dominio.AnaliseIndisponivel;
import br.ufrn.friovax.api.telemetria.dominio.AnaliseTermica;
import br.ufrn.friovax.api.telemetria.dominio.SolicitacaoDeAnalise;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static br.ufrn.friovax.api.suporte.AutenticacaoHttpSupport.autenticado;
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

    @Inject
    CamaraRepositoryEmMemoria camaras;

    @Inject
    LeituraRepositoryEmMemoria leituras;

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
    void leituraSemOGoResponde503SemConfirmar() {
        camaras.limpar();
        leituras.limpar();
        long camaraId = camaras.salvar(Camara.nova("CAM-SEM-GO", "Câmara", "UBS", 100, new BigDecimal("2.0"),
                new BigDecimal("8.0"), EstadoCamara.OPERACIONAL,
                OffsetDateTime.now(HttpEmMemoriaProfile.RELOGIO))).getId();

        autenticado(Perfil.OPERADOR).contentType(ContentType.JSON)
                .body("""
                        {"idExterno": "S-1", "camaraId": %d, "temperatura": 9.0,
                         "medidaEm": "2026-10-11T01:00:00Z", "origem": "sensor"}
                        """.formatted(camaraId))
                .post("/api/leituras")
                .then().statusCode(503).body("type", equalTo("/problemas/analise-indisponivel"));

        assertEquals(0, leituras.listarDaCamara(camaraId, new Paginacao(0, 10)).totalElementos());
    }

    @Test
    void analiseSemOGoEhIndisponibilidadeControlada() {
        var falha = assertThrows(AnaliseIndisponivel.class,
                () -> analise.analisar(SolicitacaoDeAnalise.de("sem-go", janela(1, "5"))));

        assertEquals(AnaliseIndisponivel.Motivo.SERVICO_INACESSIVEL, falha.motivo());
    }
}

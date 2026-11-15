package br.ufrn.friovax.api.telemetria.api;

import br.ufrn.friovax.api.compartilhado.api.ProblemDetails;
import br.ufrn.friovax.api.telemetria.dominio.AnaliseIndisponivel;
import br.ufrn.friovax.api.telemetria.dominio.FalhaDeIntegracao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AnaliseExceptionMappersTest {

    @ParameterizedTest
    @EnumSource(AnaliseIndisponivel.Motivo.class)
    void indisponibilidadeVira503SemConfirmarLeitura(AnaliseIndisponivel.Motivo motivo) {
        var resposta = new AnaliseIndisponivelExceptionMapper().toResponse(new AnaliseIndisponivel(motivo));
        var problema = (ProblemDetails) resposta.getEntity();

        assertEquals(503, resposta.getStatus());
        assertEquals("application/problem+json", resposta.getMediaType().toString());
        assertEquals("/problemas/analise-indisponivel", problema.type());
        assertEquals(503, problema.status());
    }

    @Test
    void falhaDeIntegracaoVira502SemCulparOUsuarioNemExporDetalhes() {
        var detalhe = "Resposta inválida do serviço de análise térmica: evidência desconhecida na câmara 7.";

        var resposta = new FalhaDeIntegracaoExceptionMapper().toResponse(new FalhaDeIntegracao(detalhe));
        var problema = (ProblemDetails) resposta.getEntity();

        assertEquals(502, resposta.getStatus());
        assertEquals("/problemas/falha-de-integracao", problema.type());
        assertFalse(problema.detail().contains("câmara 7"), "o detalhe interno fica só no log");
        assertEquals(null, problema.erros());
    }
}

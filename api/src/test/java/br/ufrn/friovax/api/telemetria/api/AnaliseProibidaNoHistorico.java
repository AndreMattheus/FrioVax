package br.ufrn.friovax.api.telemetria.api;

import br.ufrn.friovax.api.telemetria.dominio.AnaliseTermica;
import br.ufrn.friovax.api.telemetria.dominio.ResultadoDaAnalise;
import br.ufrn.friovax.api.telemetria.dominio.SolicitacaoDeAnalise;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.util.concurrent.atomic.AtomicInteger;

/** Falha o teste caso uma consulta passe a chamar a porta de análise. */
@Alternative
@ApplicationScoped
public class AnaliseProibidaNoHistorico implements AnaliseTermica {
    private final AtomicInteger chamadas = new AtomicInteger();

    @Override
    public ResultadoDaAnalise analisar(SolicitacaoDeAnalise solicitacao) {
        chamadas.incrementAndGet();
        throw new AssertionError("Consulta de histórico não deve chamar Go");
    }

    public int chamadas() {
        return chamadas.get();
    }
}

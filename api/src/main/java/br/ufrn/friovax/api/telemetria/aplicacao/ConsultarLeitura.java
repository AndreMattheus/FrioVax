package br.ufrn.friovax.api.telemetria.aplicacao;

import br.ufrn.friovax.api.compartilhado.dominio.RecursoNaoEncontrado;
import br.ufrn.friovax.api.telemetria.dominio.Leitura;
import br.ufrn.friovax.api.telemetria.dominio.LeituraRepository;
import br.ufrn.friovax.api.telemetria.dominio.Ocorrencia;
import br.ufrn.friovax.api.telemetria.dominio.OcorrenciaRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ConsultarLeitura {
    private final LeituraRepository leituras;
    private final OcorrenciaRepository ocorrencias;

    @Inject
    public ConsultarLeitura(LeituraRepository leituras, OcorrenciaRepository ocorrencias) {
        this.leituras = leituras;
        this.ocorrencias = ocorrencias;
    }

    @Transactional
    public Resultado consultar(long id) {
        var leitura = leituras.buscarPorId(id).orElseThrow(() -> new RecursoNaoEncontrado("Leitura", id));
        return new Resultado(leitura, ocorrencias.abertaPelaLeitura(id).map(Ocorrencia::getId).orElse(null));
    }

    public record Resultado(Leitura leitura, Long ocorrenciaAbertaId) {
    }
}

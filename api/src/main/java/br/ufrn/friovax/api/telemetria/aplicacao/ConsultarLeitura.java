package br.ufrn.friovax.api.telemetria.aplicacao;

import br.ufrn.friovax.api.compartilhado.dominio.RecursoNaoEncontrado;
import br.ufrn.friovax.api.telemetria.dominio.Leitura;
import br.ufrn.friovax.api.telemetria.dominio.LeituraRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ConsultarLeitura {
    private final LeituraRepository leituras;

    @Inject
    public ConsultarLeitura(LeituraRepository leituras) {
        this.leituras = leituras;
    }

    @Transactional
    public Leitura consultar(long id) {
        return leituras.buscarPorId(id).orElseThrow(() -> new RecursoNaoEncontrado("Leitura", id));
    }
}

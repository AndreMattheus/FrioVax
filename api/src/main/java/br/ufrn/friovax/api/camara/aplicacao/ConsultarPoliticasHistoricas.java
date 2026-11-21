package br.ufrn.friovax.api.camara.aplicacao;

import br.ufrn.friovax.api.camara.dominio.CamaraRepository;
import br.ufrn.friovax.api.camara.dominio.PoliticaTermica;
import br.ufrn.friovax.api.camara.dominio.ReferenciaPolitica;
import br.ufrn.friovax.api.compartilhado.dominio.RecursoNaoEncontrado;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class ConsultarPoliticasHistoricas {
    private final CamaraRepository camaras;

    @Inject
    public ConsultarPoliticasHistoricas(CamaraRepository camaras) {
        this.camaras = camaras;
    }

    @Transactional
    public void exigirCamara(long id) {
        camaras.buscarPorId(id).orElseThrow(() -> new RecursoNaoEncontrado("Câmara", id));
    }

    @Transactional
    public Map<ReferenciaPolitica, PoliticaTermica> consultar(Set<ReferenciaPolitica> referencias) {
        return camaras.buscarPoliticas(referencias);
    }
}

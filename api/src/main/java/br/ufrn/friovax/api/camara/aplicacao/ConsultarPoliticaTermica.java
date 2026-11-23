package br.ufrn.friovax.api.camara.aplicacao;

import br.ufrn.friovax.api.camara.dominio.CamaraRepository;
import br.ufrn.friovax.api.camara.dominio.PoliticaTermica;
import br.ufrn.friovax.api.compartilhado.dominio.Pagina;
import br.ufrn.friovax.api.compartilhado.dominio.Paginacao;
import br.ufrn.friovax.api.compartilhado.dominio.RecursoNaoEncontrado;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ConsultarPoliticaTermica {
    private final CamaraRepository camaras;

    @Inject
    public ConsultarPoliticaTermica(CamaraRepository camaras) {
        this.camaras = camaras;
    }

    @Transactional
    public PoliticaTermica vigente(long camaraId) {
        return camaras.buscarPorId(camaraId)
            .orElseThrow(() -> new RecursoNaoEncontrado("Câmara", camaraId))
            .getPolitica();
    }

    @Transactional
    public Pagina<PoliticaTermica> historico(long camaraId, Paginacao paginacao) {
        if (camaras.buscarPorId(camaraId).isEmpty()) {
            throw new RecursoNaoEncontrado("Câmara", camaraId);
        }
        return camaras.listarPoliticas(camaraId, paginacao);
    }
}

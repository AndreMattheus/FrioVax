package br.ufrn.friovax.api.camara.aplicacao;

import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.CamaraRepository;
import br.ufrn.friovax.api.compartilhado.dominio.RecursoNaoEncontrado;
import br.ufrn.friovax.api.lote.dominio.LoteRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ConsultarCamara {
    private final CamaraRepository camaras;
    private final LoteRepository lotes;

    @Inject
    public ConsultarCamara(CamaraRepository camaras, LoteRepository lotes) {
        this.camaras = camaras;
        this.lotes = lotes;
    }

    @Transactional
    public Resultado consultar(long id) {
        // buscarPorId inclui registros inativos (contrato D11).
        Camara camara = camaras.buscarPorId(id)
                .orElseThrow(() -> new RecursoNaoEncontrado("Câmara", id));
        return new Resultado(camara, lotes.ocupacaoDaCamara(id));
    }

    public record Resultado(Camara camara, long ocupacao) {
    }
}

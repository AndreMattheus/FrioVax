package br.ufrn.friovax.api.camara.aplicacao;

import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.CamaraRepository;
import br.ufrn.friovax.api.compartilhado.dominio.RecursoNaoEncontrado;
import br.ufrn.friovax.api.lote.dominio.LoteRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;

@ApplicationScoped
public class AtualizarCamara {
    private final CamaraRepository camaras;
    private final LoteRepository lotes;
    private final Clock relogio;

    @Inject
    public AtualizarCamara(CamaraRepository camaras, LoteRepository lotes, Clock relogio) {
        this.camaras = camaras;
        this.lotes = lotes;
        this.relogio = relogio;
    }

    @Transactional
    public ConsultarCamara.Resultado atualizar(long id, AtualizarCamaraDTO dados) {
        Camara camara = camaras.buscarPorIdParaAlteracao(id)
                .orElseThrow(() -> new RecursoNaoEncontrado("Câmara", id));

        long ocupacao = lotes.ocupacaoDaCamara(id);
        boolean possuiLotesAtivos = lotes.existeLoteAtivoNaCamara(id);

        camara.atualizar(dados.nome(), dados.unidade(), dados.capacidade(),
                dados.temperaturaMinima(), dados.temperaturaMaxima(), dados.estado(),
                ocupacao, possuiLotesAtivos, OffsetDateTime.now(relogio));
        camaras.salvar(camara);

        return new ConsultarCamara.Resultado(camara, ocupacao);
    }
}

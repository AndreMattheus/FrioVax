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

/**
 * Remoção lógica da câmara (contrato, D12 e §3.3). Repetir numa câmara inativa não altera nada; com lotes ativos, a
 * inativação é rejeitada (D7).
 */
@ApplicationScoped
public class InativarCamara {
    private final CamaraRepository camaras;
    private final LoteRepository lotes;
    private final Clock relogio;

    @Inject
    public InativarCamara(CamaraRepository camaras, LoteRepository lotes, Clock relogio) {
        this.camaras = camaras;
        this.lotes = lotes;
        this.relogio = relogio;
    }

    @Transactional
    public void inativar(long id) {
        // O bloqueio da câmara serializa a inativação com as alocações de lotes, que bloqueiam a mesma linha (§3.2).
        Camara camara = camaras.buscarPorIdParaAlteracao(id)
                .orElseThrow(() -> new RecursoNaoEncontrado("Câmara", id));
        if (!camara.isAtivo()) {
            return;
        }
        camara.inativar(lotes.existeLoteAtivoNaCamara(id), OffsetDateTime.now(relogio));
        camaras.salvar(camara);
    }
}

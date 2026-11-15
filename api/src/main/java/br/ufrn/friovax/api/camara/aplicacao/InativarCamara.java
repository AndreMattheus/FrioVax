package br.ufrn.friovax.api.camara.aplicacao;

import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.CamaraRepository;
import br.ufrn.friovax.api.compartilhado.dominio.RecursoNaoEncontrado;
import br.ufrn.friovax.api.lote.dominio.LoteRepository;
import br.ufrn.friovax.api.compartilhado.aplicacao.MudancaConfirmada;
import br.ufrn.friovax.api.compartilhado.aplicacao.PublicadorDeMudancas;
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
    private final PublicadorDeMudancas publicador;

    @Inject
    public InativarCamara(CamaraRepository camaras, LoteRepository lotes, Clock relogio, PublicadorDeMudancas publicador) {
        this.camaras = camaras;
        this.lotes = lotes;
        this.relogio = relogio;
        this.publicador = publicador;
    }

    public InativarCamara(CamaraRepository camaras, LoteRepository lotes, Clock relogio) {
        this(camaras, lotes, relogio, PublicadorDeMudancas.nenhum());
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
        publicador.publicar(new MudancaConfirmada("camara", camara.getId(), "Inativação de câmara", null, OffsetDateTime.now(relogio)));
    }
}

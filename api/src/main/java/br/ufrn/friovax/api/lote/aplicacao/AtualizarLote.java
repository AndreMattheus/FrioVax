package br.ufrn.friovax.api.lote.aplicacao;

import br.ufrn.friovax.api.camara.dominio.CamaraRepository;
import br.ufrn.friovax.api.compartilhado.dominio.RecursoNaoEncontrado;
import br.ufrn.friovax.api.lote.dominio.Lote;
import br.ufrn.friovax.api.lote.dominio.LoteRepository;
import br.ufrn.friovax.api.compartilhado.aplicacao.MudancaConfirmada;
import br.ufrn.friovax.api.compartilhado.aplicacao.PublicadorDeMudancas;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@ApplicationScoped
public class AtualizarLote {
    private final LoteRepository lotes;
    private final CamaraRepository camaras;
    private final Clock relogio;
    private final PublicadorDeMudancas publicador;

    @Inject
    public AtualizarLote(LoteRepository lotes, CamaraRepository camaras, Clock relogio, PublicadorDeMudancas publicador) {
        this.lotes = lotes;
        this.camaras = camaras;
        this.relogio = relogio;
        this.publicador = publicador;
    }

    public AtualizarLote(LoteRepository lotes, CamaraRepository camaras, Clock relogio) {
        this(lotes, camaras, relogio, PublicadorDeMudancas.nenhum());
    }

    @Transactional
    public Lote atualizar(long id, String imunobiologico, String fabricante, LocalDate validade,
                          int quantidade, long camaraId) {
        // A linha do lote serializa alterações concorrentes do mesmo registro. A linha da câmara de destino
        // serializa as alocações e as mudanças de capacidade/estado com cadastro e PUT de câmara.
        var lote = lotes.buscarPorIdParaAlteracao(id)
                .orElseThrow(() -> new RecursoNaoEncontrado("Lote", id));
        lote.atualizar(imunobiologico, fabricante, validade, quantidade, camaraId,
                LocalDate.now(relogio), OffsetDateTime.now(relogio));

        var camara = camaras.buscarPorIdParaAlteracao(camaraId)
                .orElseThrow(() -> new RecursoNaoEncontrado("Câmara", camaraId));
        camara.verificarAlocacao(lotes.ocupacaoDaCamaraExcluindoLote(camaraId, id), quantidade);
        var salvo = lotes.salvar(lote);
        publicador.publicar(new MudancaConfirmada("lote", salvo.getId(), "Atualização de lote", null, OffsetDateTime.now(relogio)));
        return salvo;
    }
}

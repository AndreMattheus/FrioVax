package br.ufrn.friovax.api.lote.aplicacao;

import br.ufrn.friovax.api.camara.dominio.CamaraRepository;
import br.ufrn.friovax.api.compartilhado.dominio.CodigoDuplicado;
import br.ufrn.friovax.api.compartilhado.dominio.RecursoNaoEncontrado;
import br.ufrn.friovax.api.lote.dominio.Lote;
import br.ufrn.friovax.api.lote.dominio.LoteRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Cadastro de lote alocado numa câmara ativa e {@code OPERACIONAL} sem ultrapassar a capacidade (contrato, §3.2).
 */
@ApplicationScoped
public class CadastrarLote {
    private final LoteRepository lotes;
    private final CamaraRepository camaras;
    private final Clock relogio;

    @Inject
    public CadastrarLote(LoteRepository lotes, CamaraRepository camaras, Clock relogio) {
        this.lotes = lotes;
        this.camaras = camaras;
        this.relogio = relogio;
    }

    @Transactional
    public Lote cadastrar(String codigo, String imunobiologico, String fabricante, LocalDate validade,
                          int quantidade, long camaraId) {
        var agora = OffsetDateTime.now(relogio);
        var lote = Lote.novo(codigo, imunobiologico, fabricante, validade, quantidade, camaraId,
                LocalDate.now(relogio), agora);

        // O bloqueio da câmara serializa as alocações concorrentes: a segunda espera a primeira gravar o lote e só
        // então soma a ocupação, sem ultrapassar a capacidade.
        var camara = camaras.buscarPorIdParaAlteracao(camaraId)
                .orElseThrow(() -> new RecursoNaoEncontrado("Câmara", camaraId));
        if (lotes.existePorCodigo(lote.getCodigo())) {
            throw new CodigoDuplicado("lote", lote.getCodigo());
        }
        camara.verificarAlocacao(lotes.ocupacaoDaCamara(camaraId), quantidade);
        return lotes.salvar(lote);
    }
}

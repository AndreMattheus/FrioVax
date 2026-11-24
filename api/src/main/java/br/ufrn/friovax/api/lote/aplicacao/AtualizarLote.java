package br.ufrn.friovax.api.lote.aplicacao;

import br.ufrn.friovax.api.camara.dominio.CamaraRepository;
import br.ufrn.friovax.api.compartilhado.dominio.RecursoNaoEncontrado;
import br.ufrn.friovax.api.lote.dominio.Lote;
import br.ufrn.friovax.api.lote.dominio.LoteRepository;
import br.ufrn.friovax.api.auditoria.dominio.OperacaoAuditada;
import br.ufrn.friovax.api.auditoria.dominio.RecursoAuditado;
import br.ufrn.friovax.api.compartilhado.aplicacao.MudancaConfirmada;
import br.ufrn.friovax.api.compartilhado.aplicacao.PublicadorDeMudancas;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;

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
        var antes = Campos.de(lote);
        lote.atualizar(imunobiologico, fabricante, validade, quantidade, camaraId,
                LocalDate.now(relogio), OffsetDateTime.now(relogio));

        var camara = camaras.buscarPorIdParaAlteracao(camaraId)
                .orElseThrow(() -> new RecursoNaoEncontrado("Câmara", camaraId));
        if (camaraId != antes.camaraId()) {
            camara.exigirSemAlerta("lotes movidos para ela");
        } else if (quantidade > antes.quantidade()) {
            camara.exigirSemAlerta("aumento de doses");
        }
        camara.verificarAlocacao(lotes.ocupacaoDaCamaraExcluindoLote(camaraId, id), quantidade);
        var salvo = lotes.salvar(lote);
        var depois = Campos.de(salvo);
        if (!depois.equals(antes)) {
            var descricao = "Lote " + salvo.getCodigo() + " atualizado: " + antes.diferencas(depois);
            publicador.publicar(MudancaConfirmada.auditada(RecursoAuditado.LOTE, salvo.getId(),
                    OperacaoAuditada.ATUALIZACAO, descricao, OffsetDateTime.now(relogio)));
        }
        return salvo;
    }

    /** O que um PUT pode mudar. A descrição cita textos só pelo nome do campo, sem copiar o conteúdo. */
    private record Campos(String imunobiologico, String fabricante, LocalDate validade, int quantidade, long camaraId) {
        static Campos de(Lote lote) {
            return new Campos(lote.getImunobiologico(), lote.getFabricante(), lote.getValidade(),
                    lote.getQuantidade(), lote.getCamaraId());
        }

        String diferencas(Campos depois) {
            var partes = new ArrayList<String>();
            if (!imunobiologico.equals(depois.imunobiologico)) {
                partes.add("imunobiológico");
            }
            if (!fabricante.equals(depois.fabricante)) {
                partes.add("fabricante");
            }
            if (!validade.equals(depois.validade)) {
                partes.add("validade " + validade + " → " + depois.validade);
            }
            if (quantidade != depois.quantidade) {
                partes.add("quantidade " + quantidade + " → " + depois.quantidade);
            }
            if (camaraId != depois.camaraId) {
                partes.add("câmara " + camaraId + " → " + depois.camaraId);
            }
            return String.join("; ", partes);
        }
    }
}

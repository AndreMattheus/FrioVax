package br.ufrn.friovax.api.camara.aplicacao;

import br.ufrn.friovax.api.auditoria.dominio.ContextoDaOperacao;
import br.ufrn.friovax.api.auditoria.dominio.OperacaoAuditada;
import br.ufrn.friovax.api.auditoria.dominio.RecursoAuditado;
import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.CamaraRepository;
import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import br.ufrn.friovax.api.compartilhado.aplicacao.MudancaConfirmada;
import br.ufrn.friovax.api.compartilhado.aplicacao.PublicadorDeMudancas;
import br.ufrn.friovax.api.compartilhado.dominio.RecursoNaoEncontrado;
import br.ufrn.friovax.api.lote.dominio.LoteRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.UUID;

@ApplicationScoped
public class AtualizarCamara {
    private final CamaraRepository camaras;
    private final LoteRepository lotes;
    private final Clock relogio;
    private final PublicadorDeMudancas publicador;
    private final ContextoDaOperacao contexto;

    @Inject
    public AtualizarCamara(CamaraRepository camaras, LoteRepository lotes, Clock relogio,
                           PublicadorDeMudancas publicador, ContextoDaOperacao contexto) {
        this.camaras = camaras;
        this.lotes = lotes;
        this.relogio = relogio;
        this.publicador = publicador;
        this.contexto = contexto;
    }

    public AtualizarCamara(CamaraRepository camaras, LoteRepository lotes, Clock relogio,
                           PublicadorDeMudancas publicador) {
        this(camaras, lotes, relogio, publicador, ContextoDaOperacao.doSistema());
    }

    public AtualizarCamara(CamaraRepository camaras, LoteRepository lotes, Clock relogio) {
        this(camaras, lotes, relogio, PublicadorDeMudancas.nenhum());
    }

    @Transactional
    public ConsultarCamara.Resultado atualizar(long id, AtualizarCamaraDTO dados) {
        Camara camara = camaras.buscarPorIdParaAlteracao(id)
            .orElseThrow(() -> new RecursoNaoEncontrado("Câmara", id));
        camara.exigirVersaoParaFaixa(dados.versaoEsperada(), dados.temperaturaMinima(), dados.temperaturaMaxima());

        long ocupacao = lotes.ocupacaoDaCamara(id);
        boolean possuiLotesAtivos = lotes.existeLoteAtivoNaCamara(id);

        var agora = OffsetDateTime.now(relogio);
        var politicaAnterior = camara.getPolitica();
        var antes = Campos.de(camara);
        UUID autorId = contexto.ator().id();

        camara.atualizar(dados.nome(), dados.unidade(), dados.capacidade(),
            dados.temperaturaMinima(), dados.temperaturaMaxima(), dados.estado(),
            ocupacao, possuiLotesAtivos, autorId, agora);
        camaras.salvar(camara);

        var depois = Campos.de(camara);
        if (!depois.equals(antes)) {
            publicador.publicar(MudancaConfirmada.auditada(RecursoAuditado.CAMARA, id, OperacaoAuditada.ATUALIZACAO,
                "Câmara " + camara.getCodigo() + " atualizada: " + antes.diferencas(depois), autorId, agora));
        }
        if (camara.getPolitica() != politicaAnterior) {
            publicador.publicar(MudancaDePolitica.aviso(camara, politicaAnterior, autorId, agora));
        }
        return new ConsultarCamara.Resultado(camara, ocupacao);
    }

    private record Campos(String nome, String unidade, int capacidade, EstadoCamara estado) {
        static Campos de(Camara camara) {
            return new Campos(camara.getNome(), camara.getUnidade(), camara.getCapacidade(), camara.getEstado());
        }

        String diferencas(Campos depois) {
            var partes = new ArrayList<String>();
            if (!nome.equals(depois.nome)) {
                partes.add("nome");
            }
            if (!unidade.equals(depois.unidade)) {
                partes.add("unidade");
            }
            if (capacidade != depois.capacidade) {
                partes.add("capacidade " + capacidade + " → " + depois.capacidade);
            }
            if (estado != depois.estado) {
                partes.add("estado " + estado + " → " + depois.estado);
            }
            return String.join("; ", partes);
        }
    }
}

package br.ufrn.friovax.api.camara.aplicacao;

import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import br.ufrn.friovax.api.camara.dominio.CamaraRepository;
import br.ufrn.friovax.api.auditoria.dominio.OperacaoAuditada;
import br.ufrn.friovax.api.auditoria.dominio.RecursoAuditado;
import br.ufrn.friovax.api.compartilhado.aplicacao.MudancaConfirmada;
import br.ufrn.friovax.api.compartilhado.aplicacao.PublicadorDeMudancas;
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
    private final PublicadorDeMudancas publicador;

    @Inject
    public AtualizarCamara(CamaraRepository camaras, LoteRepository lotes, Clock relogio,
                           PublicadorDeMudancas publicador) {
        this.camaras = camaras;
        this.lotes = lotes;
        this.relogio = relogio;
        this.publicador = publicador;
    }

    public AtualizarCamara(CamaraRepository camaras, LoteRepository lotes, Clock relogio) {
        this(camaras, lotes, relogio, PublicadorDeMudancas.nenhum());
    }

    @Transactional
    public ConsultarCamara.Resultado atualizar(long id, AtualizarCamaraDTO dados) {
        Camara camara = camaras.buscarPorIdParaAlteracao(id)
            .orElseThrow(() -> new RecursoNaoEncontrado("Câmara", id));

        long ocupacao = lotes.ocupacaoDaCamara(id);
        boolean possuiLotesAtivos = lotes.existeLoteAtivoNaCamara(id);

        var agora = OffsetDateTime.now(relogio);
        int versaoAnterior = camara.getPolitica().getVersao();
        var antes = Campos.de(camara);

        // Autor ainda nulo. A rota já exige ADMINISTRADOR (#106), mas repassar o usuário autenticado até aqui
        // fica para a auditoria (US10): a versão da política referencia `usuarios(id)`, e o autor precisa ser um
        // usuário cadastrado.
        camara.atualizar(dados.nome(), dados.unidade(), dados.capacidade(),
            dados.temperaturaMinima(), dados.temperaturaMaxima(), dados.estado(),
            ocupacao, possuiLotesAtivos, null, agora);
        camaras.salvar(camara);

        if (!Campos.de(camara).equals(antes)) {
            publicador.publicar(MudancaConfirmada.auditada(RecursoAuditado.CAMARA, id, OperacaoAuditada.ATUALIZACAO,
                descricao(camara, versaoAnterior), agora));
        }
        return new ConsultarCamara.Resultado(camara, ocupacao);
    }

    private record Campos(String nome, String unidade, int capacidade, EstadoCamara estado, int versaoDaPolitica) {
        static Campos de(Camara camara) {
            return new Campos(camara.getNome(), camara.getUnidade(), camara.getCapacidade(), camara.getEstado(),
                camara.getPolitica().getVersao());
        }
    }

    private static String descricao(Camara camara, int versaoAnterior) {
        int versaoAtual = camara.getPolitica().getVersao();
        if (versaoAtual == versaoAnterior) {
            return "Câmara " + camara.getCodigo() + " atualizada; política térmica mantida na versão " + versaoAtual;
        }
        return "Câmara " + camara.getCodigo() + " atualizada; política térmica passou da versão " + versaoAnterior
            + " para a " + versaoAtual;
    }
}

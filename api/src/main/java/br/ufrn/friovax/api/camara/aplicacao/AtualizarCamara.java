package br.ufrn.friovax.api.camara.aplicacao;

import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.CamaraRepository;
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

        // Autor ainda nulo: as rotas de câmara não exigem login. Quando a autorização por perfil proteger a rota,
        // o usuário autenticado passa a ser informado aqui e registrado na nova versão da política.
        camara.atualizar(dados.nome(), dados.unidade(), dados.capacidade(),
            dados.temperaturaMinima(), dados.temperaturaMaxima(), dados.estado(),
            ocupacao, possuiLotesAtivos, null, agora);
        camaras.salvar(camara);

        publicador.publicar(new MudancaConfirmada("camara", id, descricao(camara, versaoAnterior), null, agora));
        return new ConsultarCamara.Resultado(camara, ocupacao);
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

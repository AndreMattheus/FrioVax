package br.ufrn.friovax.api.telemetria.aplicacao;

import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.CamaraRepository;
import br.ufrn.friovax.api.compartilhado.dominio.RecursoNaoEncontrado;
import br.ufrn.friovax.api.telemetria.dominio.Leitura;
import br.ufrn.friovax.api.telemetria.dominio.LeituraConflitante;
import br.ufrn.friovax.api.telemetria.dominio.LeituraDaAnalise;
import br.ufrn.friovax.api.telemetria.dominio.LeituraForaDeOrdem;
import br.ufrn.friovax.api.telemetria.dominio.LeituraRepository;
import br.ufrn.friovax.api.telemetria.dominio.NovaLeitura;
import br.ufrn.friovax.api.telemetria.dominio.Ocorrencia;
import br.ufrn.friovax.api.telemetria.dominio.OcorrenciaRepository;
import br.ufrn.friovax.api.telemetria.dominio.PoliticaDaAnalise;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;

import java.util.Optional;

/**
 * Transações curtas da recepção de uma leitura (ADR 0003, seção 5). Cada método abre a própria transação, bloqueia a
 * câmara e a libera no fim; a chamada ao Go acontece entre eles, em {@link ReceberLeitura}, sem lock nem transação.
 */
@ApplicationScoped
public class EtapasDaRecepcao {

    private final CamaraRepository camaras;
    private final LeituraRepository leituras;
    private final OcorrenciaRepository ocorrencias;

    @Inject
    public EtapasDaRecepcao(CamaraRepository camaras, LeituraRepository leituras, OcorrenciaRepository ocorrencias) {
        this.camaras = camaras;
        this.leituras = leituras;
        this.ocorrencias = ocorrencias;
    }

    /**
     * Passo (a): com a câmara bloqueada, confere idempotência, elegibilidade e ordem e monta o snapshot. Não grava
     * nada.
     *
     * @throws RecursoNaoEncontrado se a câmara não existir
     * @throws LeituraConflitante   se a chave já foi aceita com outro conteúdo
     * @throws br.ufrn.friovax.api.compartilhado.dominio.EstadoIncompativel se a câmara estiver inativa ou fora de
     *                              {@code OPERACIONAL}
     * @throws LeituraForaDeOrdem   se a medição não for posterior à última leitura aceita
     */
    @Transactional(TxType.REQUIRES_NEW)
    public Preparacao preparar(NovaLeitura nova) {
        var camara = bloquear(nova.camaraId());
        var repetida = jaAceita(nova);
        if (repetida.isPresent()) {
            return new Preparacao.Repetida(repetida.get());
        }
        camara.exigirAptaParaLeituras();
        var ultima = leituras.ultimaDaCamara(camara.getId());
        if (ultima.isPresent() && !nova.medidaEm().isAfter(ultima.get().getInstante())) {
            throw new LeituraForaDeOrdem(camara.getId());
        }
        return new Preparacao.Pronta(snapshot(camara, ultima));
    }

    private Camara bloquear(long camaraId) {
        return camaras.buscarPorIdParaAlteracao(camaraId)
                .orElseThrow(() -> new RecursoNaoEncontrado("Câmara", camaraId));
    }

    /** A leitura já aceita com a mesma chave, se o conteúdo for equivalente; conteúdo diferente é conflito. */
    private Optional<Leitura> jaAceita(NovaLeitura nova) {
        var gravada = leituras.buscarPorChave(nova.camaraId(), nova.idExterno());
        if (gravada.isPresent() && !nova.equivaleA(gravada.get())) {
            throw new LeituraConflitante(nova.camaraId(), nova.idExterno());
        }
        return gravada;
    }

    private SnapshotDaCamara snapshot(Camara camara, Optional<Leitura> ultima) {
        var politica = PoliticaDaAnalise.de(camara.getPolitica());
        var inicioDoEpisodio = ocorrencias.ultimaResolvidaDaCamara(camara.getId())
                .flatMap(Ocorrencia::getLeituraResolucaoId)
                .orElse(null);
        var contexto = leituras.contextoDaJanela(camara.getId(), politica.versao(), politica.limiar() - 1,
                        inicioDoEpisodio).stream()
                .map(LeituraDaAnalise::de)
                .toList();
        return new SnapshotDaCamara(camara.getId(), politica, ultima.map(Leitura::getId).orElse(null),
                inicioDoEpisodio, contexto);
    }
}

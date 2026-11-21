package br.ufrn.friovax.api.telemetria.aplicacao;

import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.CamaraRepository;
import br.ufrn.friovax.api.compartilhado.aplicacao.MudancaConfirmada;
import br.ufrn.friovax.api.compartilhado.aplicacao.PublicadorDeMudancas;
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
import br.ufrn.friovax.api.telemetria.dominio.ResultadoTermico;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
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
    private final Clock relogio;
    private final PublicadorDeMudancas publicador;

    @Inject
    public EtapasDaRecepcao(CamaraRepository camaras, LeituraRepository leituras, OcorrenciaRepository ocorrencias,
                            Clock relogio, PublicadorDeMudancas publicador) {
        this.camaras = camaras;
        this.leituras = leituras;
        this.ocorrencias = ocorrencias;
        this.relogio = relogio;
        this.publicador = publicador;
    }

    public EtapasDaRecepcao(CamaraRepository camaras, LeituraRepository leituras, OcorrenciaRepository ocorrencias,
                            Clock relogio) {
        this(camaras, leituras, ocorrencias, relogio, PublicadorDeMudancas.nenhum());
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
        return new Preparacao.Pronta(snapshot(camara, ultima, true));
    }

    /**
     * Passo (c): com a câmara bloqueada de novo, revalida chave, elegibilidade e snapshot antes de gravar. Só grava o
     * resultado se a câmara ainda estiver no ponto analisado; caso contrário, devolve
     * {@link Confirmacao.SnapshotAlterado} sem alterar nada.
     *
     * @param analisado snapshot enviado ao Go no passo (a)
     * @param resultado resultado calculado pelo Go para a candidata nesse snapshot
     */
    @Transactional(TxType.REQUIRES_NEW)
    public Confirmacao confirmar(NovaLeitura nova, SnapshotDaCamara analisado, ResultadoTermico resultado) {
        Objects.requireNonNull(analisado, "analisado");
        Objects.requireNonNull(resultado, "resultado");
        var camara = bloquear(nova.camaraId());
        var repetida = jaAceita(nova);
        if (repetida.isPresent()) {
            return new Confirmacao.Repetida(repetida.get());
        }
        camara.exigirAptaParaLeituras();
        // O contexto não precisa ser relido: leituras só são inseridas, e qualquer leitura nova muda a última.
        var atual = snapshot(camara, leituras.ultimaDaCamara(camara.getId()), false);
        if (!atual.mesmoPontoQue(analisado)) {
            return new Confirmacao.SnapshotAlterado();
        }
        var agora = OffsetDateTime.now(relogio);
        var aceita = leituras.salvar(nova.aceitar(analisado.politica().versao(), resultado, agora));
        publicador.publicar(new MudancaConfirmada("leitura", aceita.getId(), "Recepção de leitura", null, agora));
        return new Confirmacao.Aceita(aceita);
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

    private SnapshotDaCamara snapshot(Camara camara, Optional<Leitura> ultima, boolean comContexto) {
        var politica = PoliticaDaAnalise.de(camara.getPolitica());
        var inicioDoEpisodio = ocorrencias.ultimaResolvidaDaCamara(camara.getId())
                .flatMap(Ocorrencia::getLeituraResolucaoId)
                .orElse(null);
        var contexto = !comContexto ? List.<LeituraDaAnalise>of()
                : leituras.contextoDaJanela(camara.getId(), politica.versao(), politica.limiar() - 1,
                                inicioDoEpisodio).stream()
                        .map(LeituraDaAnalise::de)
                        .toList();
        return new SnapshotDaCamara(camara.getId(), politica, ultima.map(Leitura::getId).orElse(null),
                inicioDoEpisodio, contexto);
    }
}

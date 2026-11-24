package br.ufrn.friovax.api.telemetria.aplicacao;

import br.ufrn.friovax.api.auditoria.dominio.OperacaoAuditada;
import br.ufrn.friovax.api.auditoria.dominio.RecursoAuditado;
import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.CamaraRepository;
import br.ufrn.friovax.api.compartilhado.aplicacao.MudancaConfirmada;
import br.ufrn.friovax.api.compartilhado.aplicacao.PublicadorDeMudancas;
import br.ufrn.friovax.api.compartilhado.dominio.RecursoNaoEncontrado;
import br.ufrn.friovax.api.lote.dominio.EstadoLote;
import br.ufrn.friovax.api.lote.dominio.LoteRepository;
import br.ufrn.friovax.api.telemetria.dominio.ClassificacaoTermica;
import br.ufrn.friovax.api.telemetria.dominio.Leitura;
import br.ufrn.friovax.api.telemetria.dominio.LeituraConflitante;
import br.ufrn.friovax.api.telemetria.dominio.LeituraDaAnalise;
import br.ufrn.friovax.api.telemetria.dominio.LeituraForaDeOrdem;
import br.ufrn.friovax.api.telemetria.dominio.LeituraRepository;
import br.ufrn.friovax.api.telemetria.dominio.LoteAfetado;
import br.ufrn.friovax.api.telemetria.dominio.NovaLeitura;
import br.ufrn.friovax.api.telemetria.dominio.Ocorrencia;
import br.ufrn.friovax.api.telemetria.dominio.OcorrenciaRepository;
import br.ufrn.friovax.api.telemetria.dominio.PoliticaDaAnalise;
import br.ufrn.friovax.api.telemetria.dominio.ResultadoDaCandidata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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
    private final LoteRepository lotes;
    private final Clock relogio;
    private final PublicadorDeMudancas publicador;

    @Inject
    public EtapasDaRecepcao(CamaraRepository camaras, LeituraRepository leituras, OcorrenciaRepository ocorrencias,
                            LoteRepository lotes, Clock relogio, PublicadorDeMudancas publicador) {
        this.camaras = camaras;
        this.leituras = leituras;
        this.ocorrencias = ocorrencias;
        this.lotes = lotes;
        this.relogio = relogio;
        this.publicador = publicador;
    }

    public EtapasDaRecepcao(CamaraRepository camaras, LeituraRepository leituras, OcorrenciaRepository ocorrencias,
                            LoteRepository lotes, Clock relogio) {
        this(camaras, leituras, ocorrencias, lotes, relogio, PublicadorDeMudancas.nenhum());
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
            return new Preparacao.Repetida(repetida.get(), ocorrenciaAbertaPor(repetida.get()));
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
     * <p>Na candidata que atinge o limiar sem ocorrência aberta, abre a ocorrência com as evidências e a fotografia
     * dos lotes, põe a câmara {@code EM_ALERTA} e registra a auditoria, tudo nesta mesma transação (S2-11, ADR 0003,
     * seção 4): qualquer falha desfaz a leitura junto. Com ocorrência já aberta, o sinal não abre outra. Os avisos de
     * mudança só saem depois do commit.
     *
     * @param analisado snapshot enviado ao Go no passo (a)
     * @param candidata resultado e evidências calculados pelo Go para a candidata nesse snapshot
     */
    @Transactional(TxType.REQUIRES_NEW)
    public Confirmacao confirmar(NovaLeitura nova, SnapshotDaCamara analisado, ResultadoDaCandidata candidata) {
        Objects.requireNonNull(analisado, "analisado");
        Objects.requireNonNull(candidata, "candidata");
        var camara = bloquear(nova.camaraId());
        var repetida = jaAceita(nova);
        if (repetida.isPresent()) {
            return new Confirmacao.Repetida(repetida.get(), ocorrenciaAbertaPor(repetida.get()));
        }
        camara.exigirAptaParaLeituras();
        // O contexto não precisa ser relido: leituras só são inseridas, e qualquer leitura nova muda a última.
        var atual = snapshot(camara, leituras.ultimaDaCamara(camara.getId()), false);
        if (!atual.mesmoPontoQue(analisado)) {
            return new Confirmacao.SnapshotAlterado();
        }
        var agora = OffsetDateTime.now(relogio);
        var resultado = candidata.resultado();
        var aceita = leituras.salvar(nova.aceitar(analisado.politica().versao(), resultado, agora));

        // A constraint de uma aberta por câmara é a última barreira; com o lock da câmara, esta leitura já a vê.
        var jaAberta = ocorrencias.abertaDaCamara(camara.getId());
        Ocorrencia aberta = null;
        if (resultado.limiarAtingido() && jaAberta.isEmpty()) {
            aberta = ocorrencias.salvar(Ocorrencia.abrir(camara.getId(), analisado.politica().versao(),
                    aceita.getId(), evidencias(camara.getId(), candidata, aceita), fotografiaDosLotes(camara.getId()),
                    false, agora));
        }
        if (camara.registrarResultadoTermico(resultado.classificacao() == ClassificacaoTermica.INSEGURA,
                jaAberta.isPresent() || aberta != null)) {
            camaras.salvar(camara);
        }

        publicador.publicar(new MudancaConfirmada("leitura", aceita.getId(), "Recepção de leitura", null, agora));
        if (aberta != null) {
            publicador.publicar(MudancaConfirmada.auditada(RecursoAuditado.OCORRENCIA, aberta.getId(),
                    OperacaoAuditada.ABERTURA_DE_OCORRENCIA, descricaoDaAbertura(camara, aceita, aberta), agora));
        }
        return new Confirmacao.Aceita(aceita, aberta == null ? null : aberta.getId());
    }

    /**
     * Ids das leituras da sequência, na ordem devolvida pelo Go (a própria candidata por último). As anteriores já
     * estão gravadas: o adaptador conferiu que são as últimas do contexto enviado.
     */
    private List<Long> evidencias(long camaraId, ResultadoDaCandidata candidata, Leitura aceita) {
        var ids = new ArrayList<Long>();
        for (String idExterno : candidata.evidencias()) {
            if (idExterno.equals(aceita.getIdExterno())) {
                ids.add(aceita.getId());
            } else {
                ids.add(leituras.buscarPorChave(camaraId, idExterno)
                        .orElseThrow(() -> new IllegalStateException("Evidência " + idExterno
                                + " ausente na câmara " + camaraId))
                        .getId());
            }
        }
        return ids;
    }

    /** Lotes com doses na câmara neste instante. Esgotados e descartados não têm doses em uso a proteger. */
    private List<LoteAfetado> fotografiaDosLotes(long camaraId) {
        return lotes.ativosDaCamara(camaraId).stream()
                .filter(lote -> lote.getEstado() == EstadoLote.DISPONIVEL && lote.getQuantidade() > 0)
                .map(lote -> new LoteAfetado(lote.getId(), lote.getCodigo(), lote.getImunobiologico(),
                        lote.getQuantidade()))
                .toList();
    }

    private static String descricaoDaAbertura(Camara camara, Leitura leitura, Ocorrencia ocorrencia) {
        int lotes = ocorrencia.getLotesAfetados().size();
        return "Ocorrência aberta na câmara " + camara.getCodigo() + " pela leitura " + leitura.getIdExterno()
                + " após " + ocorrencia.getEvidencias().size() + " leituras inseguras (política v"
                + ocorrencia.getPoliticaVersao() + "); " + lotes + (lotes == 1 ? " lote" : " lotes")
                + " na fotografia";
    }

    private Long ocorrenciaAbertaPor(Leitura leitura) {
        return ocorrencias.abertaPelaLeitura(leitura.getId()).map(Ocorrencia::getId).orElse(null);
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

package br.ufrn.friovax.api.telemetria.aplicacao;

import br.ufrn.friovax.api.auditoria.dominio.ContextoDaOperacao;
import br.ufrn.friovax.api.compartilhado.dominio.ValidacaoDeNegocio;
import br.ufrn.friovax.api.telemetria.aplicacao.EtapasDoLote.Grupo;
import br.ufrn.friovax.api.telemetria.aplicacao.ResultadoDoLote.Desfecho;
import br.ufrn.friovax.api.telemetria.dominio.AnaliseIndisponivel;
import br.ufrn.friovax.api.telemetria.dominio.AnaliseTermica;
import br.ufrn.friovax.api.telemetria.dominio.ChamadaDeAnaliseRecusada;
import br.ufrn.friovax.api.telemetria.dominio.LeituraConcorrente;
import br.ufrn.friovax.api.telemetria.dominio.NovaLeitura;
import br.ufrn.friovax.api.telemetria.dominio.ResultadoDaJanela;
import br.ufrn.friovax.api.telemetria.dominio.SolicitacaoDeAnalise;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import org.jboss.logging.Logger;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Coordena a única RPC do lote entre a preparação e os commits independentes das câmaras. */
@ApplicationScoped
public class ReceberLeiturasEmLote {

    private static final Logger LOG = Logger.getLogger(ReceberLeiturasEmLote.class);

    private final EtapasDoLote etapas;
    private final AnaliseTermica analise;
    private final ContextoDaOperacao contexto;
    private final Clock relogio;

    @Inject
    ReceberLeiturasEmLote(EtapasDoLote etapas, AnaliseTermica analise, ContextoDaOperacao contexto, Clock relogio) {
        this.etapas = etapas;
        this.analise = analise;
        this.contexto = contexto;
        this.relogio = relogio;
    }

    @Transactional(TxType.NEVER)
    public List<ResultadoDoLote> receber(List<NovaLeitura> entradas) {
        var porCamara = agrupar(entradas, OffsetDateTime.now(relogio));
        var correlacao = contexto.correlacaoId();
        var resultados = new HashMap<NovaLeitura, ResultadoDoLote>();
        var grupos = new ArrayList<Grupo>();
        porCamara.forEach((camaraId, leituras) -> {
            try {
                var preparo = etapas.preparar(camaraId, leituras);
                preparo.definidos().forEach(resposta -> resultados.put(resposta.entrada(), resposta));
                if (preparo.grupo() != null) {
                    grupos.add(preparo.grupo());
                }
            } catch (RuntimeException falha) {
                LOG.errorf("Lote %s: falha ao preparar a câmara %d (%s)", correlacao, camaraId,
                        falha.getClass().getSimpleName());
                rejeitar(resultados, leituras, Desfecho.FALHA_TRANSACIONAL);
            }
        });
        if (!grupos.isEmpty()) {
            var solicitacao = new SolicitacaoDeAnalise(correlacao,
                    grupos.stream().map(grupo -> grupo.snapshot().janela(grupo.candidatas())).toList());
            var resposta = analisar(solicitacao);
            for (int i = 0; i < grupos.size(); i++) {
                confirmar(grupos.get(i), resposta.get(i), correlacao, resultados);
            }
        }
        return entradas.stream().map(resultados::get).toList();
    }

    private List<ResultadoDaJanela> analisar(SolicitacaoDeAnalise solicitacao) {
        try {
            return analise.analisar(solicitacao).janelas();
        } catch (ChamadaDeAnaliseRecusada recusada) {
            throw new AnaliseIndisponivel(AnaliseIndisponivel.Motivo.CHAMADA_RECUSADA);
        }
    }

    private void confirmar(Grupo grupo, ResultadoDaJanela resultado, String correlacao,
                           Map<NovaLeitura, ResultadoDoLote> resultados) {
        if (resultado instanceof ResultadoDaJanela.Rejeitada rejeitada) {
            LOG.warnf("Lote %s: janela da câmara %d rejeitada pela análise (%s)", correlacao, rejeitada.camaraId(),
                    rejeitada.motivo());
            rejeitar(resultados, grupo.candidatas(), Desfecho.ANALISE_REJEITADA);
            return;
        }
        try {
            var analisada = (ResultadoDaJanela.Analisada) resultado;
            etapas.confirmar(grupo, analisada.candidatas())
                    .forEach(resposta -> resultados.put(resposta.entrada(), resposta));
        } catch (LeituraConcorrente conflito) {
            rejeitar(resultados, grupo.candidatas(), Desfecho.SNAPSHOT_ALTERADO);
        } catch (RuntimeException falha) {
            LOG.errorf("Lote %s: falha ao confirmar a câmara %d (%s)", correlacao, grupo.snapshot().camaraId(),
                    falha.getClass().getSimpleName());
            rejeitar(resultados, grupo.candidatas(), Desfecho.FALHA_TRANSACIONAL);
        }
    }

    private static Map<Long, List<NovaLeitura>> agrupar(List<NovaLeitura> entradas, OffsetDateTime agora) {
        if (entradas == null || entradas.isEmpty() || entradas.size() > SolicitacaoDeAnalise.CANDIDATAS_MAXIMAS) {
            throw new ValidacaoDeNegocio("leituras", "deve conter de 1 a "
                    + SolicitacaoDeAnalise.CANDIDATAS_MAXIMAS + " leituras");
        }
        var porCamara = new LinkedHashMap<Long, List<NovaLeitura>>();
        var idsPorCamara = new HashMap<Long, HashSet<String>>();
        for (int i = 0; i < entradas.size(); i++) {
            var leitura = entradas.get(i);
            if (leitura == null) {
                throw new ValidacaoDeNegocio("leituras[" + i + "]", "deve ser uma leitura");
            }
            try {
                leitura.validarToleranciaFutura(agora);
                if (!idsPorCamara.computeIfAbsent(leitura.camaraId(), id -> new HashSet<>()).add(leitura.idExterno())) {
                    throw new ValidacaoDeNegocio("idExterno", "a chave (camaraId, idExterno) já aparece neste lote");
                }
            } catch (ValidacaoDeNegocio invalida) {
                throw new ValidacaoDeNegocio("leituras[" + i + "]." + invalida.campo(), invalida.getMessage());
            }
            porCamara.computeIfAbsent(leitura.camaraId(), id -> new ArrayList<>()).add(leitura);
        }
        return porCamara;
    }

    private static void rejeitar(Map<NovaLeitura, ResultadoDoLote> resultados, List<NovaLeitura> candidatas,
                                 Desfecho motivo) {
        candidatas.forEach(candidata -> resultados.put(candidata, ResultadoDoLote.rejeitada(candidata, motivo)));
    }
}

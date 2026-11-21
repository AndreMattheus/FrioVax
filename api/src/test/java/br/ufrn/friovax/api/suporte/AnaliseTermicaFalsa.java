package br.ufrn.friovax.api.suporte;

import br.ufrn.friovax.api.telemetria.dominio.AnaliseTermica;
import br.ufrn.friovax.api.telemetria.dominio.JanelaDeAnalise;
import br.ufrn.friovax.api.telemetria.dominio.LeituraDaAnalise;
import br.ufrn.friovax.api.telemetria.dominio.ResultadoDaAnalise;
import br.ufrn.friovax.api.telemetria.dominio.ResultadoDaCandidata;
import br.ufrn.friovax.api.telemetria.dominio.ResultadoDaJanela;
import br.ufrn.friovax.api.telemetria.dominio.ResultadoTermico;
import br.ufrn.friovax.api.telemetria.dominio.SolicitacaoDeAnalise;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Porta de análise para testes sem gRPC: calcula como o contrato descreve (faixa inclusiva, sequência limitada a N e
 * evidências), registra cada solicitação e permite ao teste agir durante a "RPC" ou trocar a resposta.
 */
public class AnaliseTermicaFalsa implements AnaliseTermica {

    private final List<SolicitacaoDeAnalise> solicitacoes = new CopyOnWriteArrayList<>();
    private volatile Consumer<SolicitacaoDeAnalise> durante = solicitacao -> { };
    private volatile Function<SolicitacaoDeAnalise, ResultadoDaAnalise> resposta = AnaliseTermicaFalsa::referencia;

    /** Executa {@code acao} enquanto a análise está "em andamento", antes de responder. */
    public void durante(Consumer<SolicitacaoDeAnalise> acao) {
        this.durante = acao;
    }

    public void responder(Function<SolicitacaoDeAnalise, ResultadoDaAnalise> resposta) {
        this.resposta = resposta;
    }

    public void falhar(RuntimeException falha) {
        this.resposta = solicitacao -> {
            throw falha;
        };
    }

    public int chamadas() {
        return solicitacoes.size();
    }

    public List<SolicitacaoDeAnalise> solicitacoes() {
        return List.copyOf(solicitacoes);
    }

    public SolicitacaoDeAnalise ultima() {
        return solicitacoes.getLast();
    }

    @Override
    public ResultadoDaAnalise analisar(SolicitacaoDeAnalise solicitacao) {
        solicitacoes.add(solicitacao);
        durante.accept(solicitacao);
        return resposta.apply(solicitacao);
    }

    public static ResultadoDaAnalise referencia(SolicitacaoDeAnalise solicitacao) {
        return new ResultadoDaAnalise(solicitacao.correlacaoId(),
                solicitacao.janelas().stream().map(AnaliseTermicaFalsa::analisar).toList());
    }

    private static ResultadoDaJanela analisar(JanelaDeAnalise janela) {
        var politica = janela.politica();
        var inseguras = new ArrayDeque<String>();
        for (var leitura : janela.contexto()) {
            acumular(inseguras, leitura, janela);
        }
        var candidatas = new ArrayList<ResultadoDaCandidata>();
        for (var leitura : janela.candidatas()) {
            if (acumular(inseguras, leitura, janela)) {
                candidatas.add(new ResultadoDaCandidata(leitura.idExterno(),
                        ResultadoTermico.insegura(inseguras.size(), inseguras.size() == politica.limiar()),
                        List.copyOf(inseguras)));
            } else {
                candidatas.add(new ResultadoDaCandidata(leitura.idExterno(), ResultadoTermico.segura(), List.of()));
            }
        }
        return new ResultadoDaJanela.Analisada(janela.camaraId(), politica.versao(), candidatas);
    }

    private static boolean acumular(ArrayDeque<String> inseguras, LeituraDaAnalise leitura, JanelaDeAnalise janela) {
        var politica = janela.politica();
        boolean insegura = leitura.temperatura().compareTo(politica.temperaturaMinima()) < 0
                || leitura.temperatura().compareTo(politica.temperaturaMaxima()) > 0;
        if (!insegura) {
            inseguras.clear();
            return false;
        }
        inseguras.addLast(leitura.idExterno());
        if (inseguras.size() > politica.limiar()) {
            inseguras.removeFirst();
        }
        return true;
    }
}

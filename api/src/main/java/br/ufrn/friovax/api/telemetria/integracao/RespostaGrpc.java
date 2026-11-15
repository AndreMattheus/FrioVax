package br.ufrn.friovax.api.telemetria.integracao;

import br.ufrn.friovax.api.telemetria.dominio.ClassificacaoTermica;
import br.ufrn.friovax.api.telemetria.dominio.FalhaDeIntegracao;
import br.ufrn.friovax.api.telemetria.dominio.JanelaDeAnalise;
import br.ufrn.friovax.api.telemetria.dominio.LeituraDaAnalise;
import br.ufrn.friovax.api.telemetria.dominio.ResultadoDaAnalise;
import br.ufrn.friovax.api.telemetria.dominio.ResultadoDaCandidata;
import br.ufrn.friovax.api.telemetria.dominio.ResultadoDaJanela;
import br.ufrn.friovax.api.telemetria.dominio.ResultadoDaJanela.MotivoDaRejeicao;
import br.ufrn.friovax.api.telemetria.dominio.ResultadoTermico;
import br.ufrn.friovax.api.telemetria.dominio.SolicitacaoDeAnalise;
import br.ufrn.friovax.telemetry.v1.AnalyzeReadingsResponse;
import br.ufrn.friovax.telemetry.v1.CandidateResult;
import br.ufrn.friovax.telemetry.v1.WindowAnalysis;
import br.ufrn.friovax.telemetry.v1.WindowError;
import br.ufrn.friovax.telemetry.v1.WindowResult;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Converte a resposta Protobuf no resultado do domínio, conferindo-a contra a solicitação enviada. Qualquer
 * incoerência (correlação, ordem, candidata ausente, enum desconhecido, sequência impossível) compromete a chamada
 * inteira e vira {@link FalhaDeIntegracao}: nenhum resultado parcial é aproveitado.
 */
final class RespostaGrpc {

    static final int DESCRICAO_MAXIMA = 500;

    private RespostaGrpc() {
    }

    static ResultadoDaAnalise converter(SolicitacaoDeAnalise solicitacao, AnalyzeReadingsResponse resposta) {
        if (!solicitacao.correlacaoId().equals(resposta.getCorrelationId())) {
            throw falha("a correlação da resposta difere da enviada");
        }
        if (resposta.getResultsCount() != solicitacao.janelas().size()) {
            throw falha("a resposta tem " + resposta.getResultsCount() + " resultados para "
                    + solicitacao.janelas().size() + " janelas");
        }
        var janelas = new ArrayList<ResultadoDaJanela>(solicitacao.janelas().size());
        for (int i = 0; i < solicitacao.janelas().size(); i++) {
            janelas.add(janela(solicitacao.janelas().get(i), resposta.getResults(i)));
        }
        return new ResultadoDaAnalise(resposta.getCorrelationId(), janelas);
    }

    private static ResultadoDaJanela janela(JanelaDeAnalise janela, WindowResult resultado) {
        if (resultado.getChamberId() != janela.camaraId()) {
            throw falha("resultado da câmara " + resultado.getChamberId() + " na posição da câmara "
                    + janela.camaraId());
        }
        return switch (resultado.getOutcomeCase()) {
            case ANALYSIS -> analisada(janela, resultado.getPolicyVersion(), resultado.getAnalysis());
            case ERROR -> rejeitada(janela, resultado.getPolicyVersion(), resultado.getError());
            case OUTCOME_NOT_SET -> throw falha("a câmara " + janela.camaraId() + " não tem análise nem erro");
        };
    }

    private static ResultadoDaJanela analisada(JanelaDeAnalise janela, long politicaVersao, WindowAnalysis analise) {
        if (politicaVersao != janela.politica().versao()) {
            throw falha("a câmara " + janela.camaraId() + " foi analisada com outra versão de política");
        }
        if (analise.getCandidatesCount() != janela.candidatas().size()) {
            throw falha("a câmara " + janela.camaraId() + " tem " + analise.getCandidatesCount()
                    + " resultados para " + janela.candidatas().size() + " candidatas");
        }
        var ordem = Stream.concat(janela.contexto().stream(), janela.candidatas().stream())
                .toList();
        var candidatas = new ArrayList<ResultadoDaCandidata>(janela.candidatas().size());
        for (int i = 0; i < janela.candidatas().size(); i++) {
            int posicao = janela.contexto().size() + i;
            candidatas.add(candidata(janela, janela.candidatas().get(i), analise.getCandidates(i), ordem, posicao));
        }
        return new ResultadoDaJanela.Analisada(janela.camaraId(), janela.politica().versao(), candidatas);
    }

    private static ResultadoDaCandidata candidata(JanelaDeAnalise janela, LeituraDaAnalise enviada,
                                                  CandidateResult resultado, List<LeituraDaAnalise> ordem, int posicao) {
        var camara = janela.camaraId();
        if (!enviada.idExterno().equals(resultado.getExternalId())) {
            throw falha("resultado fora de ordem na câmara " + camara);
        }
        int limiar = janela.politica().limiar();
        int sequencia = resultado.getConsecutiveUnsafeCount();
        if (sequencia > limiar) {
            throw falha("sequência acima do limiar na câmara " + camara);
        }
        List<String> evidencias = resultado.getEvidenceExternalIdsList();
        if (!evidenciasConsecutivas(evidencias, ordem, posicao, janela)) {
            throw falha("evidências fora da sequência insegura que termina na candidata, na câmara " + camara);
        }
        try {
            var termico = switch (resultado.getClassification()) {
                case READING_CLASSIFICATION_SAFE ->
                        new ResultadoTermico(ClassificacaoTermica.SEGURA, sequencia, resultado.getThresholdReached());
                case READING_CLASSIFICATION_UNSAFE -> {
                    if (resultado.getThresholdReached() != (sequencia == limiar)) {
                        throw falha("sinal de limiar incoerente com a sequência na câmara " + camara);
                    }
                    yield ResultadoTermico.insegura(sequencia, resultado.getThresholdReached());
                }
                case READING_CLASSIFICATION_UNSPECIFIED, UNRECOGNIZED ->
                        throw falha("classificação ausente ou desconhecida na câmara " + camara);
            };
            return new ResultadoDaCandidata(resultado.getExternalId(), termico, evidencias);
        } catch (IllegalArgumentException incoerente) {
            throw falha("resultado incoerente na câmara " + camara + " (" + semPonto(incoerente.getMessage()) + ")");
        }
    }

    /**
     * As evidências são todas as últimas inseguras consecutivas até a candidata, limitadas a N. Uma temperatura
     * dentro da faixa inclusiva interrompe a sequência, tanto no contexto quanto nas candidatas. A conferência usa
     * as temperaturas enviadas para também recusar classificações incoerentes ou uma sequência truncada.
     */
    private static boolean evidenciasConsecutivas(List<String> evidencias, List<LeituraDaAnalise> ordem,
                                                 int posicao, JanelaDeAnalise janela) {
        var politica = janela.politica();
        int inicio = posicao;
        while (inicio >= 0 && posicao - inicio < politica.limiar()) {
            var temperatura = ordem.get(inicio).temperatura();
            if (temperatura.compareTo(politica.temperaturaMinima()) >= 0
                    && temperatura.compareTo(politica.temperaturaMaxima()) <= 0) {
                break;
            }
            inicio--;
        }
        var esperadas = ordem.subList(inicio + 1, posicao + 1).stream().map(LeituraDaAnalise::idExterno).toList();
        return evidencias.equals(esperadas);
    }

    private static ResultadoDaJanela rejeitada(JanelaDeAnalise janela, long politicaVersao, WindowError erro) {
        if (politicaVersao != 0 && politicaVersao != janela.politica().versao()) {
            throw falha("a rejeição da câmara " + janela.camaraId() + " cita outra versão de política");
        }
        var motivo = switch (erro.getCode()) {
            case WINDOW_ERROR_CODE_INVALID_WINDOW -> MotivoDaRejeicao.JANELA_INVALIDA;
            case WINDOW_ERROR_CODE_INVALID_POLICY -> MotivoDaRejeicao.POLITICA_INVALIDA;
            case WINDOW_ERROR_CODE_CHAMBER_MISMATCH -> MotivoDaRejeicao.CAMARA_DIVERGENTE;
            case WINDOW_ERROR_CODE_POLICY_VERSION_MISMATCH -> MotivoDaRejeicao.VERSAO_DIVERGENTE;
            case WINDOW_ERROR_CODE_UNSPECIFIED, UNRECOGNIZED ->
                    throw falha("erro de janela sem código conhecido na câmara " + janela.camaraId());
        };
        return new ResultadoDaJanela.Rejeitada(janela.camaraId(), motivo, descricaoLimitada(erro.getDescription()));
    }

    private static String descricaoLimitada(String descricao) {
        var texto = descricao.strip();
        return texto.length() <= DESCRICAO_MAXIMA ? texto : texto.substring(0, DESCRICAO_MAXIMA);
    }

    private static String semPonto(String texto) {
        return texto.endsWith(".") ? texto.substring(0, texto.length() - 1) : texto;
    }

    private static FalhaDeIntegracao falha(String detalhe) {
        return new FalhaDeIntegracao("Resposta inválida do serviço de análise térmica: " + detalhe + ".");
    }
}

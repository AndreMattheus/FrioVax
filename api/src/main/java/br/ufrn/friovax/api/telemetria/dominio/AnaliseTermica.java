package br.ufrn.friovax.api.telemetria.dominio;

/**
 * Porta da análise térmica remota (ADR 0003, seções 1 e 7). O caso de uso envia, numa única chamada, uma janela por
 * câmara e recebe um resultado por janela, na ordem enviada. A implementação não persiste nada e não decide
 * ocorrências: isso é responsabilidade da orquestração Java.
 *
 * <p>Não há repetição automática: a chamada é feita uma vez, com o prazo configurado no adaptador.
 */
public interface AnaliseTermica {

    /**
     * Analisa as janelas da solicitação.
     *
     * @return um resultado por janela, na mesma ordem, cada um com todas as candidatas ou com a rejeição da janela
     * @throws AnaliseIndisponivel se o serviço estiver inacessível, o prazo esgotar ou a chamada for cancelada; nada
     *                             deve ser confirmado nesse caso
     * @throws FalhaDeIntegracao   se o serviço recusar a chamada inteira ou devolver uma resposta incoerente com o
     *                             contrato; não é erro do usuário
     */
    ResultadoDaAnalise analisar(SolicitacaoDeAnalise solicitacao);
}

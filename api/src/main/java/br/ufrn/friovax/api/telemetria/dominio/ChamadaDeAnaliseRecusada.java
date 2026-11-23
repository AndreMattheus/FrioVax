package br.ufrn.friovax.api.telemetria.dominio;

/** Falha sinalizada pelo status da RPC, antes de obter uma resposta de análise. */
public class ChamadaDeAnaliseRecusada extends FalhaDeIntegracao {

    public ChamadaDeAnaliseRecusada(String mensagem) {
        super(mensagem);
    }
}

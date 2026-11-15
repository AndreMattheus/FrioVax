package br.ufrn.friovax.api.telemetria.dominio;

/**
 * O serviço de análise recusou a chamada inteira ou respondeu fora do contrato interno. É falha entre os serviços,
 * nunca culpa de quem enviou a leitura (ADR 0003, seções 6 e 7). A mensagem descreve o problema sem payload, stack ou
 * credenciais.
 */
public class FalhaDeIntegracao extends RuntimeException {

    public FalhaDeIntegracao(String mensagem) {
        super(mensagem, null, false, false);
    }
}

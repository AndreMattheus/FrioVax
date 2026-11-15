package br.ufrn.friovax.api.suporte;

/**
 * Porta local sem servidor para os testes que simulam o Go ausente. Portas abaixo de 1024 não são sorteadas como
 * efêmeras: um {@code ServerSocket(0)} de outro teste nunca a recebe, e quem tenta conectar recebe conexão recusada.
 * A porta 1 (tcpmux) não tem serviço nas máquinas de desenvolvimento nem nos runners do CI.
 */
public final class PortaSemServico {

    private static final int PORTA = 1;

    private PortaSemServico() {
    }

    public static int porta() {
        return PORTA;
    }
}

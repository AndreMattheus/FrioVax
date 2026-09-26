package br.ufrn.friovax.api.compartilhado.dominio;

// erro lançado pela camada de aplicação quando uma referência (ex.: camaraId de um lote) aponta pra um registro que não existe.
// Mapeada para 404 / /problemas/recurso-nao-encontrado (docs/contrato-api.md) pelo RecursoNaoEncontradoExceptionMapper.
public class RecursoNaoEncontradoException extends RuntimeException {
    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}

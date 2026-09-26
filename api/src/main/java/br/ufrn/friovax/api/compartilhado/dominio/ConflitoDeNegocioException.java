package br.ufrn.friovax.api.compartilhado.dominio;

//erro lançado pela camada de aplicação quando uma operação é rejeitada por regra de negócio que não é sobre o formato do dado em si
// ou seja: código já existe, capacidade excedida, ou estado atual do recurso não permite a operação.
//se precisar adicionar outro tipo adiciona no enum TipoConflito
// Mapeada para 409 pelo ConflitoDeNegocioExceptionMapper, com type/title escolhidos por `tipo` (docs/contrato-api.md).
public class ConflitoDeNegocioException extends RuntimeException {

    private final TipoConflito tipo;

    public ConflitoDeNegocioException(TipoConflito tipo, String mensagem) {
        super(mensagem);
        this.tipo = tipo;
    }

    public TipoConflito tipo() {
        return tipo;
    }
}

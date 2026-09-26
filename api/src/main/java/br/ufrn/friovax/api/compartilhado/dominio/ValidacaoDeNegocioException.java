package br.ufrn.friovax.api.compartilhado.dominio;

import java.util.List;

// erro lançado pela camada de aplicação para regras de 422 que dependem de mais contexto do que um único DTO consegue validar sozinho (por isso não é coberta por Bean Validation)
// ex: uma regra que precisa checar outro registro no banco antes de decidir se o valor é válido.
// Mapeada para 422 / /problemas/validacao pelo ValidacaoDeNegocioExceptionMapper, mesmo type usado pelas violações de Bean Validation (o contrato trata as duas como a mesma categoria de erro).
public class ValidacaoDeNegocioException extends RuntimeException {

    private final List<CampoInvalido> campos;

    public ValidacaoDeNegocioException(String mensagem, List<CampoInvalido> campos) {
        super(mensagem);
        this.campos = campos;
    }

    public ValidacaoDeNegocioException(String campo, String mensagem) {
        this(mensagem, List.of(new CampoInvalido(campo, mensagem)));
    }

    public List<CampoInvalido> campos() {
        return campos;
    }

    public record CampoInvalido(String campo, String mensagem) {}
}

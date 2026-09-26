package br.ufrn.friovax.api.compartilhado.api;

import java.util.List;

//formato de erro da API, seguindo RFC 9457 (Problem Details)

public record ProblemDetails(
        String type,
        String title,
        int status,
        String detail,
        String instance,
        List<CampoErro> erros
) {

    public ProblemDetails(String type, String title, int status, String detail, String instance) {
        this(type, title, status, detail, instance, null);
    }

    public record CampoErro(String campo, String mensagem) {}
}

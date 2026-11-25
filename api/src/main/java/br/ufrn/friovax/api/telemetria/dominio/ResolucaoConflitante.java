package br.ufrn.friovax.api.telemetria.dominio;

public class ResolucaoConflitante extends ConflitoDeResolucao {

    public ResolucaoConflitante(long ocorrenciaId) {
        super(ocorrenciaId, "A ocorrência " + ocorrenciaId + " já foi resolvida com outra justificativa; "
                + "o tratamento registrado não é alterado.");
    }
}

package br.ufrn.friovax.api.telemetria.dominio;

public class ResolucaoNaoPermitida extends ConflitoDeResolucao {

    public ResolucaoNaoPermitida(long ocorrenciaId, String motivo) {
        super(ocorrenciaId, "A ocorrência " + ocorrenciaId + " ainda não pode ser resolvida: " + motivo);
    }
}

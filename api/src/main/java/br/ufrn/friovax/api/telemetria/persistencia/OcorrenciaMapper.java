package br.ufrn.friovax.api.telemetria.persistencia;

import br.ufrn.friovax.api.telemetria.dominio.LoteAfetado;
import br.ufrn.friovax.api.telemetria.dominio.Ocorrencia;

import java.util.List;

final class OcorrenciaMapper {
    private OcorrenciaMapper() {
    }

    static Ocorrencia paraDominio(OcorrenciaEntity e, List<Long> evidencias, List<LoteAfetado> lotes) {
        return Ocorrencia.reconstituir(e.id, e.camaraId, e.politicaVersao, e.leituraAberturaId, e.abertaEm,
                evidencias, lotes, e.resolvidaEm, e.resolvidaPor, e.justificativa, e.leituraResolucaoId);
    }

    static OcorrenciaEntity paraEntidade(Ocorrencia o) {
        var e = new OcorrenciaEntity();
        e.id = o.getId();
        e.camaraId = o.getCamaraId();
        e.politicaVersao = o.getPoliticaVersao();
        e.leituraAberturaId = o.getLeituraAberturaId();
        e.abertaEm = o.getAbertaEm();
        copiarResolucao(o, e);
        return e;
    }

    static void copiarResolucao(Ocorrencia o, OcorrenciaEntity e) {
        e.resolvidaEm = o.getResolvidaEm().orElse(null);
        e.resolvidaPor = o.getResolvidaPor().orElse(null);
        e.justificativa = o.getJustificativa().orElse(null);
        e.leituraResolucaoId = o.getLeituraResolucaoId().orElse(null);
    }
}

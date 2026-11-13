package br.ufrn.friovax.api.telemetria.persistencia;

import br.ufrn.friovax.api.telemetria.dominio.Leitura;
import br.ufrn.friovax.api.telemetria.dominio.ResultadoTermico;

final class LeituraMapper {
    private LeituraMapper() {
    }

    static Leitura paraDominio(LeituraEntity e) {
        return Leitura.reconstituir(e.id, e.camaraId, e.idExterno, e.instante, e.temperatura, e.origem,
                e.politicaVersao, new ResultadoTermico(e.classificacao, e.sequencia, e.limiarAtingido), e.aceitaEm);
    }

    static LeituraEntity paraEntidade(Leitura l) {
        var e = new LeituraEntity();
        e.id = l.getId();
        e.camaraId = l.getCamaraId();
        e.idExterno = l.getIdExterno();
        e.instante = l.getInstante();
        e.temperatura = l.getTemperatura();
        e.origem = l.getOrigem();
        e.politicaVersao = l.getPoliticaVersao();
        e.classificacao = l.getResultado().classificacao();
        e.sequencia = l.getResultado().sequencia();
        e.limiarAtingido = l.getResultado().limiarAtingido();
        e.aceitaEm = l.getAceitaEm();
        return e;
    }
}

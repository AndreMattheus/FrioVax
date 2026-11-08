package br.ufrn.friovax.api.camara.persistencia;

import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.PoliticaTermica;

final class CamaraMapper {
    private CamaraMapper() {
    }

    static Camara paraDominio(CamaraEntity e, PoliticaTermicaEntity politicaVigente) {
        return Camara.reconstituir(e.id, e.codigo, e.nome, e.unidade, e.capacidade,
            politicaParaDominio(politicaVigente), e.estadoTermico, e.estado, e.ativo, e.criadoEm, e.atualizadoEm);
    }

    static CamaraEntity paraEntidade(Camara c) {
        var e = new CamaraEntity();
        e.id = c.getId();
        e.codigo = c.getCodigo();
        e.nome = c.getNome();
        e.unidade = c.getUnidade();
        e.capacidade = c.getCapacidade();
        e.estadoTermico = c.getEstadoTermico();
        e.estado = c.getEstado();
        e.ativo = c.isAtivo();
        e.criadoEm = c.getCriadoEm();
        e.atualizadoEm = c.getAtualizadoEm();
        return e;
    }

    static PoliticaTermica politicaParaDominio(PoliticaTermicaEntity e) {
        return PoliticaTermica.reconstituir(e.versao, e.temperaturaMinima, e.temperaturaMaxima, e.limiar,
            e.autorId, e.criadaEm);
    }

    static PoliticaTermicaEntity politicaParaEntidade(long camaraId, PoliticaTermica p) {
        var e = new PoliticaTermicaEntity();
        e.camaraId = camaraId;
        e.versao = p.getVersao();
        e.temperaturaMinima = p.getTemperaturaMinima();
        e.temperaturaMaxima = p.getTemperaturaMaxima();
        e.limiar = p.getLimiar();
        e.autorId = p.getAutorId().orElse(null);
        e.criadaEm = p.getCriadaEm();
        return e;
    }
}

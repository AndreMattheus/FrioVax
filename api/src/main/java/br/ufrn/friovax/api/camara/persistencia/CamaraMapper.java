package br.ufrn.friovax.api.camara.persistencia;

import br.ufrn.friovax.api.camara.dominio.Camara;

public class CamaraMapper {
    public static Camara paraDominio(CamaraEntity e) {
        return Camara.reconstituir(e.id, e.codigo, e.nome, e.unidade, e.capacidade,
            e.temperaturaMinima, e.temperaturaMaxima, e.estado, e.ativo, e.criadoEm, e.atualizadoEm);
    }

    public static CamaraEntity paraEntidade(Camara c) {
        var e = new CamaraEntity();
        e.id = c.getId(); e.codigo = c.getCodigo(); e.nome = c.getNome(); e.unidade = c.getUnidade();
        e.capacidade = c.getCapacidade(); e.temperaturaMinima = c.getTemperaturaMinima();
        e.temperaturaMaxima = c.getTemperaturaMaxima(); e.estado = c.getEstado();
        e.ativo = c.isAtivo(); e.criadoEm = c.getCriadoEm(); e.atualizadoEm = c.getAtualizadoEm();
        return e;
    }
}
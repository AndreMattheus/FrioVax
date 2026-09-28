package br.ufrn.friovax.api.lote.persistencia;

import br.ufrn.friovax.api.lote.dominio.Lote;

final class LoteMapper {
    private LoteMapper() {
    }

    static Lote paraDominio(LoteEntity e) {
        return Lote.reconstituir(e.id, e.codigo, e.imunobiologico, e.fabricante, e.validade, e.quantidade,
                e.camaraId, e.estado, e.ativo, e.criadoEm, e.atualizadoEm);
    }

    static LoteEntity paraEntidade(Lote l) {
        var e = new LoteEntity();
        e.id = l.getId();
        e.codigo = l.getCodigo();
        e.imunobiologico = l.getImunobiologico();
        e.fabricante = l.getFabricante();
        e.validade = l.getValidade();
        e.quantidade = l.getQuantidade();
        e.camaraId = l.getCamaraId();
        e.estado = l.getEstado();
        e.ativo = l.isAtivo();
        e.criadoEm = l.getCriadoEm();
        e.atualizadoEm = l.getAtualizadoEm();
        return e;
    }
}

package br.ufrn.friovax.api.suporte;

import br.ufrn.friovax.api.compartilhado.dominio.CodigoDuplicado;
import br.ufrn.friovax.api.compartilhado.dominio.Pagina;
import br.ufrn.friovax.api.compartilhado.dominio.Paginacao;
import br.ufrn.friovax.api.lote.dominio.Lote;
import br.ufrn.friovax.api.lote.dominio.LoteFiltro;
import br.ufrn.friovax.api.lote.dominio.LoteRepository;

import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/**
 * {@link LoteRepository} em memória para testes sem banco. Guarda cópias, como o banco faria: alterações num objeto
 * só valem depois de {@link #salvar(Lote)}.
 */
public class LoteRepositoryEmMemoria implements LoteRepository {

    private final Map<Long, Lote> lotes = new TreeMap<>();
    private final AtomicLong sequencia = new AtomicLong();

    @Override
    public synchronized Lote salvar(Lote lote) {
        if (lote.getId() == null) {
            if (existePorCodigo(lote.getCodigo())) {
                throw new CodigoDuplicado("lote", lote.getCodigo());
            }
            lote.atribuirId(sequencia.incrementAndGet());
        } else if (!lotes.containsKey(lote.getId())) {
            throw new IllegalStateException("Lote " + lote.getId() + " não está gravado");
        }
        lotes.put(lote.getId(), copia(lote));
        return lote;
    }

    @Override
    public synchronized Optional<Lote> buscarPorId(long id) {
        return Optional.ofNullable(lotes.get(id)).map(LoteRepositoryEmMemoria::copia);
    }

    @Override
    public synchronized boolean existePorCodigo(String codigo) {
        return lotes.values().stream().anyMatch(lote -> lote.getCodigo().equals(codigo));
    }

    @Override
    public synchronized Pagina<Lote> listar(LoteFiltro filtro, Paginacao paginacao) {
        var filtrados = lotes.values().stream()
                .filter(atende(filtro))
                .map(LoteRepositoryEmMemoria::copia)
                .toList();
        return Paginador.paginar(filtrados, paginacao);
    }

    @Override
    public synchronized long ocupacaoDaCamara(long camaraId) {
        return somarAtivos(lote -> lote.getCamaraId() == camaraId);
    }

    @Override
    public synchronized long ocupacaoDaCamaraExcluindoLote(long camaraId, long loteId) {
        return somarAtivos(lote -> lote.getCamaraId() == camaraId && lote.getId() != loteId);
    }

    @Override
    public synchronized Map<Long, Long> ocupacaoPorCamara(Collection<Long> camaraIds) {
        var ocupacoes = new HashMap<Long, Long>();
        camaraIds.forEach(id -> ocupacoes.put(id, ocupacaoDaCamara(id)));
        return ocupacoes;
    }

    @Override
    public synchronized boolean existeLoteAtivoNaCamara(long camaraId) {
        return lotes.values().stream().anyMatch(lote -> lote.isAtivo() && lote.getCamaraId() == camaraId);
    }

    private long somarAtivos(Predicate<Lote> condicao) {
        return lotes.values().stream()
                .filter(Lote::isAtivo)
                .filter(condicao)
                .mapToLong(Lote::getQuantidade)
                .sum();
    }

    private static Predicate<Lote> atende(LoteFiltro filtro) {
        var termo = filtro.imunobiologico() == null ? null : filtro.imunobiologico().toLowerCase(Locale.ROOT);
        return lote -> lote.isAtivo() == filtro.ativo()
                && (termo == null || lote.getImunobiologico().toLowerCase(Locale.ROOT).contains(termo))
                && (filtro.validadeDe() == null || !lote.getValidade().isBefore(filtro.validadeDe()))
                && (filtro.validadeAte() == null || !lote.getValidade().isAfter(filtro.validadeAte()))
                && (filtro.camaraId() == null || lote.getCamaraId() == filtro.camaraId())
                && (filtro.estado() == null || lote.getEstado() == filtro.estado());
    }

    private static Lote copia(Lote lote) {
        return Lote.reconstituir(lote.getId(), lote.getCodigo(), lote.getImunobiologico(), lote.getFabricante(),
                lote.getValidade(), lote.getQuantidade(), lote.getCamaraId(), lote.getEstado(), lote.isAtivo(),
                lote.getCriadoEm(), lote.getAtualizadoEm());
    }
}

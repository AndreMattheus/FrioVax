package br.ufrn.friovax.api.lote.persistencia;

import br.ufrn.friovax.api.compartilhado.dominio.CodigoDuplicado;
import br.ufrn.friovax.api.compartilhado.dominio.Pagina;
import br.ufrn.friovax.api.compartilhado.dominio.Paginacao;
import br.ufrn.friovax.api.lote.dominio.Lote;
import br.ufrn.friovax.api.lote.dominio.LoteFiltro;
import br.ufrn.friovax.api.lote.dominio.LoteRepository;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceException;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;

@ApplicationScoped
public class LoteRepositoryPostgres implements LoteRepository, PanacheRepositoryBase<LoteEntity, Long> {

    private static final String SOMA_DOS_ATIVOS =
            "select coalesce(sum(l.quantidade), 0L) from LoteEntity l where l.ativo = true and l.camaraId = :camaraId";

    @Override
    public Lote salvar(Lote lote) {
        var entity = LoteMapper.paraEntidade(lote);
        if (entity.id != null && findByIdOptional(entity.id).isEmpty()) {
            throw new IllegalStateException("Lote " + entity.id + " não está gravado");
        }
        try {
            if (entity.id == null) {
                persist(entity);
                getEntityManager().flush(); // força o INSERT agora, pra pegar a violação aqui
                lote.atribuirId(entity.id);
            } else {
                getEntityManager().merge(entity);
                getEntityManager().flush();
            }
        } catch (PersistenceException e) {
            Throwable causa = e;
            while (causa != null) {
                if (causa instanceof ConstraintViolationException cve
                        && "uk_lotes_codigo".equals(cve.getConstraintName())) {
                    throw new CodigoDuplicado("lote", lote.getCodigo());
                }
                causa = causa.getCause();
            }
            throw e;
        }
        return lote;
    }

    @Override
    public Optional<Lote> buscarPorId(long id) {
        return findByIdOptional(id).map(LoteMapper::paraDominio);
    }

    @Override
    public Optional<Lote> buscarPorIdParaAlteracao(long id) {
        return findByIdOptional(id, LockModeType.PESSIMISTIC_WRITE)
                .map(LoteMapper::paraDominio);
    }

    @Override
    public boolean existePorCodigo(String codigo) {
        return count("codigo", codigo) > 0;
    }

    @Override
    public Pagina<Lote> listar(LoteFiltro filtro, Paginacao paginacao) {
        var condicoes = new StringBuilder("ativo = :ativo");
        Map<String, Object> params = new HashMap<>();
        params.put("ativo", filtro.ativo());

        if (filtro.imunobiologico() != null) {
            condicoes.append(" and lower(imunobiologico) like lower(:imunobiologico) escape '!'");
            params.put("imunobiologico", "%" + literalParaLike(filtro.imunobiologico()) + "%");
        }
        if (filtro.validadeDe() != null) {
            condicoes.append(" and validade >= :validadeDe");
            params.put("validadeDe", filtro.validadeDe());
        }
        if (filtro.validadeAte() != null) {
            condicoes.append(" and validade <= :validadeAte");
            params.put("validadeAte", filtro.validadeAte());
        }
        if (filtro.camaraId() != null) {
            condicoes.append(" and camaraId = :camaraId");
            params.put("camaraId", filtro.camaraId());
        }
        if (filtro.estado() != null) {
            condicoes.append(" and estado = :estado");
            params.put("estado", filtro.estado());
        }

        var query = find(condicoes.toString(), Sort.by("id"), params);

        // O Panache calcula o deslocamento em int (pagina * tamanho): acima disso estouraria e voltaria outra
        // página ou um deslocamento negativo. Não há registros tão longe, então a página é vazia.
        List<Lote> itens = paginacao.deslocamento() > Integer.MAX_VALUE
            ? List.of()
            : query.page(paginacao.pagina(), paginacao.tamanho()).list().stream()
                .map(LoteMapper::paraDominio)
                .toList();

        return Pagina.de(itens, paginacao, query.count());
    }

    @Override
    public long ocupacaoDaCamara(long camaraId) {
        return getEntityManager().createQuery(SOMA_DOS_ATIVOS, Long.class)
                .setParameter("camaraId", camaraId)
                .getSingleResult();
    }

    @Override
    public long ocupacaoDaCamaraExcluindoLote(long camaraId, long loteId) {
        return getEntityManager().createQuery(SOMA_DOS_ATIVOS + " and l.id <> :loteId", Long.class)
                .setParameter("camaraId", camaraId)
                .setParameter("loteId", loteId)
                .getSingleResult();
    }

    @Override
    public Map<Long, Long> ocupacaoPorCamara(Collection<Long> camaraIds) {
        var ocupacoes = new HashMap<Long, Long>();
        if (camaraIds.isEmpty()) {
            return ocupacoes;
        }
        camaraIds.forEach(id -> ocupacoes.put(id, 0L));
        getEntityManager().createQuery("""
                        select l.camaraId, sum(l.quantidade) from LoteEntity l
                        where l.ativo = true and l.camaraId in :camaraIds
                        group by l.camaraId""", Object[].class)
                .setParameter("camaraIds", camaraIds)
                .getResultList()
                .forEach(linha -> ocupacoes.put((Long) linha[0], (Long) linha[1]));
        return ocupacoes;
    }

    @Override
    public boolean existeLoteAtivoNaCamara(long camaraId) {
        return find("camaraId = ?1 and ativo = true", camaraId).firstResultOptional().isPresent();
    }

    /** Escapa os curingas do LIKE para que a busca seja um "contém" literal, como no contrato. */
    private static String literalParaLike(String termo) {
        return termo.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}

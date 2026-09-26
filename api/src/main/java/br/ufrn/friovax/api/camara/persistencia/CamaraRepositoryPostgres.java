package br.ufrn.friovax.api.camara.persistencia;

import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.CamaraFiltro;
import br.ufrn.friovax.api.camara.dominio.CamaraRepository;
import br.ufrn.friovax.api.compartilhado.dominio.CodigoDuplicado;
import br.ufrn.friovax.api.compartilhado.dominio.Pagina;
import br.ufrn.friovax.api.compartilhado.dominio.Paginacao;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;

@ApplicationScoped
public class CamaraRepositoryPostgres implements CamaraRepository, PanacheRepositoryBase<CamaraEntity, Long> {

    @Override
    public Camara salvar(Camara camara) {
        var entity = CamaraMapper.paraEntidade(camara);
        try {
            if (entity.id == null) {
                persist(entity);
                getEntityManager().flush(); // força o INSERT agora, pra pegar a violação aqui
                camara.atribuirId(entity.id);
            } else {
                getEntityManager().merge(entity);
                getEntityManager().flush();
            }
        } catch (PersistenceException e) {
            if (e.getCause() instanceof ConstraintViolationException) {
                throw new CodigoDuplicado("câmara", camara.getCodigo());
            }
            throw e;
        }
        return camara;
    }

    @Override
    public Optional<Camara> buscarPorId(long id) {
        return findByIdOptional(id).map(CamaraMapper::paraDominio);
    }

    @Override
    public Optional<Camara> buscarPorIdParaAlteracao(long id) {
        var entity = find("id", id)
            .withLock(LockModeType.PESSIMISTIC_WRITE)
            .firstResult();
        return Optional.ofNullable(entity).map(CamaraMapper::paraDominio);
    }

    @Override
    public boolean existePorCodigo(String codigo) {
        return count("codigo", codigo) > 0;
    }

    @Override
    public Pagina<Camara> listar(CamaraFiltro filtro, Paginacao paginacao) {
        var condicoes = new StringBuilder("ativo = :ativo");
        Map<String, Object> params = new HashMap<>();
        params.put("ativo", filtro.ativo());

        if (filtro.unidade() != null) {
            condicoes.append(" and lower(unidade) = lower(:unidade)");
            params.put("unidade", filtro.unidade());
        }
        if (filtro.estado() != null) {
            condicoes.append(" and estado = :estado");
            params.put("estado", filtro.estado());
        }

        var query = find(condicoes.toString(), Sort.by("id"), params)
            .page(paginacao.pagina(), paginacao.tamanho());

        List<Camara> itens = query.list().stream()
            .map(CamaraMapper::paraDominio)
            .toList();

        return Pagina.de(itens, paginacao, query.count());
    }
}
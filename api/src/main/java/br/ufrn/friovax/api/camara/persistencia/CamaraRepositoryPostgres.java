package br.ufrn.friovax.api.camara.persistencia;

import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.CamaraFiltro;
import br.ufrn.friovax.api.camara.dominio.CamaraRepository;
import br.ufrn.friovax.api.camara.dominio.PoliticaTermica;
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
import java.util.function.Function;
import java.util.stream.Collectors;

import org.hibernate.exception.ConstraintViolationException;

@ApplicationScoped
public class CamaraRepositoryPostgres implements CamaraRepository, PanacheRepositoryBase<CamaraEntity, Long> {

    @Override
    public Camara salvar(Camara camara) {
        var entity = CamaraMapper.paraEntidade(camara);
        if (entity.id != null && findByIdOptional(entity.id).isEmpty()) {
            throw new IllegalStateException("Câmara " + entity.id + " não está gravada");
        }
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
            Throwable causa = e;
            while (causa != null) {
                if (causa instanceof ConstraintViolationException cve
                    && "uk_camaras_codigo".equals(cve.getConstraintName())) {
                    throw new CodigoDuplicado("câmara", camara.getCodigo());
                }
                causa = causa.getCause();
            }
            throw e;
        }
        gravarPoliticaSeNova(camara.getId(), camara.getPolitica());
        return camara;
    }

    @Override
    public List<PoliticaTermica> historicoDePoliticas(long camaraId) {
        return getEntityManager()
            .createQuery("from PoliticaTermicaEntity where camaraId = :camara order by versao",
                PoliticaTermicaEntity.class)
            .setParameter("camara", camaraId)
            .getResultStream()
            .map(CamaraMapper::politicaParaDominio)
            .toList();
    }

    private void gravarPoliticaSeNova(long camaraId, PoliticaTermica politica) {
        long gravadas = getEntityManager()
            .createQuery("select count(p) from PoliticaTermicaEntity p"
                + " where p.camaraId = :camara and p.versao = :versao", Long.class)
            .setParameter("camara", camaraId)
            .setParameter("versao", politica.getVersao())
            .getSingleResult();
        if (gravadas == 0) {
            getEntityManager().persist(CamaraMapper.politicaParaEntidade(camaraId, politica));
            getEntityManager().flush();
        }
    }

    private Camara comPoliticaVigente(CamaraEntity camara) {
        return CamaraMapper.paraDominio(camara, politicasVigentes(List.of(camara.id)).get(camara.id));
    }

    private Map<Long, PoliticaTermicaEntity> politicasVigentes(List<Long> camaraIds) {
        if (camaraIds.isEmpty()) {
            return Map.of();
        }
        return getEntityManager()
            .createQuery("from PoliticaTermicaEntity p where p.camaraId in :camaras and p.versao ="
                    + " (select max(v.versao) from PoliticaTermicaEntity v where v.camaraId = p.camaraId)",
                PoliticaTermicaEntity.class)
            .setParameter("camaras", camaraIds)
            .getResultStream()
            .collect(Collectors.toMap(p -> p.camaraId, Function.identity()));
    }

    @Override
    public Optional<Camara> buscarPorId(long id) {
        return findByIdOptional(id).map(this::comPoliticaVigente);
    }

    @Override
    public Optional<Camara> buscarPorIdParaAlteracao(long id) {
        return findByIdOptional(id, LockModeType.PESSIMISTIC_WRITE)
            .map(this::comPoliticaVigente);
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

        var query = find(condicoes.toString(), Sort.by("id"), params);

        // O Panache calcula o deslocamento em int (pagina * tamanho): acima disso estouraria e voltaria outra
        // página ou um deslocamento negativo. Não há registros tão longe, então a página é vazia.
        List<CamaraEntity> entidades = paginacao.deslocamento() > Integer.MAX_VALUE
            ? List.of()
            : query.page(paginacao.pagina(), paginacao.tamanho()).list();
        var politicas = politicasVigentes(entidades.stream().map(c -> c.id).toList());
        List<Camara> itens = entidades.stream()
            .map(c -> CamaraMapper.paraDominio(c, politicas.get(c.id)))
            .toList();

        return Pagina.de(itens, paginacao, query.count());
    }
}

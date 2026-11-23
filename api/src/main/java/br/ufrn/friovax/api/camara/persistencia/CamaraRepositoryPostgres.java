package br.ufrn.friovax.api.camara.persistencia;

import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.CamaraFiltro;
import br.ufrn.friovax.api.camara.dominio.CamaraRepository;
import br.ufrn.friovax.api.camara.dominio.PoliticaTermica;
import br.ufrn.friovax.api.camara.dominio.ReferenciaPolitica;
import br.ufrn.friovax.api.compartilhado.dominio.CodigoDuplicado;
import br.ufrn.friovax.api.compartilhado.dominio.Pagina;
import br.ufrn.friovax.api.compartilhado.dominio.Paginacao;
import br.ufrn.friovax.api.compartilhado.dominio.VersaoDesatualizada;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceException;
import org.hibernate.exception.ConstraintViolationException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    public Map<ReferenciaPolitica, PoliticaTermica> buscarPoliticas(
            Set<ReferenciaPolitica> referencias) {
        if (referencias.isEmpty()) {
            return Map.of();
        }
        var pares = new ArrayList<>(referencias);
        var predicados = new ArrayList<String>();
        for (int i = 0; i < pares.size(); i++) {
            predicados.add("(camaraId = :camara" + i + " and versao = :versao" + i + ")");
        }
        var query = getEntityManager().createQuery("from PoliticaTermicaEntity where "
                + String.join(" or ", predicados), PoliticaTermicaEntity.class);
        for (int i = 0; i < pares.size(); i++) {
            query.setParameter("camara" + i, pares.get(i).camaraId());
            query.setParameter("versao" + i, pares.get(i).versao());
        }
        return query.getResultStream().collect(Collectors.toMap(
                e -> new ReferenciaPolitica(e.camaraId, e.versao),
                CamaraMapper::politicaParaDominio));
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
        var gravada = getEntityManager()
            .createQuery("from PoliticaTermicaEntity p where p.camaraId = :camara and p.versao = :versao",
                PoliticaTermicaEntity.class)
            .setParameter("camara", camaraId)
            .setParameter("versao", politica.getVersao())
            .getResultStream()
            .findFirst();
        if (gravada.isPresent()) {
            var existente = gravada.get();
            if (existente.temperaturaMinima.compareTo(politica.getTemperaturaMinima()) != 0
                || existente.temperaturaMaxima.compareTo(politica.getTemperaturaMaxima()) != 0
                || existente.limiar != politica.getLimiar()) {
                throw versaoJaGravada(camaraId, politica);
            }
            return;
        }
        try {
            getEntityManager().persist(CamaraMapper.politicaParaEntidade(camaraId, politica));
            getEntityManager().flush();
        } catch (PersistenceException e) {
            if (violou(e, "uk_politicas_camara_versao")) {
                throw versaoJaGravada(camaraId, politica);
            }
            throw e;
        }
    }

    private static VersaoDesatualizada versaoJaGravada(long camaraId, PoliticaTermica politica) {
        return new VersaoDesatualizada("A versão " + politica.getVersao() + " da política térmica da câmara "
            + camaraId + " já foi gravada por outra alteração. Consulte a política vigente e reenvie.");
    }

    private static boolean violou(PersistenceException e, String restricao) {
        for (Throwable causa = e; causa != null; causa = causa.getCause()) {
            if (causa instanceof ConstraintViolationException cve && restricao.equals(cve.getConstraintName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Pagina<PoliticaTermica> listarPoliticas(long camaraId, Paginacao paginacao) {
        long total = getEntityManager()
            .createQuery("select count(p) from PoliticaTermicaEntity p where p.camaraId = :camara", Long.class)
            .setParameter("camara", camaraId)
            .getSingleResult();
        List<PoliticaTermica> itens = paginacao.deslocamento() >= total
            ? List.of()
            : getEntityManager()
                .createQuery("from PoliticaTermicaEntity where camaraId = :camara order by versao desc",
                    PoliticaTermicaEntity.class)
                .setParameter("camara", camaraId)
                .setFirstResult((int) paginacao.deslocamento())
                .setMaxResults(paginacao.tamanho())
                .getResultStream()
                .map(CamaraMapper::politicaParaDominio)
                .toList();
        return Pagina.de(itens, paginacao, total);
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

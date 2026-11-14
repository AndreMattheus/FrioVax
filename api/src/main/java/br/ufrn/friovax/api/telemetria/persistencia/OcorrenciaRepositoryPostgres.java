package br.ufrn.friovax.api.telemetria.persistencia;

import br.ufrn.friovax.api.compartilhado.dominio.Pagina;
import br.ufrn.friovax.api.compartilhado.dominio.Paginacao;
import br.ufrn.friovax.api.telemetria.dominio.LoteAfetado;
import br.ufrn.friovax.api.telemetria.dominio.Ocorrencia;
import br.ufrn.friovax.api.telemetria.dominio.OcorrenciaJaAberta;
import br.ufrn.friovax.api.telemetria.dominio.OcorrenciaRepository;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.PersistenceException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Grava a ocorrência com suas evidências e lotes afetados na mesma transação do chamador: se qualquer parte falhar,
 * nada fica gravado. Evidências e lotes só são inseridos na abertura; depois, só a resolução é atualizada.
 */
@ApplicationScoped
public class OcorrenciaRepositoryPostgres implements OcorrenciaRepository, PanacheRepositoryBase<OcorrenciaEntity, Long> {

    @Override
    public Ocorrencia salvar(Ocorrencia ocorrencia) {
        if (ocorrencia.getId() == null) {
            inserir(ocorrencia);
        } else {
            gravarResolucao(ocorrencia);
        }
        return ocorrencia;
    }

    private void inserir(Ocorrencia ocorrencia) {
        var entity = OcorrenciaMapper.paraEntidade(ocorrencia);
        try {
            persist(entity);
            getEntityManager().flush();
        } catch (PersistenceException e) {
            if (RestricoesPostgres.violada(e).filter("uk_ocorrencias_aberta_por_camara"::equals).isPresent()) {
                throw new OcorrenciaJaAberta(ocorrencia.getCamaraId());
            }
            throw e;
        }

        int posicao = 1;
        for (Long leituraId : ocorrencia.getEvidencias()) {
            getEntityManager().createNativeQuery("""
                    insert into ocorrencia_evidencias (ocorrencia_id, leitura_id, posicao)
                    values (:ocorrencia, :leitura, :posicao)
                    """)
                    .setParameter("ocorrencia", entity.id)
                    .setParameter("leitura", leituraId)
                    .setParameter("posicao", posicao++)
                    .executeUpdate();
        }
        for (LoteAfetado lote : ocorrencia.getLotesAfetados()) {
            getEntityManager().createNativeQuery("""
                    insert into ocorrencia_lotes (ocorrencia_id, lote_id, codigo_lote, imunobiologico, quantidade)
                    values (:ocorrencia, :lote, :codigo, :imunobiologico, :quantidade)
                    """)
                    .setParameter("ocorrencia", entity.id)
                    .setParameter("lote", lote.loteId())
                    .setParameter("codigo", lote.codigo())
                    .setParameter("imunobiologico", lote.imunobiologico())
                    .setParameter("quantidade", lote.quantidade())
                    .executeUpdate();
        }
        ocorrencia.atribuirId(entity.id);
    }

    private void gravarResolucao(Ocorrencia ocorrencia) {
        var entity = findByIdOptional(ocorrencia.getId())
                .orElseThrow(() -> new IllegalStateException("Ocorrência " + ocorrencia.getId() + " não está gravada"));
        if (entity.resolvidaEm != null && ocorrencia.isAberta()) {
            throw new IllegalStateException("Uma ocorrência resolvida não pode ser reaberta");
        }
        OcorrenciaMapper.copiarResolucao(ocorrencia, entity);
        getEntityManager().flush();
    }

    @Override
    public Optional<Ocorrencia> buscarPorId(long id) {
        return findByIdOptional(id).map(this::completa);
    }

    @Override
    public Optional<Ocorrencia> abertaDaCamara(long camaraId) {
        return find("camaraId = ?1 and resolvidaEm is null", camaraId).firstResultOptional().map(this::completa);
    }

    @Override
    public Optional<Ocorrencia> ultimaResolvidaDaCamara(long camaraId) {
        return find("camaraId = ?1 and resolvidaEm is not null", Sort.descending("resolvidaEm", "id"), camaraId)
                .firstResultOptional()
                .map(this::completa);
    }

    @Override
    public Optional<Ocorrencia> abertaPelaLeitura(long leituraId) {
        return find("leituraAberturaId = ?1", leituraId).firstResultOptional().map(this::completa);
    }

    @Override
    public Pagina<Ocorrencia> listarDaCamara(long camaraId, Paginacao paginacao) {
        var query = find("camaraId = ?1", Sort.descending("abertaEm", "id"), camaraId);

        List<OcorrenciaEntity> entidades = paginacao.deslocamento() > Integer.MAX_VALUE
            ? List.of()
            : query.page(paginacao.pagina(), paginacao.tamanho()).list();

        // Evidências e lotes da página inteira em duas consultas, em vez de duas por ocorrência.
        var ids = entidades.stream().map(e -> e.id).toList();
        var evidencias = evidencias(ids);
        var lotes = lotesAfetados(ids);
        List<Ocorrencia> itens = entidades.stream()
                .map(e -> OcorrenciaMapper.paraDominio(e, evidencias.getOrDefault(e.id, List.of()),
                        lotes.getOrDefault(e.id, List.of())))
                .toList();

        return Pagina.de(itens, paginacao, query.count());
    }

    private Ocorrencia completa(OcorrenciaEntity e) {
        return OcorrenciaMapper.paraDominio(e, evidencias(List.of(e.id)).getOrDefault(e.id, List.of()),
                lotesAfetados(List.of(e.id)).getOrDefault(e.id, List.of()));
    }

    @SuppressWarnings("unchecked")
    private Map<Long, List<Long>> evidencias(Collection<Long> ocorrenciaIds) {
        var resultado = new HashMap<Long, List<Long>>();
        if (ocorrenciaIds.isEmpty()) {
            return resultado;
        }
        List<Object[]> linhas = getEntityManager().createNativeQuery("""
                select ocorrencia_id, leitura_id from ocorrencia_evidencias
                where ocorrencia_id in (:ids) order by ocorrencia_id, posicao
                """)
                .setParameter("ids", ocorrenciaIds)
                .getResultList();
        for (Object[] linha : linhas) {
            resultado.computeIfAbsent(((Number) linha[0]).longValue(), id -> new ArrayList<>())
                    .add(((Number) linha[1]).longValue());
        }
        return resultado;
    }

    @SuppressWarnings("unchecked")
    private Map<Long, List<LoteAfetado>> lotesAfetados(Collection<Long> ocorrenciaIds) {
        var resultado = new HashMap<Long, List<LoteAfetado>>();
        if (ocorrenciaIds.isEmpty()) {
            return resultado;
        }
        List<Object[]> linhas = getEntityManager().createNativeQuery("""
                select ocorrencia_id, lote_id, codigo_lote, imunobiologico, quantidade from ocorrencia_lotes
                where ocorrencia_id in (:ids) order by ocorrencia_id, lote_id
                """)
                .setParameter("ids", ocorrenciaIds)
                .getResultList();
        for (Object[] linha : linhas) {
            resultado.computeIfAbsent(((Number) linha[0]).longValue(), id -> new ArrayList<>())
                    .add(new LoteAfetado(((Number) linha[1]).longValue(), (String) linha[2], (String) linha[3],
                            ((Number) linha[4]).intValue()));
        }
        return resultado;
    }
}

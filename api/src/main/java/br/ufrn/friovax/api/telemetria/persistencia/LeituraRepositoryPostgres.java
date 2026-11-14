package br.ufrn.friovax.api.telemetria.persistencia;

import br.ufrn.friovax.api.compartilhado.dominio.Pagina;
import br.ufrn.friovax.api.compartilhado.dominio.Paginacao;
import br.ufrn.friovax.api.telemetria.dominio.Leitura;
import br.ufrn.friovax.api.telemetria.dominio.LeituraForaDeOrdem;
import br.ufrn.friovax.api.telemetria.dominio.LeituraJaRegistrada;
import br.ufrn.friovax.api.telemetria.dominio.LeituraRepository;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.PersistenceException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class LeituraRepositoryPostgres implements LeituraRepository, PanacheRepositoryBase<LeituraEntity, Long> {

    @Override
    public Leitura salvar(Leitura leitura) {
        if (leitura.getId() != null) {
            throw new IllegalStateException("Leituras não são alteradas depois de gravadas (id " + leitura.getId() + ")");
        }
        var entity = LeituraMapper.paraEntidade(leitura);
        try {
            persist(entity);
            getEntityManager().flush();
        } catch (PersistenceException e) {
            var restricao = RestricoesPostgres.violada(e).orElse("");
            if (restricao.equals("uk_leituras_camara_id_externo")) {
                throw new LeituraJaRegistrada(leitura.getCamaraId(), leitura.getIdExterno());
            }
            if (restricao.equals("uk_leituras_camara_instante")) {
                throw new LeituraForaDeOrdem(leitura.getCamaraId());
            }
            throw e;
        }
        leitura.atribuirId(entity.id);
        return leitura;
    }

    @Override
    public Optional<Leitura> buscarPorId(long id) {
        return findByIdOptional(id).map(LeituraMapper::paraDominio);
    }

    @Override
    public Optional<Leitura> buscarPorChave(long camaraId, String idExterno) {
        return find("camaraId = ?1 and idExterno = ?2", camaraId, idExterno)
                .firstResultOptional()
                .map(LeituraMapper::paraDominio);
    }

    @Override
    public Optional<Leitura> ultimaDaCamara(long camaraId) {
        return find("camaraId = ?1", Sort.descending("instante", "id"), camaraId)
                .firstResultOptional()
                .map(LeituraMapper::paraDominio);
    }

    @Override
    public List<Leitura> contextoDaJanela(long camaraId, int politicaVersao, int limite, Long aposLeituraId) {
        if (limite < 0 || limite > CONTEXTO_MAXIMO) {
            throw new IllegalArgumentException("O contexto da janela vai de 0 a " + CONTEXTO_MAXIMO + " leituras.");
        }
        if (limite == 0) {
            return List.of();
        }
        var jpql = new StringBuilder("from LeituraEntity where camaraId = :camara and politicaVersao = :versao");
        if (aposLeituraId != null) {
            jpql.append(" and id > :apos");
        }
        jpql.append(" order by instante desc, id desc");

        var consulta = getEntityManager().createQuery(jpql.toString(), LeituraEntity.class)
                .setParameter("camara", camaraId)
                .setParameter("versao", politicaVersao)
                .setMaxResults(limite); // LIMIT no SQL p nunca carregar o histórico inteiro
        if (aposLeituraId != null) {
            consulta.setParameter("apos", aposLeituraId);
        }

        var recentes = new ArrayList<>(consulta.getResultList().stream().map(LeituraMapper::paraDominio).toList());
        Collections.reverse(recentes);
        return recentes;
    }

    @Override
    public Pagina<Leitura> listarDaCamara(long camaraId, Paginacao paginacao) {
        var query = find("camaraId = ?1", Sort.descending("instante", "id"), camaraId);

        List<Leitura> itens = paginacao.deslocamento() > Integer.MAX_VALUE
            ? List.of()
            : query.page(paginacao.pagina(), paginacao.tamanho()).list().stream()
                .map(LeituraMapper::paraDominio)
                .toList();

        return Pagina.de(itens, paginacao, query.count());
    }
}

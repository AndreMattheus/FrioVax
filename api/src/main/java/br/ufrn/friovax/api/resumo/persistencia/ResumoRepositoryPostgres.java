package br.ufrn.friovax.api.resumo.persistencia;

import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import br.ufrn.friovax.api.camara.dominio.EstadoTermico;
import br.ufrn.friovax.api.lote.dominio.EstadoLote;
import br.ufrn.friovax.api.resumo.dominio.ResumoRepository;
import br.ufrn.friovax.api.resumo.dominio.TotaisResumo;
import jakarta.enterprise.context.ApplicationScoped;
import io.quarkus.hibernate.orm.panache.Panache;
import jakarta.transaction.Transactional;
import java.util.HashMap;

@ApplicationScoped
public class ResumoRepositoryPostgres implements ResumoRepository {

    // Uma instrução SQL: todos os agregados enxergam o mesmo snapshot do PostgreSQL.
    @Override
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public TotaisResumo consultar() {
        var operacionais = new HashMap<String, Long>();
        var termicos = new HashMap<String, Long>();
        var estoques = new HashMap<String, TotaisResumo.Estoque>();
        for (var estado : EstadoCamara.values()) operacionais.put(estado.name(), 0L);
        for (var estado : EstadoTermico.values()) termicos.put(estado.name(), 0L);
        for (var estado : EstadoLote.values()) estoques.put(estado.name(), new TotaisResumo.Estoque(0, 0));
        long abertos = 0, afetados = 0;
        var linhas = Panache.getEntityManager().createNativeQuery("""
                select 'operacional', estado, count(*), 0 from camaras where ativo group by estado
                union all
                select 'termico', estado_termico, count(*), 0 from camaras where ativo group by estado_termico
                union all
                select 'estoque', estado, count(*), sum(quantidade) from lotes where ativo group by estado
                union all
                select 'abertos', '', count(*), 0 from ocorrencias where resolvida_em is null
                union all
                select 'afetados', '', count(distinct ol.lote_id), 0
                  from ocorrencia_lotes ol join ocorrencias o on o.id = ol.ocorrencia_id
                 where o.resolvida_em is null
                """, Object[].class).getResultList();
        for (Object resultado : linhas) {
            Object[] linha = (Object[]) resultado;
            var estado = (String) linha[1];
            long quantidade = ((Number) linha[2]).longValue();
            switch ((String) linha[0]) {
                case "operacional" -> operacionais.put(estado, quantidade);
                case "termico" -> termicos.put(estado, quantidade);
                case "estoque" -> estoques.put(estado,
                        new TotaisResumo.Estoque(quantidade, ((Number) linha[3]).longValue()));
                case "abertos" -> abertos = quantidade;
                case "afetados" -> afetados = quantidade;
                default -> throw new IllegalStateException("Agregado desconhecido");
            }
        }
        return new TotaisResumo(operacionais, termicos, estoques, abertos, afetados);
    }
}

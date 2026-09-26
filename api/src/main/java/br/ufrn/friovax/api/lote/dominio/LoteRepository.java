package br.ufrn.friovax.api.lote.dominio;

import br.ufrn.friovax.api.compartilhado.dominio.Pagina;
import br.ufrn.friovax.api.compartilhado.dominio.Paginacao;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Porta de persistência de lotes. A ocupação de uma câmara é a soma de {@code quantidade} dos seus lotes ativos
 * (contrato, D6).
 */
public interface LoteRepository {

    /**
     * Insere o lote (atribuindo o id) ou grava as alterações de um lote existente.
     *
     * @throws br.ufrn.friovax.api.compartilhado.dominio.CodigoDuplicado se outro lote já usa o código
     */
    Lote salvar(Lote lote);

    Optional<Lote> buscarPorId(long id);

    /** Considera lotes ativos e inativos; o código deve estar normalizado. */
    boolean existePorCodigo(String codigo);

    /** Lista os lotes que atendem ao filtro, ordenados por id crescente. */
    Pagina<Lote> listar(LoteFiltro filtro, Paginacao paginacao);

    long ocupacaoDaCamara(long camaraId);

    /** Ocupação sem contar o lote informado, para revalidar a capacidade ao alterá-lo sem contagem dupla. */
    long ocupacaoDaCamaraExcluindoLote(long camaraId, long loteId);

    /** Ocupação de várias câmaras numa única consulta; câmaras sem lotes ativos aparecem com zero. */
    Map<Long, Long> ocupacaoPorCamara(Collection<Long> camaraIds);

    boolean existeLoteAtivoNaCamara(long camaraId);
}

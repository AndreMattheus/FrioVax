package br.ufrn.friovax.api.camara.dominio;

import br.ufrn.friovax.api.compartilhado.dominio.Pagina;
import br.ufrn.friovax.api.compartilhado.dominio.Paginacao;

import java.util.Optional;

/**
 * Porta de persistência de câmaras. Implementada pelo adaptador PostgreSQL na aplicação e em memória nos testes.
 */
public interface CamaraRepository {

    /**
     * Insere a câmara (atribuindo o id) ou grava as alterações de uma câmara existente.
     *
     * @throws br.ufrn.friovax.api.compartilhado.dominio.CodigoDuplicado se outra câmara já usa o código
     */
    Camara salvar(Camara camara);

    Optional<Camara> buscarPorId(long id);

    /**
     * Busca a câmara bloqueando-a até o fim da transação, para que alocações concorrentes não ultrapassem a
     * capacidade (contrato, §3.2).
     */
    Optional<Camara> buscarPorIdParaAlteracao(long id);

    /** Considera câmaras ativas e inativas; o código deve estar normalizado. */
    boolean existePorCodigo(String codigo);

    /** Lista as câmaras que atendem ao filtro, ordenadas por id crescente. */
    Pagina<Camara> listar(CamaraFiltro filtro, Paginacao paginacao);
}

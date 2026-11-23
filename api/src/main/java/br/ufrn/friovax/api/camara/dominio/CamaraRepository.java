package br.ufrn.friovax.api.camara.dominio;

import br.ufrn.friovax.api.compartilhado.dominio.CodigoDuplicado;
import br.ufrn.friovax.api.compartilhado.dominio.Pagina;
import br.ufrn.friovax.api.compartilhado.dominio.Paginacao;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Porta de persistência de câmaras. Implementada pelo adaptador PostgreSQL na aplicação e em memória nos testes.
 */
public interface CamaraRepository {

    /**
     * Insere a câmara (atribuindo o id) ou grava as alterações de uma câmara existente.
     *
     * @throws CodigoDuplicado se outra câmara já usa o código
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

    /** Busca somente as versões solicitadas, em conjunto, para compor páginas de histórico. */
    Map<ReferenciaPolitica, PoliticaTermica> buscarPoliticas(
            Set<ReferenciaPolitica> referencias);

    /**
     * Todas as versões da política térmica da câmara, da mais antiga para a vigente. A vigente também está em
     * {@link Camara#getPolitica()}; as anteriores nunca são alteradas nem apagadas.
     */
    List<PoliticaTermica> historicoDePoliticas(long camaraId);

    /** Página do histórico de versões da política, da mais recente para a mais antiga (versão decrescente). */
    Pagina<PoliticaTermica> listarPoliticas(long camaraId, Paginacao paginacao);

    /** Lista as câmaras que atendem ao filtro, ordenadas por id crescente. */
    Pagina<Camara> listar(CamaraFiltro filtro, Paginacao paginacao);
}

package br.ufrn.friovax.api.telemetria.dominio;

import br.ufrn.friovax.api.compartilhado.dominio.Pagina;
import br.ufrn.friovax.api.compartilhado.dominio.Paginacao;

import java.util.List;
import java.util.Optional;

/** Porta de persistência das leituras. As leituras só são inseridas, nunca alteradas. */
public interface LeituraRepository {

    /** Maior quantidade de leituras de contexto numa janela: N−1 com N até 100 (ADR 0003, seção 3). */
    int CONTEXTO_MAXIMO = 99;

    /**
     * Grava uma leitura nova.
     *
     * <p>A constraint {@code uk_leituras_camara_instante} só impede dois instantes iguais na mesma câmara; não impede
     * gravar um instante anterior ao da última leitura. A exigência de instante estritamente posterior (ADR 0003,
     * seção 5) é do caso de uso da recepção, que confere {@link #ultimaDaCamara} com a câmara bloqueada.
     *
     * @throws LeituraJaRegistrada se a chave {@code (camaraId, idExterno)} já existir
     * @throws LeituraForaDeOrdem  se já houver leitura da câmara no mesmo instante
     */
    Leitura salvar(Leitura leitura);

    Optional<Leitura> buscarPorId(long id);

    /** Leitura já aceita com a chave externa, para recuperar o resultado de um reenvio. */
    Optional<Leitura> buscarPorChave(long camaraId, String idExterno);

    /** Última leitura aceita da câmara, de qualquer versão da política (usada para exigir ordem crescente). */
    Optional<Leitura> ultimaDaCamara(long camaraId);

    /**
     * Contexto da janela de análise: as últimas {@code limite} leituras da câmara na versão de política informada,
     * em ordem cronológica crescente.
     *
     * @param limite              de 0 a {@link #CONTEXTO_MAXIMO} (N−1); 0 devolve lista vazia sem consultar
     * @param aposLeituraId       quando informado, só considera leituras com id maior, para não atravessar o
     *                            reinício do episódio depois de uma resolução
     */
    List<Leitura> contextoDaJanela(long camaraId, int politicaVersao, int limite, Long aposLeituraId);

    /** Leituras da câmara, da mais recente para a mais antiga (instante decrescente, depois id decrescente). */
    Pagina<Leitura> listarDaCamara(long camaraId, Paginacao paginacao);
}

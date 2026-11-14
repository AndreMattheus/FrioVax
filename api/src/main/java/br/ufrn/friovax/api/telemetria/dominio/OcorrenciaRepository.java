package br.ufrn.friovax.api.telemetria.dominio;

import br.ufrn.friovax.api.compartilhado.dominio.Pagina;
import br.ufrn.friovax.api.compartilhado.dominio.Paginacao;

import java.util.Optional;

/** Porta de persistência das ocorrências, com suas evidências e lotes afetados. */
public interface OcorrenciaRepository {

    /**
     * Grava uma ocorrência nova (com evidências e lotes afetados) ou a resolução de uma existente. Evidências e
     * lotes nunca são alterados depois da abertura.
     *
     * @throws OcorrenciaJaAberta se a câmara já tiver outra ocorrência aberta
     */
    Ocorrencia salvar(Ocorrencia ocorrencia);

    Optional<Ocorrencia> buscarPorId(long id);

    Optional<Ocorrencia> abertaDaCamara(long camaraId);

    /** Ocorrência resolvida mais recente, que marca onde começa o episódio atual. */
    Optional<Ocorrencia> ultimaResolvidaDaCamara(long camaraId);

    /** Ocorrência aberta por esta leitura, para recuperar o resultado de um reenvio. */
    Optional<Ocorrencia> abertaPelaLeitura(long leituraId);

    /** Ocorrências da câmara, da mais recente para a mais antiga (abertura decrescente, depois id decrescente). */
    Pagina<Ocorrencia> listarDaCamara(long camaraId, Paginacao paginacao);
}

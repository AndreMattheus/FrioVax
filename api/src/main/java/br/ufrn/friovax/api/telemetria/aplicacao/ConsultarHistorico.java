package br.ufrn.friovax.api.telemetria.aplicacao;

import br.ufrn.friovax.api.camara.aplicacao.ConsultarPoliticasHistoricas;
import br.ufrn.friovax.api.camara.dominio.PoliticaTermica;
import br.ufrn.friovax.api.camara.dominio.ReferenciaPolitica;
import br.ufrn.friovax.api.compartilhado.dominio.Pagina;
import br.ufrn.friovax.api.compartilhado.dominio.Paginacao;
import br.ufrn.friovax.api.compartilhado.dominio.RecursoNaoEncontrado;
import br.ufrn.friovax.api.telemetria.dominio.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Consulta somente resultados confirmados; não depende da porta de análise térmica. */
@ApplicationScoped
public class ConsultarHistorico {
    private final LeituraRepository leituras;
    private final OcorrenciaRepository ocorrencias;
    private final ConsultarPoliticasHistoricas politicas;

    @Inject
    public ConsultarHistorico(LeituraRepository leituras, OcorrenciaRepository ocorrencias,
                             ConsultarPoliticasHistoricas politicas) {
        this.leituras = leituras;
        this.ocorrencias = ocorrencias;
        this.politicas = politicas;
    }

    @Transactional
    public Pagina<LeituraHistorica> listarLeituras(long camaraId, PeriodoConsulta periodo, Paginacao paginacao) {
        politicas.exigirCamara(camaraId);
        var pagina = leituras.listarDaCamara(camaraId, periodo, paginacao);
        var versoes = politicasDasLeituras(pagina.itens());
        return pagina.map(l -> leituraHistorica(l, versoes));
    }

    @Transactional
    public Pagina<OcorrenciaHistorica> listarOcorrencias(OcorrenciaFiltro filtro, Paginacao paginacao) {
        var pagina = ocorrencias.listar(filtro, paginacao);
        var versoes = politicas.consultar(pagina.itens().stream()
                .map(o -> new ReferenciaPolitica(o.camaraId(), o.politicaVersao())).collect(Collectors.toSet()));
        return pagina.map(o -> new OcorrenciaHistorica(o, politica(versoes, o.camaraId(), o.politicaVersao())));
    }

    @Transactional
    public DetalheOcorrencia consultarOcorrencia(long id) {
        var o = ocorrencias.buscarPorId(id).orElseThrow(() -> new RecursoNaoEncontrado("Ocorrência", id));
        var evidencias = leituras.buscarPorIds(o.getEvidencias());
        var porId = evidencias.stream().collect(Collectors.toMap(Leitura::getId, Function.identity()));
        var versoes = politicasDasLeituras(evidencias);
        var ordenadas = o.getEvidencias().stream().map(leituraId -> leituraHistorica(
                Objects.requireNonNull(porId.get(leituraId), "Evidência persistida ausente"), versoes)).toList();
        return new DetalheOcorrencia(new OcorrenciaHistorica(ResumoOcorrencia.de(o),
                politica(versoes, o.getCamaraId(), o.getPoliticaVersao())), o.getLeituraAberturaId(),
                ordenadas, o.getLotesAfetados(), o.getLeituraResolucaoId().orElse(null));
    }

    private Map<ReferenciaPolitica, PoliticaTermica> politicasDasLeituras(List<Leitura> lista) {
        return politicas.consultar(lista.stream().map(l -> new ReferenciaPolitica(l.getCamaraId(),
                l.getPoliticaVersao())).collect(Collectors.toSet()));
    }

    private static PoliticaTermica politica(Map<ReferenciaPolitica, PoliticaTermica> versoes,
                                            long camaraId, int versao) {
        return Objects.requireNonNull(versoes.get(new ReferenciaPolitica(camaraId, versao)),
                "Política histórica persistida ausente");
    }

    private static LeituraHistorica leituraHistorica(Leitura l, Map<ReferenciaPolitica, PoliticaTermica> versoes) {
        return new LeituraHistorica(l, politica(versoes, l.getCamaraId(), l.getPoliticaVersao()));
    }

    public record LeituraHistorica(Leitura leitura, PoliticaTermica politica) { }
    public record OcorrenciaHistorica(ResumoOcorrencia ocorrencia, PoliticaTermica politica) { }
    public record DetalheOcorrencia(OcorrenciaHistorica resumo, long leituraAberturaId,
                                   List<LeituraHistorica> evidencias, List<LoteAfetado> lotesAfetados,
                                   Long leituraResolucaoId) {
        public DetalheOcorrencia {
            evidencias = List.copyOf(evidencias);
            lotesAfetados = List.copyOf(lotesAfetados);
        }
    }
}

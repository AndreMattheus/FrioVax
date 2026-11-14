package br.ufrn.friovax.api.suporte;

import br.ufrn.friovax.api.compartilhado.dominio.Pagina;
import br.ufrn.friovax.api.compartilhado.dominio.Paginacao;
import br.ufrn.friovax.api.telemetria.dominio.Ocorrencia;
import br.ufrn.friovax.api.telemetria.dominio.OcorrenciaJaAberta;
import br.ufrn.friovax.api.telemetria.dominio.OcorrenciaRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

public class OcorrenciaRepositoryEmMemoria implements OcorrenciaRepository {
    private static final Comparator<Ocorrencia> MAIS_RECENTE_PRIMEIRO =
            Comparator.comparing(Ocorrencia::getAbertaEm).thenComparing(Ocorrencia::getId).reversed();

    private final Map<Long, Ocorrencia> ocorrencias = new TreeMap<>();
    private final AtomicLong sequencia = new AtomicLong();

    public synchronized void limpar() {
        ocorrencias.clear();
    }

    @Override
    public synchronized Ocorrencia salvar(Ocorrencia ocorrencia) {
        if (ocorrencia.getId() == null) {
            if (ocorrencia.isAberta() && abertaDaCamara(ocorrencia.getCamaraId()).isPresent()) {
                throw new OcorrenciaJaAberta(ocorrencia.getCamaraId());
            }
            ocorrencia.atribuirId(sequencia.incrementAndGet());
        } else {
            var gravada = ocorrencias.get(ocorrencia.getId());
            if (gravada == null) {
                throw new IllegalStateException("Ocorrência " + ocorrencia.getId() + " não está gravada");
            }
            if (!gravada.isAberta() && ocorrencia.isAberta()) {
                throw new IllegalStateException("Uma ocorrência resolvida não pode ser reaberta");
            }
        }
        ocorrencias.put(ocorrencia.getId(), copia(ocorrencia));
        return ocorrencia;
    }

    @Override
    public synchronized Optional<Ocorrencia> buscarPorId(long id) {
        return Optional.ofNullable(ocorrencias.get(id)).map(OcorrenciaRepositoryEmMemoria::copia);
    }

    @Override
    public synchronized Optional<Ocorrencia> abertaDaCamara(long camaraId) {
        return daCamara(camaraId).stream().filter(Ocorrencia::isAberta).findFirst()
                .map(OcorrenciaRepositoryEmMemoria::copia);
    }

    @Override
    public synchronized Optional<Ocorrencia> ultimaResolvidaDaCamara(long camaraId) {
        return daCamara(camaraId).stream()
                .filter(o -> !o.isAberta())
                .max(Comparator.comparing((Ocorrencia o) -> o.getResolvidaEm().orElseThrow())
                        .thenComparing(Ocorrencia::getId))
                .map(OcorrenciaRepositoryEmMemoria::copia);
    }

    @Override
    public synchronized Optional<Ocorrencia> abertaPelaLeitura(long leituraId) {
        return ocorrencias.values().stream().filter(o -> o.getLeituraAberturaId() == leituraId).findFirst()
                .map(OcorrenciaRepositoryEmMemoria::copia);
    }

    @Override
    public synchronized Pagina<Ocorrencia> listarDaCamara(long camaraId, Paginacao paginacao) {
        return Paginador.paginar(daCamara(camaraId).stream()
                .sorted(MAIS_RECENTE_PRIMEIRO)
                .map(OcorrenciaRepositoryEmMemoria::copia)
                .toList(), paginacao);
    }

    private List<Ocorrencia> daCamara(long camaraId) {
        return ocorrencias.values().stream().filter(o -> o.getCamaraId() == camaraId).toList();
    }

    private static Ocorrencia copia(Ocorrencia o) {
        return Ocorrencia.reconstituir(o.getId(), o.getCamaraId(), o.getPoliticaVersao(), o.getLeituraAberturaId(),
                o.getAbertaEm(), o.getEvidencias(), o.getLotesAfetados(), o.getResolvidaEm().orElse(null),
                o.getResolvidaPor().orElse(null), o.getJustificativa().orElse(null),
                o.getLeituraResolucaoId().orElse(null));
    }
}

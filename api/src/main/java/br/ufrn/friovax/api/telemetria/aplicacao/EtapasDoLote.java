package br.ufrn.friovax.api.telemetria.aplicacao;

import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.CamaraRepository;
import br.ufrn.friovax.api.compartilhado.dominio.EstadoIncompativel;
import br.ufrn.friovax.api.compartilhado.dominio.RecursoNaoEncontrado;
import br.ufrn.friovax.api.telemetria.aplicacao.ResultadoDoLote.Desfecho;
import br.ufrn.friovax.api.telemetria.dominio.ClassificacaoTermica;
import br.ufrn.friovax.api.telemetria.dominio.Leitura;
import br.ufrn.friovax.api.telemetria.dominio.LeituraConcorrente;
import br.ufrn.friovax.api.telemetria.dominio.LeituraConflitante;
import br.ufrn.friovax.api.telemetria.dominio.NovaLeitura;
import br.ufrn.friovax.api.telemetria.dominio.OcorrenciaRepository;
import br.ufrn.friovax.api.telemetria.dominio.ResultadoDaCandidata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

/** Prepara ou confirma as leituras de uma câmara por vez, sempre sob o lock da câmara. */
@ApplicationScoped
class EtapasDoLote {

    private final EtapasDaRecepcao etapas;
    private final CamaraRepository camaras;
    private final OcorrenciaRepository ocorrencias;
    private final Clock relogio;

    @Inject
    EtapasDoLote(EtapasDaRecepcao etapas, CamaraRepository camaras, OcorrenciaRepository ocorrencias, Clock relogio) {
        this.etapas = etapas;
        this.camaras = camaras;
        this.ocorrencias = ocorrencias;
        this.relogio = relogio;
    }

    @Transactional(TxType.REQUIRES_NEW)
    Preparacao preparar(long camaraId, List<NovaLeitura> entradas) {
        var definidos = new ArrayList<ResultadoDoLote>();
        Camara camara;
        try {
            camara = etapas.bloquear(camaraId);
        } catch (RecursoNaoEncontrado inexistente) {
            return rejeitar(definidos, entradas, Desfecho.CAMARA_INEXISTENTE);
        }

        var candidatas = new ArrayList<NovaLeitura>();
        boolean chaveConflitante = false;
        for (var entrada : entradas) {
            try {
                var aceita = etapas.jaAceita(entrada);
                if (aceita.isEmpty()) {
                    candidatas.add(entrada);
                } else {
                    var original = aceita.get();
                    definidos.add(new ResultadoDoLote(entrada, Desfecho.REPETIDA, original,
                            etapas.ocorrenciaAbertaPor(original)));
                }
            } catch (LeituraConflitante conflito) {
                chaveConflitante = true;
                definidos.add(ResultadoDoLote.rejeitada(entrada, Desfecho.CHAVE_CONFLITANTE));
            }
        }
        if (candidatas.isEmpty()) {
            return new Preparacao(definidos, null);
        }
        if (chaveConflitante) {
            return rejeitar(definidos, candidatas, Desfecho.CHAVE_CONFLITANTE);
        }
        try {
            camara.exigirAptaParaLeituras();
        } catch (EstadoIncompativel inapta) {
            return rejeitar(definidos, candidatas, Desfecho.CAMARA_INAPTA);
        }
        var ultima = etapas.ultimaDaCamara(camaraId);
        candidatas.sort(Comparator.comparing(NovaLeitura::medidaEm));
        var anterior = ultima.map(Leitura::getInstante).orElse(null);
        for (var candidata : candidatas) {
            if (anterior != null && !candidata.medidaEm().isAfter(anterior)) {
                return rejeitar(definidos, candidatas, Desfecho.FORA_DE_ORDEM);
            }
            anterior = candidata.medidaEm();
        }
        return new Preparacao(definidos,
                new Grupo(etapas.snapshot(camara, ultima, true), List.copyOf(candidatas)));
    }

    @Transactional(TxType.REQUIRES_NEW)
    List<ResultadoDoLote> confirmar(Grupo grupo, List<ResultadoDaCandidata> analises) {
        var snapshot = grupo.snapshot();
        var camara = revalidar(grupo);
        var agora = OffsetDateTime.now(relogio);
        var ids = new HashMap<String, Long>();
        snapshot.contexto().forEach(leitura -> ids.put(leitura.getIdExterno(), leitura.getId()));
        boolean ocorrenciaAberta = ocorrencias.abertaDaCamara(camara.getId()).isPresent();
        var confirmadas = new ArrayList<ResultadoDoLote>(grupo.candidatas().size());
        for (int i = 0; i < grupo.candidatas().size(); i++) {
            var candidata = grupo.candidatas().get(i);
            var analise = analises.get(i);
            var leitura = etapas.gravar(candidata, snapshot, analise.resultado(), agora);
            ids.put(leitura.getIdExterno(), leitura.getId());
            Long ocorrenciaId = null;
            if (analise.resultado().limiarAtingido() && !ocorrenciaAberta) {
                var evidencias = analise.evidencias().stream().map(ids::get).toList();
                ocorrenciaId = etapas.abrirOcorrencia(camara, leitura, evidencias, agora).getId();
                ocorrenciaAberta = true;
            }
            confirmadas.add(new ResultadoDoLote(candidata, Desfecho.ACEITA, leitura, ocorrenciaId));
        }
        var ultima = confirmadas.getLast().leitura();
        if (camara.registrarResultadoTermico(ultima.getResultado().classificacao() == ClassificacaoTermica.INSEGURA,
                ocorrenciaAberta)) {
            camaras.salvar(camara);
        }
        return confirmadas;
    }

    private Camara revalidar(Grupo grupo) {
        long camaraId = grupo.snapshot().camaraId();
        try {
            var camara = etapas.bloquear(camaraId);
            camara.exigirAptaParaLeituras();
            for (var candidata : grupo.candidatas()) {
                if (etapas.jaAceita(candidata).isPresent()) {
                    throw new LeituraConcorrente(camaraId);
                }
            }
            var atual = etapas.snapshot(camara, etapas.ultimaDaCamara(camaraId), false);
            if (!atual.mesmoPontoQue(grupo.snapshot())) {
                throw new LeituraConcorrente(camaraId);
            }
            return camara;
        } catch (RecursoNaoEncontrado | EstadoIncompativel | LeituraConflitante alterada) {
            throw new LeituraConcorrente(camaraId);
        }
    }

    private static Preparacao rejeitar(List<ResultadoDoLote> definidos, List<NovaLeitura> candidatas, Desfecho motivo) {
        candidatas.forEach(candidata -> definidos.add(ResultadoDoLote.rejeitada(candidata, motivo)));
        return new Preparacao(definidos, null);
    }

    record Preparacao(List<ResultadoDoLote> definidos, Grupo grupo) {}

    record Grupo(SnapshotDaCamara snapshot, List<NovaLeitura> candidatas) {}
}

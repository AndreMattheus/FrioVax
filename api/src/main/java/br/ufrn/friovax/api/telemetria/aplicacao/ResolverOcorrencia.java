package br.ufrn.friovax.api.telemetria.aplicacao;

import br.ufrn.friovax.api.auditoria.dominio.ContextoDaOperacao;
import br.ufrn.friovax.api.auditoria.dominio.OperacaoAuditada;
import br.ufrn.friovax.api.auditoria.dominio.RecursoAuditado;
import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.CamaraRepository;
import br.ufrn.friovax.api.compartilhado.aplicacao.MudancaConfirmada;
import br.ufrn.friovax.api.compartilhado.aplicacao.PublicadorDeMudancas;
import br.ufrn.friovax.api.compartilhado.dominio.RecursoNaoEncontrado;
import br.ufrn.friovax.api.telemetria.dominio.Leitura;
import br.ufrn.friovax.api.telemetria.dominio.LeituraRepository;
import br.ufrn.friovax.api.telemetria.dominio.Ocorrencia;
import br.ufrn.friovax.api.telemetria.dominio.OcorrenciaRepository;
import br.ufrn.friovax.api.telemetria.dominio.ResolucaoConflitante;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;

@ApplicationScoped
public class ResolverOcorrencia {

    private final OcorrenciaRepository ocorrencias;
    private final LeituraRepository leituras;
    private final CamaraRepository camaras;
    private final Clock relogio;
    private final PublicadorDeMudancas publicador;
    private final ContextoDaOperacao contexto;

    @Inject
    public ResolverOcorrencia(OcorrenciaRepository ocorrencias, LeituraRepository leituras, CamaraRepository camaras,
                              Clock relogio, PublicadorDeMudancas publicador, ContextoDaOperacao contexto) {
        this.ocorrencias = ocorrencias;
        this.leituras = leituras;
        this.camaras = camaras;
        this.relogio = relogio;
        this.publicador = publicador;
        this.contexto = contexto;
    }

    @Transactional
    public Resultado resolver(long ocorrenciaId, String justificativa) {
        Ocorrencia.justificativaValidada(justificativa);
        var ator = contexto.ator();
        if (ator.isSistema()) {
            throw new IllegalStateException("A resolução de ocorrência exige um administrador autenticado.");
        }

        long camaraId = ocorrencias.camaraDaOcorrencia(ocorrenciaId)
                .orElseThrow(() -> new RecursoNaoEncontrado("Ocorrência", ocorrenciaId));
        Camara camara = camaras.buscarPorIdParaAlteracao(camaraId)
                .orElseThrow(() -> new IllegalStateException("Câmara " + camaraId + " da ocorrência ausente"));
        // Lida só depois do lock: uma resolução concorrente já foi confirmada ou espera esta terminar.
        var ocorrencia = ocorrencias.buscarPorId(ocorrenciaId).orElseThrow(
                () -> new RecursoNaoEncontrado("Ocorrência", ocorrenciaId));

        if (!ocorrencia.isAberta()) {
            if (ocorrencia.resolvidaCom(justificativa)) {
                return new Resultado(ocorrencia, false);
            }
            throw new ResolucaoConflitante(ocorrenciaId);
        }

        var abertura = leituras.buscarPorId(ocorrencia.getLeituraAberturaId()).orElseThrow(
                () -> new IllegalStateException("Leitura de abertura da ocorrência " + ocorrenciaId + " ausente"));
        var ultima = leituras.ultimaDaCamara(camaraId).orElse(abertura);
        var agora = OffsetDateTime.now(relogio);
        ocorrencia.resolver(justificativa, ultima, abertura, camara.getPolitica().getVersao(), ator.id(), agora);
        ocorrencias.salvar(ocorrencia);

        if (camara.registrarResultadoTermico(false, false)) {
            camaras.salvar(camara);
        }
        publicador.publicar(MudancaConfirmada.auditada(RecursoAuditado.OCORRENCIA, ocorrenciaId,
                OperacaoAuditada.RESOLUCAO_DE_OCORRENCIA, descricao(camara, ocorrencia, ultima), ator.id(), agora));
        return new Resultado(ocorrencia, true);
    }

    private static String descricao(Camara camara, Ocorrencia ocorrencia, Leitura ultima) {
        return "Ocorrência " + ocorrencia.getId() + " da câmara " + camara.getCodigo()
                + " resolvida após a leitura segura " + ultima.getIdExterno() + " (política v"
                + ultima.getPoliticaVersao() + "); estado térmico volta a NORMAL";
    }

    public record Resultado(Ocorrencia ocorrencia, boolean resolvidaAgora) {
    }
}

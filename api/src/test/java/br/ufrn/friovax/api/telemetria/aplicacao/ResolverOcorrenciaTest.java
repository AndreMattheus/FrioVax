package br.ufrn.friovax.api.telemetria.aplicacao;

import br.ufrn.friovax.api.auditoria.dominio.Ator;
import br.ufrn.friovax.api.auditoria.dominio.ContextoDaOperacao;
import br.ufrn.friovax.api.auditoria.dominio.OperacaoAuditada;
import br.ufrn.friovax.api.autenticacao.dominio.Perfil;
import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import br.ufrn.friovax.api.camara.dominio.EstadoTermico;
import br.ufrn.friovax.api.compartilhado.aplicacao.MudancaConfirmada;
import br.ufrn.friovax.api.compartilhado.dominio.RecursoNaoEncontrado;
import br.ufrn.friovax.api.compartilhado.dominio.ValidacaoDeNegocio;
import br.ufrn.friovax.api.suporte.AnaliseTermicaFalsa;
import br.ufrn.friovax.api.suporte.CamaraRepositoryEmMemoria;
import br.ufrn.friovax.api.suporte.LeituraRepositoryEmMemoria;
import br.ufrn.friovax.api.suporte.LoteRepositoryEmMemoria;
import br.ufrn.friovax.api.suporte.OcorrenciaRepositoryEmMemoria;
import br.ufrn.friovax.api.telemetria.dominio.ResolucaoConflitante;
import br.ufrn.friovax.api.telemetria.dominio.ResolucaoNaoPermitida;
import br.ufrn.friovax.api.telemetria.dominio.ResultadoTermico;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Resolução da ocorrência (S2-12) sobre leituras recebidas de verdade pelo caso de uso, sem banco. Faixa 2–8 °C e
 * N=3: três inseguras abrem a ocorrência e põem a câmara {@code EM_ALERTA}.
 */
class ResolverOcorrenciaTest {
    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-11-24T18:00:00Z"),
            ZoneId.of("America/Fortaleza"));
    private static final OffsetDateTime AGORA = OffsetDateTime.now(RELOGIO);
    private static final OffsetDateTime INICIO = AGORA.minusHours(1);

    private final UUID ana = UUID.randomUUID();
    private final UUID bruno = UUID.randomUUID();
    private final CamaraRepositoryEmMemoria camaras = new CamaraRepositoryEmMemoria();
    private final LeituraRepositoryEmMemoria leituras = new LeituraRepositoryEmMemoria();
    private final OcorrenciaRepositoryEmMemoria ocorrencias = new OcorrenciaRepositoryEmMemoria();
    private final List<MudancaConfirmada> mudancas = new ArrayList<>();
    private final AnaliseTermicaFalsa analise = new AnaliseTermicaFalsa();
    private final ReceberLeitura receber = new ReceberLeitura(
            new EtapasDaRecepcao(camaras, leituras, ocorrencias, new LoteRepositoryEmMemoria(), RELOGIO,
                    mudancas::add),
            analise, RELOGIO);

    private long camaraId;
    private int minuto;

    @BeforeEach
    void criarCamara() {
        camaraId = camaras.salvar(Camara.nova("CAM-01", "Câmara", "UBS", 1000, new BigDecimal("2.0"),
                new BigDecimal("8.0"), EstadoCamara.OPERACIONAL, AGORA.minusDays(1))).getId();
    }

    @Test
    void resolveDepoisDaUltimaLeituraSeguraComAutoriaInstanteEAuditoria() {
        long id = abrirOcorrencia();
        var segura = receber("S-4", "5.0").leitura();
        assertEquals(EstadoTermico.EM_ALERTA, camara().getEstadoTermico(), "a segura sozinha não fecha");

        var resultado = resolver(ana, id, "  Porta reparada; lotes avaliados  ");

        assertTrue(resultado.resolvidaAgora());
        var gravada = ocorrencias.buscarPorId(id).orElseThrow();
        assertFalse(gravada.isAberta());
        assertEquals(ana, gravada.getResolvidaPor().orElseThrow());
        assertEquals(AGORA, gravada.getResolvidaEm().orElseThrow());
        assertEquals("Porta reparada; lotes avaliados", gravada.getJustificativa().orElseThrow());
        assertEquals(segura.getId(), gravada.getLeituraResolucaoId().orElseThrow());
        assertEquals(EstadoTermico.NORMAL, camara().getEstadoTermico());

        var resolucoes = resolucoes();
        assertEquals(1, resolucoes.size());
        assertEquals(id, resolucoes.getFirst().recursoId());
        assertEquals(ana, resolucoes.getFirst().autorId());
        assertEquals(AGORA, resolucoes.getFirst().instante());
        assertEquals("Ocorrência " + id + " da câmara CAM-01 resolvida após a leitura segura S-4 (política v1); "
                + "estado térmico volta a NORMAL", resolucoes.getFirst().descricao());
    }

    @Test
    void recusaAntesDeUmaLeituraSeguraDepoisDaAbertura() {
        long id = abrirOcorrencia();

        assertThrows(ResolucaoNaoPermitida.class, () -> resolver(ana, id, "Porta reparada"));

        assertNadaMudou(id);
    }

    @Test
    void seguraAntigaSeguidaDeInseguraNaoAtendeACondicao() {
        long id = abrirOcorrencia();
        receber("S-4", "5.0");
        receber("S-5", "9.0");

        var erro = assertThrows(ResolucaoNaoPermitida.class, () -> resolver(ana, id, "Porta reparada"));

        assertTrue(erro.getMessage().contains("S-5, 9.0 °C) é insegura"), erro.getMessage());
        assertNadaMudou(id);
    }

    @Test
    void seguraDaPoliticaAnteriorNaoBastaMasUmaSeguraNaVigenteSim() {
        long id = abrirOcorrencia();
        receber("S-4", "5.0");
        var camara = camara();
        camara.alterarPolitica(new BigDecimal("2.0"), new BigDecimal("6.0"), 3, ana, AGORA.minusMinutes(30));
        camaras.salvar(camara);
        assertEquals(EstadoTermico.EM_ALERTA, camara().getEstadoTermico(), "nova versão mantém o alerta");

        var erro = assertThrows(ResolucaoNaoPermitida.class, () -> resolver(ana, id, "Porta reparada"));
        assertTrue(erro.getMessage().contains("política v1, e a vigente é a v2"), erro.getMessage());
        assertNadaMudou(id);

        var naVigente = receber("S-5", "5.5").leitura();
        assertEquals(2, naVigente.getPoliticaVersao());
        resolver(ana, id, "Porta reparada");
        assertEquals(naVigente.getId(),
                ocorrencias.buscarPorId(id).orElseThrow().getLeituraResolucaoId().orElseThrow());
        assertEquals(EstadoTermico.NORMAL, camara().getEstadoTermico());
    }

    @Test
    void reenvioIdenticoDevolveOTratamentoOriginalSemNovoEvento() {
        long id = abrirOcorrencia();
        receber("S-4", "5.0");
        resolver(ana, id, "Porta reparada");

        // Outro administrador reenvia o mesmo conteúdo um minuto depois: nada muda.
        var reenvio = new ResolverOcorrencia(ocorrencias, leituras, camaras,
                Clock.offset(RELOGIO, java.time.Duration.ofMinutes(1)), mudancas::add, contexto(bruno))
                .resolver(id, " Porta reparada ");

        assertFalse(reenvio.resolvidaAgora());
        assertEquals(ana, reenvio.ocorrencia().getResolvidaPor().orElseThrow());
        assertEquals(AGORA, reenvio.ocorrencia().getResolvidaEm().orElseThrow());
        assertEquals(1, resolucoes().size());
    }

    @Test
    void conteudoConflitanteRetornaConflitoEMantemOTratamentoOriginal() {
        long id = abrirOcorrencia();
        receber("S-4", "5.0");
        resolver(ana, id, "Porta reparada");

        assertThrows(ResolucaoConflitante.class, () -> resolver(bruno, id, "Outra justificativa"));

        var gravada = ocorrencias.buscarPorId(id).orElseThrow();
        assertEquals("Porta reparada", gravada.getJustificativa().orElseThrow());
        assertEquals(ana, gravada.getResolvidaPor().orElseThrow());
        assertEquals(1, resolucoes().size());
    }

    @Test
    void novaExcursaoAbreOutraOcorrenciaComSequenciaZeradaSemTocarNaResolvida() {
        long primeira = abrirOcorrencia();
        receber("S-4", "5.0");
        resolver(ana, primeira, "Porta reparada");
        var resolvida = ocorrencias.buscarPorId(primeira).orElseThrow();

        assertEquals(ResultadoTermico.insegura(1, false), receber("S-5", "9.0").leitura().getResultado(),
                "a sequência recomeça depois da resolução");
        assertEquals(EstadoTermico.ATENCAO, camara().getEstadoTermico());
        receber("S-6", "9.0");
        var abriu = receber("S-7", "9.0").ocorrenciaAbertaId();

        assertNotNull(abriu);
        assertTrue(abriu != primeira, "outro id");
        assertEquals(EstadoTermico.EM_ALERTA, camara().getEstadoTermico());
        var depois = ocorrencias.buscarPorId(primeira).orElseThrow();
        assertFalse(depois.isAberta(), "a resolvida não é reaberta");
        assertEquals(resolvida.getJustificativa(), depois.getJustificativa());
        assertEquals(resolvida.getLeituraResolucaoId(), depois.getLeituraResolucaoId());
        assertEquals(resolvida.getEvidencias(), depois.getEvidencias());
        assertEquals(3, ocorrencias.buscarPorId(abriu).orElseThrow().getEvidencias().size(),
                "a nova não herda evidências do episódio anterior");
    }

    @Test
    void leituraInseguraEmAnaliseDuranteAResolucaoEhRefeitaNoEpisodioNovo() {
        long id = abrirOcorrencia();
        receber("S-4", "5.0");
        // A resolução é confirmada enquanto S-5 está no Go: o snapshot de S-5 muda e o ciclo é refeito.
        var executada = new boolean[1];
        analise.durante(solicitacao -> {
            if (!executada[0]) {
                executada[0] = true;
                resolver(ana, id, "Porta reparada");
            }
        });

        var recebida = receber("S-5", "9.0").leitura();

        assertEquals(ResultadoTermico.insegura(1, false), recebida.getResultado(),
                "não usa o contexto do episódio encerrado");
        assertFalse(ocorrencias.buscarPorId(id).orElseThrow().isAberta(), "a leitura não desfaz a resolução");
        assertTrue(ocorrencias.abertaDaCamara(camaraId).isEmpty());
        assertEquals(EstadoTermico.ATENCAO, camara().getEstadoTermico(),
                "o estado segue a leitura mais recente, não a resolução");
    }

    @Test
    void justificativaEmBrancoOuLongaEhRecusadaAntesDeQualquerCoisa() {
        long id = abrirOcorrencia();
        receber("S-4", "5.0");

        assertEquals("justificativa",
                assertThrows(ValidacaoDeNegocio.class, () -> resolver(ana, id, "   ")).campo());
        assertThrows(ValidacaoDeNegocio.class, () -> resolver(ana, id, null));
        assertThrows(ValidacaoDeNegocio.class, () -> resolver(ana, id, "x".repeat(501)));
        assertThrows(ValidacaoDeNegocio.class, () -> resolver(ana, 999, " "), "422 antes do 404");
        assertNadaMudou(id);
    }

    @Test
    void ocorrenciaInexistenteRetornaNaoEncontrada() {
        assertThrows(RecursoNaoEncontrado.class, () -> resolver(ana, 999, "Porta reparada"));
    }

    @Test
    void semUsuarioAutenticadoNaoResolve() {
        long id = abrirOcorrencia();
        receber("S-4", "5.0");
        var sistema = new ResolverOcorrencia(ocorrencias, leituras, camaras, RELOGIO, mudancas::add,
                ContextoDaOperacao.doSistema());

        assertThrows(IllegalStateException.class, () -> sistema.resolver(id, "Porta reparada"));
        assertNadaMudou(id);
    }

    private long abrirOcorrencia() {
        receber("S-1", "9.0");
        receber("S-2", "9.0");
        var id = receber("S-3", "9.0").ocorrenciaAbertaId();
        assertNotNull(id);
        assertEquals(EstadoTermico.EM_ALERTA, camara().getEstadoTermico());
        return id;
    }

    private void assertNadaMudou(long id) {
        var ocorrencia = ocorrencias.buscarPorId(id).orElseThrow();
        assertTrue(ocorrencia.isAberta());
        assertTrue(ocorrencia.getResolvidaPor().isEmpty());
        assertEquals(EstadoTermico.EM_ALERTA, camara().getEstadoTermico());
        assertTrue(resolucoes().isEmpty());
    }

    private ResolverOcorrencia.Resultado resolver(UUID admin, long id, String justificativa) {
        return new ResolverOcorrencia(ocorrencias, leituras, camaras, RELOGIO, mudancas::add, contexto(admin))
                .resolver(id, justificativa);
    }

    private static ContextoDaOperacao contexto(UUID admin) {
        return ContextoDaOperacao.de(Ator.usuario(admin, "admin-" + admin, Perfil.ADMINISTRADOR));
    }

    private List<MudancaConfirmada> resolucoes() {
        return mudancas.stream().filter(m -> m.operacao() == OperacaoAuditada.RESOLUCAO_DE_OCORRENCIA).toList();
    }

    private LeituraRecebida receber(String idExterno, String temperatura) {
        return receber.receber(camaraId, idExterno, new BigDecimal(temperatura), INICIO.plusMinutes(minuto++),
                "sensor-1");
    }

    private Camara camara() {
        return camaras.buscarPorId(camaraId).orElseThrow();
    }
}

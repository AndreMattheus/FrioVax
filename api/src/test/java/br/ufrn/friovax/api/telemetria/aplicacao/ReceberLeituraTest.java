package br.ufrn.friovax.api.telemetria.aplicacao;

import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import br.ufrn.friovax.api.compartilhado.aplicacao.MudancaConfirmada;
import br.ufrn.friovax.api.compartilhado.dominio.EstadoIncompativel;
import br.ufrn.friovax.api.compartilhado.dominio.Paginacao;
import br.ufrn.friovax.api.compartilhado.dominio.RecursoNaoEncontrado;
import br.ufrn.friovax.api.compartilhado.dominio.ValidacaoDeNegocio;
import br.ufrn.friovax.api.suporte.AnaliseTermicaFalsa;
import br.ufrn.friovax.api.suporte.CamaraRepositoryEmMemoria;
import br.ufrn.friovax.api.suporte.LeituraRepositoryEmMemoria;
import br.ufrn.friovax.api.suporte.OcorrenciaRepositoryEmMemoria;
import br.ufrn.friovax.api.telemetria.dominio.AnaliseIndisponivel;
import br.ufrn.friovax.api.telemetria.dominio.FalhaDeIntegracao;
import br.ufrn.friovax.api.telemetria.dominio.Leitura;
import br.ufrn.friovax.api.telemetria.dominio.LeituraConcorrente;
import br.ufrn.friovax.api.telemetria.dominio.LeituraConflitante;
import br.ufrn.friovax.api.telemetria.dominio.LeituraDaAnalise;
import br.ufrn.friovax.api.telemetria.dominio.LeituraForaDeOrdem;
import br.ufrn.friovax.api.telemetria.dominio.Ocorrencia;
import br.ufrn.friovax.api.telemetria.dominio.ResultadoDaAnalise;
import br.ufrn.friovax.api.telemetria.dominio.ResultadoDaJanela;
import br.ufrn.friovax.api.telemetria.dominio.ResultadoTermico;
import br.ufrn.friovax.api.telemetria.dominio.SolicitacaoDeAnalise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Recepção individual com faixa 2–8 °C e N=3, sem banco nem gRPC (ADR 0003, seções 3 e 5). */
class ReceberLeituraTest {

    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-11-20T15:00:00Z"),
            ZoneId.of("America/Fortaleza"));
    private static final OffsetDateTime AGORA = OffsetDateTime.now(RELOGIO);
    private static final OffsetDateTime INICIO = AGORA.minusHours(1);

    private final CamaraRepositoryEmMemoria camaras = new CamaraRepositoryEmMemoria();
    private final LeituraRepositoryEmMemoria leituras = new LeituraRepositoryEmMemoria();
    private final OcorrenciaRepositoryEmMemoria ocorrencias = new OcorrenciaRepositoryEmMemoria();
    private final AnaliseTermicaFalsa analise = new AnaliseTermicaFalsa();
    private final List<MudancaConfirmada> mudancas = new ArrayList<>();
    private final ReceberLeitura receber = new ReceberLeitura(
            new EtapasDaRecepcao(camaras, leituras, ocorrencias, RELOGIO, mudancas::add), analise, RELOGIO);

    private long camaraId;

    @BeforeEach
    void criarCamara() {
        camaraId = camaraNova("CAM-01", EstadoCamara.OPERACIONAL);
    }

    @Test
    void primeiraAceitacaoGravaEntradaPoliticaEResultado() {
        var recebida = receber("S-1", 0, "9.0");

        assertTrue(recebida.criada());
        var leitura = recebida.leitura();
        assertEquals(leitura.getId(), leituras.buscarPorChave(camaraId, "S-1").orElseThrow().getId());
        assertEquals(1, leitura.getPoliticaVersao());
        assertEquals(ResultadoTermico.insegura(1, false), leitura.getResultado());
        assertEquals("sensor-1", leitura.getOrigem());
        assertTrue(leitura.getAceitaEm().isEqual(AGORA));
        assertEquals(1, analise.chamadas());
        assertEquals(List.of("leitura"), mudancas.stream().map(MudancaConfirmada::recurso).toList());
    }

    @Test
    void contextoDaPoliticaVigenteCompletaASequenciaAteOLimiar() {
        receber("S-1", 0, "9.0");
        receber("S-2", 1, "1.0");

        var terceira = receber("S-3", 2, "10.0").leitura();

        assertEquals(ResultadoTermico.insegura(3, true), terceira.getResultado());
        var janela = analise.ultima().janelas().getFirst();
        assertEquals(List.of("S-1", "S-2"), janela.contexto().stream().map(LeituraDaAnalise::idExterno).toList());
        assertEquals(List.of("S-3"), janela.candidatas().stream().map(LeituraDaAnalise::idExterno).toList());
    }

    @Test
    void reenvioIdenticoDevolveOMesmoRegistroSemChamarOGo() {
        var primeira = receber("S-1", 0, "9.0").leitura();

        var reenvio = receber("S-1", 0, "9.0");

        assertFalse(reenvio.criada());
        assertEquals(primeira.getId(), reenvio.leitura().getId());
        assertEquals(1, analise.chamadas());
        assertEquals(1, mudancas.size());
    }

    @Test
    void conteudoEquivalenteComOutraEscalaOffsetEEspacosNaOrigemEhReenvio() {
        var primeira = receber("S-1", 0, "8.5").leitura();
        var mesmoInstanteEmUtc = INICIO.withOffsetSameInstant(ZoneOffset.UTC);

        var reenvio = receber.receber(camaraId, "S-1", new BigDecimal("8.50"), mesmoInstanteEmUtc, "  sensor-1 ");

        assertFalse(reenvio.criada());
        assertEquals(primeira.getId(), reenvio.leitura().getId());
        assertEquals(1, analise.chamadas());
    }

    @Test
    void mesmaChaveComOutroConteudoEhConflitoSemAlteracao() {
        receber("S-1", 0, "9.0");

        assertThrows(LeituraConflitante.class, () -> receber("S-1", 0, "9.1"));
        assertThrows(LeituraConflitante.class, () -> receber("S-1", 1, "9.0"));
        assertThrows(LeituraConflitante.class,
                () -> receber.receber(camaraId, "S-1", new BigDecimal("9.0"), INICIO, "sensor-2"));

        assertEquals(1, total());
        assertEquals(1, analise.chamadas());
    }

    @Test
    void idExternoNaoEhNormalizado() {
        receber("S-1", 0, "5.0");

        assertTrue(receber("s-1", 1, "5.0").criada());
        assertEquals(2, total());
    }

    @Test
    void instanteIgualOuAnteriorAUltimaLeituraEhForaDeOrdem() {
        receber("S-1", 10, "5.0");

        assertThrows(LeituraForaDeOrdem.class, () -> receber("S-2", 10, "5.0"));
        assertThrows(LeituraForaDeOrdem.class, () -> receber("S-3", 9, "5.0"));

        assertEquals(1, total());
        assertEquals(1, analise.chamadas());
    }

    @Test
    void camaraInexistenteInativaOuForaDeOperacaoNaoChamaOGo() {
        var manutencao = camaraNova("CAM-02", EstadoCamara.MANUTENCAO);
        var inativa = camaraNova("CAM-03", EstadoCamara.OPERACIONAL);
        var camara = camaras.buscarPorId(inativa).orElseThrow();
        camara.inativar(false, AGORA);
        camaras.salvar(camara);

        assertThrows(RecursoNaoEncontrado.class, () -> receber.receber(999, "S-1", BigDecimal.ONE, INICIO, "s"));
        assertThrows(EstadoIncompativel.class, () -> receber.receber(manutencao, "S-1", BigDecimal.ONE, INICIO, "s"));
        assertThrows(EstadoIncompativel.class, () -> receber.receber(inativa, "S-1", BigDecimal.ONE, INICIO, "s"));

        assertEquals(0, analise.chamadas());
    }

    @Test
    void reenvioDeLeituraJaAceitaContinuaRecuperavelComCamaraForaDeOperacao() {
        var primeira = receber("S-1", 0, "5.0").leitura();
        var camara = camaras.buscarPorId(camaraId).orElseThrow();
        camara.atualizar("Câmara", "UBS", 1000, new BigDecimal("2.0"), new BigDecimal("8.0"),
                EstadoCamara.MANUTENCAO, 0, false, AGORA);
        camaras.salvar(camara);

        assertEquals(primeira.getId(), receber("S-1", 0, "5.0").leitura().getId());
        assertThrows(EstadoIncompativel.class, () -> receber("S-2", 1, "5.0"));
    }

    @Test
    void toleranciaFuturaDeCincoMinutosUsaORelogioDaApi() {
        assertTrue(receber.receber(camaraId, "S-1", new BigDecimal("5.0"), AGORA.plusMinutes(5), "s").criada());

        var falha = assertThrows(ValidacaoDeNegocio.class, () -> receber.receber(camaraId, "S-2",
                new BigDecimal("5.0"), AGORA.plusMinutes(5).plusNanos(1_000), "s"));

        assertEquals("medidaEm", falha.campo());
        assertEquals(1, analise.chamadas());
    }

    @Test
    void camposInvalidosSaoRecusadosAntesDaAnalise() {
        assertEquals("temperatura", campo(() -> receber("S-1", 0, "8.05")));
        assertEquals("idExterno", campo(() -> receber.receber(camaraId, " ", BigDecimal.ONE, INICIO, "s")));
        assertEquals("origem", campo(() -> receber.receber(camaraId, "S-1", BigDecimal.ONE, INICIO, "  ")));
        assertEquals("medidaEm", campo(() -> receber.receber(camaraId, "S-1", BigDecimal.ONE, null, "s")));
        assertEquals(0, analise.chamadas());
    }

    @Test
    void goIndisponivelNaoConfirmaLeitura() {
        analise.falhar(new AnaliseIndisponivel(AnaliseIndisponivel.Motivo.PRAZO_ESGOTADO));

        assertThrows(AnaliseIndisponivel.class, () -> receber("S-1", 0, "9.0"));

        assertEquals(0, total());
        assertTrue(mudancas.isEmpty());
    }

    @Test
    void janelaRecusadaPeloGoEhFalhaDeIntegracaoSemGravar() {
        analise.responder(solicitacao -> new ResultadoDaAnalise(solicitacao.correlacaoId(),
                List.of(new ResultadoDaJanela.Rejeitada(camaraId, ResultadoDaJanela.MotivoDaRejeicao.JANELA_INVALIDA,
                        "janela inválida"))));

        assertThrows(FalhaDeIntegracao.class, () -> receber("S-1", 0, "9.0"));

        assertEquals(0, total());
    }

    @Test
    void leituraAceitaDuranteAAnaliseEntraNoContextoDaSegundaTentativa() {
        receber("S-1", 0, "9.0");
        umaVez(solicitacao -> leituras.salvar(Leitura.nova(camaraId, "S-2", INICIO.plusMinutes(1),
                new BigDecimal("9.0"), "outro", 1, ResultadoTermico.insegura(2, false), AGORA)));

        var recebida = receber("S-3", 2, "9.0");

        assertTrue(recebida.criada());
        assertEquals(ResultadoTermico.insegura(3, true), recebida.leitura().getResultado());
        assertEquals(3, analise.chamadas());
        var contextos = analise.solicitacoes().subList(1, 3).stream()
                .map(s -> s.janelas().getFirst().contexto().stream().map(LeituraDaAnalise::idExterno).toList())
                .toList();
        assertEquals(List.of(List.of("S-1"), List.of("S-1", "S-2")), contextos);
    }

    @Test
    void politicaAlteradaDuranteAAnaliseRefazOCicloNaNovaVersao() {
        receber("S-1", 0, "9.0");
        umaVez(solicitacao -> alterarFaixa("0.0", "10.0"));

        var recebida = receber("S-2", 1, "9.0").leitura();

        assertEquals(2, recebida.getPoliticaVersao());
        assertEquals(ResultadoTermico.segura(), recebida.getResultado());
        assertTrue(analise.ultima().janelas().getFirst().contexto().isEmpty());
    }

    @Test
    void snapshotAlteradoNasDuasAnalisesEhConflitoSemGravar() {
        var faixas = new ArrayList<>(List.of("10.0", "11.0"));
        analise.durante(solicitacao -> alterarFaixa("0.0", faixas.removeFirst()));

        assertThrows(LeituraConcorrente.class, () -> receber("S-1", 0, "9.0"));

        assertEquals(2, analise.chamadas());
        assertEquals(0, total());
        assertTrue(mudancas.isEmpty());
    }

    @Test
    void mesmaChaveConfirmadaPorOutraRequisicaoDuranteAAnaliseDevolveOOriginal() {
        var concorrente = new long[1];
        analise.durante(solicitacao -> concorrente[0] = leituras.salvar(Leitura.nova(camaraId, "S-1", INICIO,
                new BigDecimal("9.0"), "sensor-1", 1, ResultadoTermico.insegura(1, false), AGORA)).getId());

        var recebida = receber("S-1", 0, "9.0");

        assertFalse(recebida.criada());
        assertEquals(concorrente[0], recebida.leitura().getId());
        assertEquals(1, analise.chamadas());
        assertEquals(1, total());
    }

    @Test
    void mesmaChaveConfirmadaComOutroConteudoDuranteAAnaliseEhConflito() {
        analise.durante(solicitacao -> leituras.salvar(Leitura.nova(camaraId, "S-1", INICIO,
                new BigDecimal("4.0"), "sensor-1", 1, ResultadoTermico.segura(), AGORA)));

        assertThrows(LeituraConflitante.class, () -> receber("S-1", 0, "9.0"));

        assertEquals(1, total());
    }

    @Test
    void camaraTiradaDeOperacaoDuranteAAnaliseRecusaALeitura() {
        analise.durante(solicitacao -> {
            var camara = camaras.buscarPorId(camaraId).orElseThrow();
            camara.inativar(false, AGORA);
            camaras.salvar(camara);
        });

        assertThrows(EstadoIncompativel.class, () -> receber("S-1", 0, "9.0"));

        assertEquals(0, total());
    }

    @Test
    void contextoNaoAtravessaAResolucaoDoEpisodio() {
        receber("S-1", 0, "9.0");
        receber("S-2", 1, "9.0");
        var abertura = receber("S-3", 2, "9.0").leitura();
        var segura = receber("S-4", 3, "5.0").leitura();
        var ocorrencia = ocorrencias.salvar(Ocorrencia.abrir(camaraId, 1, abertura.getId(),
                List.of(1L, 2L, abertura.getId()), List.of(), false, AGORA));
        ocorrencia.resolver("Equipamento verificado", segura.getId(), null, AGORA);
        ocorrencias.salvar(ocorrencia);

        receber("S-5", 4, "9.0");

        assertTrue(analise.ultima().janelas().getFirst().contexto().isEmpty());
    }

    @Test
    void resolucaoConcorrenteDuranteAAnaliseRefazOCicloSemOContextoAntigo() {
        receber("S-1", 0, "9.0");
        receber("S-2", 1, "9.0");
        var abertura = receber("S-3", 2, "9.0").leitura();
        var ocorrencia = ocorrencias.salvar(Ocorrencia.abrir(camaraId, 1, abertura.getId(),
                List.of(1L, 2L, abertura.getId()), List.of(), false, AGORA));
        umaVez(solicitacao -> {
            ocorrencia.resolver("Equipamento verificado", abertura.getId(), null, AGORA);
            ocorrencias.salvar(ocorrencia);
        });

        var recebida = receber("S-4", 3, "9.0").leitura();

        assertEquals(ResultadoTermico.insegura(1, false), recebida.getResultado());
        assertEquals(5, analise.chamadas());
        assertTrue(analise.ultima().janelas().getFirst().contexto().isEmpty());
    }

    private LeituraRecebida receber(String idExterno, int minutos, String temperatura) {
        return receber.receber(camaraId, idExterno, new BigDecimal(temperatura), INICIO.plusMinutes(minutos),
                "sensor-1");
    }

    /** A ação roda só na próxima análise, como uma requisição concorrente confirmada durante a RPC. */
    private void umaVez(Consumer<SolicitacaoDeAnalise> acao) {
        var executada = new boolean[1];
        analise.durante(solicitacao -> {
            if (!executada[0]) {
                executada[0] = true;
                acao.accept(solicitacao);
            }
        });
    }

    private void alterarFaixa(String minima, String maxima) {
        var camara = camaras.buscarPorId(camaraId).orElseThrow();
        camara.alterarPolitica(new BigDecimal(minima), new BigDecimal(maxima), 3, null, AGORA);
        camaras.salvar(camara);
    }

    private long camaraNova(String codigo, EstadoCamara estado) {
        return camaras.salvar(Camara.nova(codigo, "Câmara", "UBS", 1000, new BigDecimal("2.0"),
                new BigDecimal("8.0"), estado, AGORA.minusDays(1))).getId();
    }

    private long total() {
        return leituras.listarDaCamara(camaraId, new Paginacao(0, 100)).totalElementos();
    }

    private static String campo(Runnable acao) {
        return assertThrows(ValidacaoDeNegocio.class, acao::run).campo();
    }
}

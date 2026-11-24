package br.ufrn.friovax.api.telemetria.aplicacao;

import br.ufrn.friovax.api.auditoria.dominio.OperacaoAuditada;
import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import br.ufrn.friovax.api.camara.dominio.EstadoTermico;
import br.ufrn.friovax.api.compartilhado.aplicacao.MudancaConfirmada;
import br.ufrn.friovax.api.lote.dominio.Lote;
import br.ufrn.friovax.api.lote.dominio.MotivoBaixa;
import br.ufrn.friovax.api.suporte.AnaliseTermicaFalsa;
import br.ufrn.friovax.api.suporte.CamaraRepositoryEmMemoria;
import br.ufrn.friovax.api.suporte.LeituraRepositoryEmMemoria;
import br.ufrn.friovax.api.suporte.LoteRepositoryEmMemoria;
import br.ufrn.friovax.api.suporte.OcorrenciaRepositoryEmMemoria;
import br.ufrn.friovax.api.telemetria.dominio.LoteAfetado;
import br.ufrn.friovax.api.telemetria.dominio.ResultadoTermico;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Abertura da ocorrência na leitura que atinge o limiar e estado térmico da câmara (S2-11), sem banco. */
class AberturaDeOcorrenciaTest {
    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-11-24T15:00:00Z"),
            ZoneId.of("America/Fortaleza"));
    private static final OffsetDateTime AGORA = OffsetDateTime.now(RELOGIO);
    private static final OffsetDateTime INICIO = AGORA.minusHours(1);

    private final CamaraRepositoryEmMemoria camaras = new CamaraRepositoryEmMemoria();
    private final LeituraRepositoryEmMemoria leituras = new LeituraRepositoryEmMemoria();
    private final OcorrenciaRepositoryEmMemoria ocorrencias = new OcorrenciaRepositoryEmMemoria();
    private final LoteRepositoryEmMemoria lotes = new LoteRepositoryEmMemoria();
    private final List<MudancaConfirmada> mudancas = new ArrayList<>();
    private final ReceberLeitura receber = new ReceberLeitura(
            new EtapasDaRecepcao(camaras, leituras, ocorrencias, lotes, RELOGIO, mudancas::add),
            new AnaliseTermicaFalsa(), RELOGIO);

    private long camaraId;
    private int minuto;

    @BeforeEach
    void criarCamara() {
        camaraId = camaras.salvar(Camara.nova("CAM-01", "Câmara", "UBS", 1000, new BigDecimal("2.0"),
                new BigDecimal("8.0"), EstadoCamara.OPERACIONAL, AGORA.minusDays(1))).getId();
    }

    @Test
    void leituraNAbreUmaOcorrenciaComEvidenciasFotografiaEAlerta() {
        long lote = lote("L-1", 40, camaraId);
        long outraCamara = camaras.salvar(Camara.nova("CAM-02", "Outra", "UBS", 1000, new BigDecimal("2.0"),
                new BigDecimal("8.0"), EstadoCamara.OPERACIONAL, AGORA.minusDays(1))).getId();
        lote("L-OUTRA", 10, outraCamara);

        var primeira = receber("S-1", "9.0");
        var segunda = receber("S-2", "1.0");
        assertNull(segunda.ocorrenciaAbertaId(), "N-1 não abre");
        assertTrue(ocorrencias.abertaDaCamara(camaraId).isEmpty());
        assertEquals(EstadoTermico.ATENCAO, camara().getEstadoTermico());

        var terceira = receber("S-3", "10.0");

        assertEquals(ResultadoTermico.insegura(3, true), terceira.leitura().getResultado());
        assertNotNull(terceira.ocorrenciaAbertaId());
        var ocorrencia = ocorrencias.abertaDaCamara(camaraId).orElseThrow();
        assertEquals(terceira.ocorrenciaAbertaId(), ocorrencia.getId());
        assertEquals(terceira.leitura().getId(), ocorrencia.getLeituraAberturaId());
        assertEquals(List.of(primeira.leitura().getId(), segunda.leitura().getId(), terceira.leitura().getId()),
                ocorrencia.getEvidencias());
        assertEquals(1, ocorrencia.getPoliticaVersao());
        assertEquals(List.of(new LoteAfetado(lote, "L-1", "Vacina", 40)), ocorrencia.getLotesAfetados(),
                "só lotes desta câmara");

        var camara = camara();
        assertEquals(EstadoTermico.EM_ALERTA, camara.getEstadoTermico());
        assertEquals(EstadoCamara.OPERACIONAL, camara.getEstado(), "o estado operacional não muda");
        assertTrue(camara.isAtivo());
        assertEquals(40, lotes.buscarPorId(lote).orElseThrow().getQuantidade(), "as doses não mudam");

        var abertura = mudancas.stream().filter(m -> m.recurso().equals("ocorrencia")).toList();
        assertEquals(1, abertura.size());
        assertEquals(OperacaoAuditada.ABERTURA_DE_OCORRENCIA, abertura.getFirst().operacao());
        assertEquals(ocorrencia.getId(), abertura.getFirst().recursoId());
        assertEquals("Ocorrência aberta na câmara CAM-01 pela leitura S-3 após 3 leituras inseguras (política v1); "
                + "1 lote na fotografia", abertura.getFirst().descricao());
    }

    @Test
    void leiturasAdicionaisESeguraDepoisNaoDuplicamNemFecham() {
        receber("S-1", "9.0");
        receber("S-2", "9.0");
        var abriu = receber("S-3", "9.0").ocorrenciaAbertaId();

        var quarta = receber("S-4", "9.0");
        assertTrue(quarta.leitura().getResultado().limiarAtingido(), "o Go mantém o sinal");
        assertNull(quarta.ocorrenciaAbertaId(), "o sinal não abre outra");
        var segura = receber("S-5", "5.0");
        assertNull(segura.ocorrenciaAbertaId());

        assertEquals(abriu, ocorrencias.abertaDaCamara(camaraId).orElseThrow().getId());
        assertEquals(1, mudancas.stream().filter(m -> m.recurso().equals("ocorrencia")).count());
        assertEquals(EstadoTermico.EM_ALERTA, camara().getEstadoTermico(), "segura não fecha o alerta");
    }

    @Test
    void reenvioDaLeituraQueAbriuDevolveAMesmaOcorrenciaSemAbrirOutra() {
        receber("S-1", "9.0");
        receber("S-2", "9.0");
        var original = receber("S-3", "9.0");
        int eventos = mudancas.size();

        var reenvio = receber.receber(camaraId, "S-3", new BigDecimal("9.0"), INICIO.plusMinutes(2), "sensor-1");

        assertFalse(reenvio.criada());
        assertEquals(original.ocorrenciaAbertaId(), reenvio.ocorrenciaAbertaId());
        assertEquals(eventos, mudancas.size());
    }

    @Test
    void limiarUmAbreNaPrimeiraInsegura() {
        var camara = camara();
        camara.alterarPolitica(new BigDecimal("2.0"), new BigDecimal("8.0"), 1, null, AGORA.minusHours(2));
        camaras.salvar(camara);

        var primeira = receber("S-1", "8.1");

        assertNotNull(primeira.ocorrenciaAbertaId());
        assertEquals(List.of(primeira.leitura().getId()),
                ocorrencias.abertaDaCamara(camaraId).orElseThrow().getEvidencias());
    }

    @Test
    void estadoTermicoSegueAsLeiturasSemOcorrencia() {
        assertEquals(EstadoTermico.SEM_LEITURAS, camara().getEstadoTermico());
        receber("S-1", "5.0");
        assertEquals(EstadoTermico.NORMAL, camara().getEstadoTermico());
        receber("S-2", "8.5");
        assertEquals(EstadoTermico.ATENCAO, camara().getEstadoTermico());
        receber("S-3", "2.0");
        assertEquals(EstadoTermico.NORMAL, camara().getEstadoTermico(), "limite da faixa é seguro");
        assertTrue(ocorrencias.abertaDaCamara(camaraId).isEmpty());
    }

    @Test
    void fotografiaIgnoraLotesEsgotadosDescartadosEInativos() {
        long disponivel = lote("L-OK", 10, camaraId);
        var esgotado = lotes.buscarPorId(lote("L-ESG", 5, camaraId)).orElseThrow();
        esgotado.darBaixa(5, MotivoBaixa.ADMINISTRADA, AGORA);
        lotes.salvar(esgotado);
        var descartado = lotes.buscarPorId(lote("L-DESC", 5, camaraId)).orElseThrow();
        descartado.descartar(AGORA);
        lotes.salvar(descartado);
        var inativo = lotes.buscarPorId(lote("L-INAT", 5, camaraId)).orElseThrow();
        inativo.inativar(AGORA);
        lotes.salvar(inativo);

        receber("S-1", "9.0");
        receber("S-2", "9.0");
        receber("S-3", "9.0");

        assertEquals(List.of(disponivel), ocorrencias.abertaDaCamara(camaraId).orElseThrow().getLotesAfetados()
                .stream().map(LoteAfetado::loteId).toList());
    }

    private LeituraRecebida receber(String idExterno, String temperatura) {
        return receber.receber(camaraId, idExterno, new BigDecimal(temperatura), INICIO.plusMinutes(minuto++),
                "sensor-1");
    }

    private Camara camara() {
        return camaras.buscarPorId(camaraId).orElseThrow();
    }

    private long lote(String codigo, int quantidade, long camara) {
        return lotes.salvar(Lote.novo(codigo, "Vacina", "Fabricante", LocalDate.of(2030, 1, 1), quantidade, camara,
                AGORA.toLocalDate(), AGORA)).getId();
    }
}

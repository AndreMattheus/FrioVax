package br.ufrn.friovax.api.lote.aplicacao;

import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.CamaraEmAlerta;
import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import br.ufrn.friovax.api.compartilhado.aplicacao.MudancaConfirmada;
import br.ufrn.friovax.api.compartilhado.dominio.EstadoIncompativel;
import br.ufrn.friovax.api.lote.dominio.EstadoLote;
import br.ufrn.friovax.api.lote.dominio.MotivoBaixa;
import br.ufrn.friovax.api.suporte.CamaraRepositoryEmMemoria;
import br.ufrn.friovax.api.suporte.LoteRepositoryEmMemoria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Proteção dos lotes de uma câmara {@code EM_ALERTA} (S2-11, ADR 0003, seção 4), sem banco. */
class LotesEmCamaraEmAlertaTest {
    private static final OffsetDateTime AGORA = OffsetDateTime.parse("2026-11-24T10:00:00-03:00");
    private static final Clock RELOGIO = Clock.fixed(AGORA.toInstant(), AGORA.getOffset());
    private static final LocalDate VALIDADE = LocalDate.of(2030, 1, 1);

    private final CamaraRepositoryEmMemoria camaras = new CamaraRepositoryEmMemoria();
    private final LoteRepositoryEmMemoria lotes = new LoteRepositoryEmMemoria();
    private final List<MudancaConfirmada> publicadas = new ArrayList<>();
    private final CadastrarLote cadastrar = new CadastrarLote(lotes, camaras, RELOGIO, publicadas::add);
    private final AtualizarLote atualizar = new AtualizarLote(lotes, camaras, RELOGIO, publicadas::add);
    private final RegistrarBaixaLote baixa = new RegistrarBaixaLote(lotes, camaras, RELOGIO, publicadas::add);
    private final DescartarLote descartar = new DescartarLote(lotes, RELOGIO, publicadas::add);
    private final InativarLote inativar = new InativarLote(lotes, RELOGIO, publicadas::add);

    private long emAlerta;
    private long normal;
    private long lote;

    @BeforeEach
    void preparar() {
        emAlerta = camara("CAM-ALERTA");
        normal = camara("CAM-NORMAL");
        lote = cadastrar.cadastrar("L-1", "Vacina", "Fabricante", VALIDADE, 100, emAlerta).getId();
        var camara = camaras.buscarPorId(emAlerta).orElseThrow();
        camara.registrarResultadoTermico(true, true);
        camaras.salvar(camara);
        publicadas.clear();
    }

    @Test
    void novoLoteNaCamaraEmAlertaEhRecusado() {
        var erro = assertThrows(CamaraEmAlerta.class,
                () -> cadastrar.cadastrar("L-2", "Vacina", "Fabricante", VALIDADE, 10, emAlerta));
        assertInstanceOf(EstadoIncompativel.class, erro, "responde 409");
        assertFalse(lotes.existePorCodigo("L-2"));
        assertTrue(publicadas.isEmpty());

        cadastrar.cadastrar("L-3", "Vacina", "Fabricante", VALIDADE, 10, normal);
    }

    @Test
    void aumentoDeDosesEMovimentacaoParaACamaraEmAlertaSaoRecusados() {
        assertThrows(CamaraEmAlerta.class,
                () -> atualizar.atualizar(lote, "Vacina", "Fabricante", VALIDADE, 101, emAlerta));
        long outro = cadastrar.cadastrar("L-2", "Vacina", "Fabricante", VALIDADE, 10, normal).getId();
        publicadas.clear();
        assertThrows(CamaraEmAlerta.class,
                () -> atualizar.atualizar(outro, "Vacina", "Fabricante", VALIDADE, 10, emAlerta));

        assertEquals(100, lotes.buscarPorId(lote).orElseThrow().getQuantidade());
        assertEquals(normal, lotes.buscarPorId(outro).orElseThrow().getCamaraId());
        assertTrue(publicadas.isEmpty());
    }

    @Test
    void editarSemAumentarEMoverParaForaContinuamPermitidos() {
        // Reduzir doses é por baixa (§4.2), não por PUT; editar dados cadastrais mantendo as doses é permitido.
        atualizar.atualizar(lote, "Vacina atenuada", "Fabricante", VALIDADE, 100, emAlerta);
        atualizar.atualizar(lote, "Vacina atenuada", "Fabricante", VALIDADE, 100, normal);

        var movido = lotes.buscarPorId(lote).orElseThrow();
        assertEquals(normal, movido.getCamaraId());
        assertEquals("Vacina atenuada", movido.getImunobiologico());
    }

    @Test
    void baixaAdministradaEhRecusadaSemAlterarDoses() {
        assertThrows(CamaraEmAlerta.class, () -> baixa.registrar(lote, 10, MotivoBaixa.ADMINISTRADA));

        assertEquals(100, lotes.buscarPorId(lote).orElseThrow().getQuantidade());
        assertTrue(publicadas.isEmpty());
    }

    @Test
    void baixaPorOutroMotivoDescarteEInativacaoContinuamPermitidos() {
        for (var motivo : MotivoBaixa.values()) {
            if (motivo != MotivoBaixa.ADMINISTRADA) {
                baixa.registrar(lote, 1, motivo);
            }
        }
        descartar.descartar(lote);
        assertEquals(EstadoLote.DESCARTADO, lotes.buscarPorId(lote).orElseThrow().getEstado());
        inativar.inativar(lote);
        assertFalse(lotes.buscarPorId(lote).orElseThrow().isAtivo());
    }

    @Test
    void camaraForaDoAlertaAceitaTudo() {
        long semAlerta = cadastrar.cadastrar("L-2", "Vacina", "Fabricante", VALIDADE, 10, normal).getId();
        atualizar.atualizar(semAlerta, "Vacina", "Fabricante", VALIDADE, 20, normal);
        baixa.registrar(semAlerta, 5, MotivoBaixa.ADMINISTRADA);
        assertEquals(15, lotes.buscarPorId(semAlerta).orElseThrow().getQuantidade());
    }

    private long camara(String codigo) {
        return camaras.salvar(Camara.nova(codigo, "Câmara", "UBS", 1000, new BigDecimal("2.0"),
                new BigDecimal("8.0"), EstadoCamara.OPERACIONAL, AGORA.minusDays(1))).getId();
    }
}

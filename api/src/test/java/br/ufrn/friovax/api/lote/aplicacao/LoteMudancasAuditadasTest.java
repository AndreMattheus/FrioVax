package br.ufrn.friovax.api.lote.aplicacao;

import br.ufrn.friovax.api.auditoria.dominio.OperacaoAuditada;
import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import br.ufrn.friovax.api.compartilhado.aplicacao.MudancaConfirmada;
import br.ufrn.friovax.api.lote.dominio.MotivoBaixa;
import br.ufrn.friovax.api.suporte.CamaraRepositoryEmMemoria;
import br.ufrn.friovax.api.suporte.LoteRepositoryEmMemoria;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cada alteração real de lote publica uma mudança auditada; reenviar o mesmo pedido não publica de novo (US10). */
class LoteMudancasAuditadasTest {
    private static final OffsetDateTime AGORA = OffsetDateTime.parse("2026-11-18T10:00:00-03:00");
    private static final Clock RELOGIO = Clock.fixed(AGORA.toInstant(), ZoneId.of("America/Fortaleza"));
    private static final LocalDate VALIDADE = LocalDate.of(2030, 1, 1);

    private final CamaraRepositoryEmMemoria camaras = new CamaraRepositoryEmMemoria();
    private final LoteRepositoryEmMemoria lotes = new LoteRepositoryEmMemoria();
    private final List<MudancaConfirmada> publicadas = new ArrayList<>();

    private final CadastrarLote cadastrar = new CadastrarLote(lotes, camaras, RELOGIO, publicadas::add);
    private final AtualizarLote atualizar = new AtualizarLote(lotes, camaras, RELOGIO, publicadas::add);
    private final RegistrarBaixaLote baixa = new RegistrarBaixaLote(lotes, RELOGIO, publicadas::add);
    private final DescartarLote descartar = new DescartarLote(lotes, RELOGIO, publicadas::add);
    private final InativarLote inativar = new InativarLote(lotes, RELOGIO, publicadas::add);

    private long camara() {
        return camaras.salvar(Camara.nova("CAM-01", "Câmara", "UBS", 1000,
                new BigDecimal("2.0"), new BigDecimal("8.0"), EstadoCamara.OPERACIONAL, AGORA)).getId();
    }

    private long lote(long camaraId) {
        long id = cadastrar.cadastrar("FX2027A", "Febre amarela", "Fabricante", VALIDADE, 100, camaraId).getId();
        publicadas.clear();
        return id;
    }

    @Test
    void cadastroPublicaMudancaAuditada() {
        long camaraId = camara();

        long id = cadastrar.cadastrar("FX2027A", "Febre amarela", "Fabricante", VALIDADE, 100, camaraId).getId();

        assertEquals(1, publicadas.size());
        var mudanca = publicadas.get(0);
        assertTrue(mudanca.auditavel());
        assertEquals("lote", mudanca.recurso());
        assertEquals(id, mudanca.recursoId());
        assertEquals(OperacaoAuditada.CADASTRO, mudanca.operacao());
        assertEquals("Lote FX2027A cadastrado na câmara " + camaraId + " com 100 doses", mudanca.descricao());
    }

    @Test
    void reenviarOsMesmosDadosNaoPublica() {
        long camaraId = camara();
        long id = lote(camaraId);

        atualizar.atualizar(id, "Febre amarela", "Fabricante", VALIDADE, 100, camaraId);
        atualizar.atualizar(id, "  Febre amarela  ", "Fabricante", VALIDADE, 100, camaraId);

        assertTrue(publicadas.isEmpty(), "texto igual depois de normalizado também não é alteração");
    }

    @Test
    void atualizacaoDescreveOsCamposSemCopiarTextos() {
        long camaraId = camara();
        long id = lote(camaraId);

        atualizar.atualizar(id, "Febre amarela atenuada", "Outro fabricante", VALIDADE, 120, camaraId);

        assertEquals(1, publicadas.size());
        var mudanca = publicadas.get(0);
        assertEquals(OperacaoAuditada.ATUALIZACAO, mudanca.operacao());
        assertEquals("Lote FX2027A atualizado: imunobiológico; fabricante; quantidade 100 → 120", mudanca.descricao());
        assertFalse(mudanca.descricao().contains("atenuada"));
    }

    @Test
    void baixaInformaQuantidadeMotivoESaldo() {
        long id = lote(camara());

        baixa.registrar(id, 30, MotivoBaixa.ADMINISTRADA);

        assertEquals(OperacaoAuditada.BAIXA, publicadas.get(0).operacao());
        assertEquals("Baixa de 30 doses do lote FX2027A (ADMINISTRADA); restam 70", publicadas.get(0).descricao());
    }

    @Test
    void descarteEInativacaoRepetidosPublicamUmaVez() {
        long id = lote(camara());

        descartar.descartar(id);
        descartar.descartar(id);
        inativar.inativar(id);
        inativar.inativar(id);

        assertEquals(List.of(OperacaoAuditada.DESCARTE, OperacaoAuditada.INATIVACAO),
                publicadas.stream().map(MudancaConfirmada::operacao).toList());
    }
}

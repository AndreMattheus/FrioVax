package br.ufrn.friovax.api.camara.aplicacao;

import br.ufrn.friovax.api.auditoria.dominio.OperacaoAuditada;
import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import br.ufrn.friovax.api.compartilhado.aplicacao.MudancaConfirmada;
import br.ufrn.friovax.api.suporte.CamaraRepositoryEmMemoria;
import br.ufrn.friovax.api.suporte.LoteRepositoryEmMemoria;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cada alteração real de câmara publica uma mudança auditada; reenviar o mesmo PUT não publica (US10). */
class CamaraMudancasAuditadasTest {
    private static final OffsetDateTime AGORA = OffsetDateTime.parse("2026-11-18T10:00:00-03:00");
    private static final Clock RELOGIO = Clock.fixed(AGORA.toInstant(), ZoneId.of("America/Fortaleza"));

    private final CamaraRepositoryEmMemoria camaras = new CamaraRepositoryEmMemoria();
    private final LoteRepositoryEmMemoria lotes = new LoteRepositoryEmMemoria();
    private final List<MudancaConfirmada> publicadas = new ArrayList<>();

    private final CamaraService cadastrar = new CamaraService(camaras, RELOGIO, publicadas::add);
    private final AtualizarCamara atualizar = new AtualizarCamara(camaras, lotes, RELOGIO, publicadas::add);
    private final InativarCamara inativar = new InativarCamara(camaras, lotes, RELOGIO, publicadas::add);

    private long cadastrar() {
        return cadastrar.cadastrar(new CadastrarCamaraDTO("CAM-01", "Câmara", "UBS Centro", 1000,
                new BigDecimal("2.0"), new BigDecimal("8.0"), EstadoCamara.OPERACIONAL)).id();
    }

    private static AtualizarCamaraDTO dados(String nome, String minima) {
        return new AtualizarCamaraDTO(nome, "UBS Centro", 1000, new BigDecimal(minima), new BigDecimal("8.0"),
                EstadoCamara.OPERACIONAL);
    }

    @Test
    void cadastroPublicaMudancaAuditada() {
        long id = cadastrar();

        var mudanca = publicadas.get(0);
        assertEquals("camara", mudanca.recurso());
        assertEquals(id, mudanca.recursoId());
        assertEquals(OperacaoAuditada.CADASTRO, mudanca.operacao());
        assertEquals("Câmara CAM-01 cadastrada com capacidade 1000 e faixa 2.0 a 8.0 °C", mudanca.descricao());
    }

    @Test
    void reenviarOMesmoPutNaoPublica() {
        long id = cadastrar();
        publicadas.clear();

        atualizar.atualizar(id, dados("Câmara", "2.0"));
        atualizar.atualizar(id, dados("Câmara", "2.00"));

        assertTrue(publicadas.isEmpty());
    }

    @Test
    void mudancaDeNomeOuDeFaixaPublica() {
        long id = cadastrar();
        publicadas.clear();

        atualizar.atualizar(id, dados("Câmara principal", "2.0"));
        atualizar.atualizar(id, dados("Câmara principal", "3.0"));

        assertEquals(2, publicadas.size());
        assertEquals(OperacaoAuditada.ATUALIZACAO, publicadas.get(1).operacao());
        assertTrue(publicadas.get(1).descricao().contains("versão 1 para a 2"), publicadas.get(1).descricao());
    }

    @Test
    void inativacaoRepetidaPublicaUmaVez() {
        long id = cadastrar();
        publicadas.clear();

        inativar.inativar(id);
        inativar.inativar(id);

        assertEquals(List.of(OperacaoAuditada.INATIVACAO), publicadas.stream().map(MudancaConfirmada::operacao).toList());
    }
}

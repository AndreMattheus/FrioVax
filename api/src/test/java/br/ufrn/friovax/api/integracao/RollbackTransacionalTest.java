package br.ufrn.friovax.api.integracao;

import br.ufrn.friovax.api.camara.aplicacao.AtualizarCamara;
import br.ufrn.friovax.api.camara.aplicacao.AtualizarCamaraDTO;
import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.CamaraRepository;
import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import br.ufrn.friovax.api.compartilhado.dominio.CapacidadeExcedida;
import br.ufrn.friovax.api.compartilhado.dominio.EstadoIncompativel;
import br.ufrn.friovax.api.lote.aplicacao.AtualizarLote;
import br.ufrn.friovax.api.lote.aplicacao.CadastrarLote;
import br.ufrn.friovax.api.lote.dominio.Lote;
import br.ufrn.friovax.api.lote.dominio.LoteRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova que uma rejeição desfaz a transação inteira no PostgreSQL, inclusive o que já tinha sido enviado ao banco
 * ({@code flush}) antes dela, e que os casos de uso rejeitados não deixam metadados alterados.
 */
@QuarkusTest
class RollbackTransacionalTest {

    private static final ZoneId FUSO = ZoneId.of("America/Fortaleza");

    @Inject CadastrarLote cadastrarLote;
    @Inject AtualizarLote atualizarLote;
    @Inject AtualizarCamara atualizarCamara;
    @Inject CamaraRepository camaras;
    @Inject LoteRepository lotes;
    @Inject EntityManager entityManager;

    private final List<Long> camarasDoTeste = new ArrayList<>();

    @AfterEach
    void limparRegistrosDoTeste() {
        QuarkusTransaction.requiringNew().run(() -> camarasDoTeste.forEach(id -> {
            entityManager.createNativeQuery("delete from lotes where camara_id = :id")
                    .setParameter("id", id).executeUpdate();
            entityManager.createNativeQuery("delete from camaras where id = :id")
                    .setParameter("id", id).executeUpdate();
        }));
    }

    @Test
    void capacidadeExcedidaDesfazLotesJaGravadosNaMesmaTransacao() {
        long camaraId = novaCamara(1000, EstadoCamara.OPERACIONAL);
        var primeiro = codigoDeLote();

        assertThrows(CapacidadeExcedida.class, () -> QuarkusTransaction.requiringNew().run(() -> {
            cadastrar(primeiro, camaraId, 600);
            assertTrue(lotes.existePorCodigo(primeiro), "o primeiro lote deveria estar gravado na transação");
            cadastrar(codigoDeLote(), camaraId, 600);
        }));

        assertFalse(QuarkusTransaction.requiringNew().call(() -> lotes.existePorCodigo(primeiro)));
        assertEquals(0, ocupacao(camaraId));
    }

    @Test
    void movimentacaoRecusadaDesfazAlteracoesAnterioresDoLoteNaMesmaTransacao() {
        long origem = novaCamara(1000, EstadoCamara.OPERACIONAL);
        long manutencao = novaCamara(1000, EstadoCamara.MANUTENCAO);
        long loteId = cadastrar(codigoDeLote(), origem, 100);
        var antes = lote(loteId);

        assertThrows(EstadoIncompativel.class, () -> QuarkusTransaction.requiringNew().run(() -> {
            atualizarLote.atualizar(loteId, "Hepatite B", "Outro fabricante", validade().plusMonths(1), 400, origem);
            atualizarLote.atualizar(loteId, "Hepatite B", "Outro fabricante", validade().plusMonths(1), 400,
                    manutencao);
        }));

        var depois = lote(loteId);
        assertEquals(antes.getImunobiologico(), depois.getImunobiologico());
        assertEquals(antes.getFabricante(), depois.getFabricante());
        assertEquals(antes.getValidade(), depois.getValidade());
        assertEquals(100, depois.getQuantidade());
        assertEquals(origem, depois.getCamaraId());
        assertEquals(antes.getAtualizadoEm().toInstant(), depois.getAtualizadoEm().toInstant());
        assertEquals(100, ocupacao(origem));
    }

    @Test
    void violacaoDeRestricaoDoBancoDesfazAlteracaoJaGravadaDaCamara() {
        long camaraId = novaCamara(1000, EstadoCamara.OPERACIONAL);
        var antes = camara(camaraId);

        // A capacidade é aceita pelo caso de uso; a segunda gravação viola o CHECK de capacidade só no banco.
        assertThrows(RuntimeException.class, () -> QuarkusTransaction.requiringNew().run(() -> {
            atualizarCamara.atualizar(camaraId, new AtualizarCamaraDTO("Renomeada", "Outra UBS", 2000,
                    new BigDecimal("1.0"), new BigDecimal("7.0"), EstadoCamara.OPERACIONAL));
            var salva = camaras.buscarPorId(camaraId).orElseThrow();
            camaras.salvar(Camara.reconstituir(camaraId, salva.getCodigo(), salva.getNome(), salva.getUnidade(), 0,
                    salva.getTemperaturaMinima(), salva.getTemperaturaMaxima(), salva.getEstado(), true,
                    salva.getCriadoEm(), salva.getAtualizadoEm()));
        }));

        var depois = camara(camaraId);
        assertEquals(antes.getNome(), depois.getNome());
        assertEquals(antes.getUnidade(), depois.getUnidade());
        assertEquals(1000, depois.getCapacidade());
        assertEquals(0, antes.getTemperaturaMinima().compareTo(depois.getTemperaturaMinima()));
        assertEquals(antes.getAtualizadoEm().toInstant(), depois.getAtualizadoEm().toInstant());
    }

    @Test
    void rejeicaoNaoImpedeAProximaTransacaoDeGravar() {
        long camaraId = novaCamara(500, EstadoCamara.OPERACIONAL);

        assertThrows(CapacidadeExcedida.class, () -> cadastrar(codigoDeLote(), camaraId, 600));
        long loteId = cadastrar(codigoDeLote(), camaraId, 500);

        assertEquals(camaraId, lote(loteId).getCamaraId());
        assertEquals(500, ocupacao(camaraId));
    }

    private long novaCamara(int capacidade, EstadoCamara estado) {
        long id = QuarkusTransaction.requiringNew().call(() -> camaras.salvar(Camara.nova(
                "C-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT), "Câmara",
                "UBS rollback", capacidade, new BigDecimal("2.0"), new BigDecimal("8.0"), estado,
                OffsetDateTime.now(FUSO))).getId());
        camarasDoTeste.add(id);
        return id;
    }

    private long cadastrar(String codigo, long camaraId, int quantidade) {
        return cadastrarLote.cadastrar(codigo, "Febre amarela", "Bio-Manguinhos", validade(), quantidade, camaraId)
                .getId();
    }

    private static String codigoDeLote() {
        return "L-" + UUID.randomUUID().toString().substring(0, 13).toUpperCase(Locale.ROOT);
    }

    private static LocalDate validade() {
        return LocalDate.now(FUSO).plusYears(1);
    }

    private long ocupacao(long camaraId) {
        return QuarkusTransaction.requiringNew().call(() -> lotes.ocupacaoDaCamara(camaraId));
    }

    private Lote lote(long id) {
        return QuarkusTransaction.requiringNew().call(() -> lotes.buscarPorId(id).orElseThrow());
    }

    private Camara camara(long id) {
        return QuarkusTransaction.requiringNew().call(() -> camaras.buscarPorId(id).orElseThrow());
    }
}

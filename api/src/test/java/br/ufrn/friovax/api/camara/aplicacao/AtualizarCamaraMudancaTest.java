package br.ufrn.friovax.api.camara.aplicacao;

import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import br.ufrn.friovax.api.camara.dominio.EstadoTermico;
import br.ufrn.friovax.api.compartilhado.aplicacao.MudancaConfirmada;
import br.ufrn.friovax.api.compartilhado.dominio.CapacidadeExcedida;
import br.ufrn.friovax.api.lote.dominio.Lote;
import br.ufrn.friovax.api.suporte.CamaraRepositoryEmMemoria;
import br.ufrn.friovax.api.suporte.LoteRepositoryEmMemoria;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** O PUT de câmara usa a política versionada e avisa a mudança (S2-05). Sem banco. */
class AtualizarCamaraMudancaTest {
    private static final OffsetDateTime CRIACAO = OffsetDateTime.parse("2026-11-07T10:00:00-03:00");
    private static final OffsetDateTime AGORA = CRIACAO.plusDays(1);

    private final CamaraRepositoryEmMemoria camaras = new CamaraRepositoryEmMemoria();
    private final List<MudancaConfirmada> publicadas = new ArrayList<>();
    private final AtualizarCamara casoDeUso = new AtualizarCamara(camaras, new LoteRepositoryEmMemoria(),
            Clock.fixed(AGORA.toInstant(), AGORA.getOffset()), publicadas::add);

    @Test
    void novaFaixaGravaNovaVersaoEPublicaAMudanca() {
        long id = criar();

        var resultado = casoDeUso.atualizar(id, dados("3.0", "7.0", 1000));

        assertEquals(2, resultado.camara().getPolitica().getVersao());
        assertEquals(EstadoTermico.SEM_LEITURAS, resultado.camara().getEstadoTermico());
        assertEquals(2, camaras.historicoDePoliticas(id).size());
        assertEquals(new BigDecimal("2.0"), camaras.historicoDePoliticas(id).get(0).getTemperaturaMinima());
        assertEquals(1, publicadas.size());
        var mudanca = publicadas.get(0);
        assertEquals("camara", mudanca.recurso());
        assertEquals(id, mudanca.recursoId());
        assertTrue(mudanca.descricao().contains("versão 1 para a 2"), mudanca.descricao());
        assertEquals(AGORA.toInstant(), mudanca.instante().toInstant());
    }

    @Test
    void mesmaFaixaNaoGeraVersaoMasPublicaAEdicao() {
        long id = criar();

        casoDeUso.atualizar(id, dados("2.0", "8.0", 900));

        assertEquals(1, camaras.historicoDePoliticas(id).size());
        assertEquals(1, publicadas.size());
        assertTrue(publicadas.get(0).descricao().contains("mantida na versão 1"), publicadas.get(0).descricao());
    }

    @Test
    void edicaoRecusadaNaoPublicaNemGeraVersao() {
        long id = criar();

        assertThrows(CapacidadeExcedida.class, () -> new AtualizarCamara(camaras, ocupada(id),
                Clock.fixed(AGORA.toInstant(), AGORA.getOffset()), publicadas::add)
                .atualizar(id, dados("3.0", "7.0", 10)));

        assertEquals(1, camaras.historicoDePoliticas(id).size());
        assertTrue(publicadas.isEmpty());
    }

    private long criar() {
        return camaras.salvar(Camara.nova("CAM-01", "Câmara", "UBS Centro", 1000, new BigDecimal("2.0"),
                new BigDecimal("8.0"), EstadoCamara.OPERACIONAL, CRIACAO)).getId();
    }

    /** Repositório de lotes em que a câmara já tem 50 doses, para forçar a recusa da capacidade. */
    private static LoteRepositoryEmMemoria ocupada(long camaraId) {
        var lotes = new LoteRepositoryEmMemoria();
        lotes.salvar(Lote.novo("L-01", "Vacina", "Fabricante",
                LocalDate.of(2027, 1, 1), 50, camaraId, CRIACAO.toLocalDate(), CRIACAO));
        return lotes;
    }

    private static AtualizarCamaraDTO dados(String minima, String maxima, int capacidade) {
        return new AtualizarCamaraDTO("Câmara", "UBS Centro", capacidade, new BigDecimal(minima),
                new BigDecimal(maxima), EstadoCamara.OPERACIONAL);
    }
}

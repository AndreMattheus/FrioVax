package br.ufrn.friovax.api.telemetria.aplicacao;

import br.ufrn.friovax.api.camara.aplicacao.ConsultarPoliticasHistoricas;
import br.ufrn.friovax.api.camara.dominio.*;
import br.ufrn.friovax.api.compartilhado.dominio.*;
import br.ufrn.friovax.api.suporte.*;
import br.ufrn.friovax.api.telemetria.dominio.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ConsultarHistoricoTest {
    private final CamaraRepositoryEmMemoria camaras = new CamaraRepositoryEmMemoria();
    private final LeituraRepositoryEmMemoria leituras = new LeituraRepositoryEmMemoria();
    private final OcorrenciaRepositoryEmMemoria ocorrencias = new OcorrenciaRepositoryEmMemoria();
    private final ConsultarHistorico consulta = new ConsultarHistorico(leituras, ocorrencias,
            new ConsultarPoliticasHistoricas(camaras));
    private final OffsetDateTime agora = OffsetDateTime.parse("2026-11-19T10:00:00-03:00");

    @Test
    void consultaPreservaPoliticaOriginalDepoisDaMudancaEInativacao() {
        var c = camaras.salvar(Camara.nova("CAM-01", "Câmara", "UBS", 100,
                new BigDecimal("2"), new BigDecimal("8"), EstadoCamara.OPERACIONAL, agora));
        leituras.salvar(Leitura.nova(c.getId(), "A", agora, new BigDecimal("9"), "sensor", 1,
                ResultadoTermico.insegura(1, false), agora));
        c.atualizar("Câmara", "UBS", 100, new BigDecimal("2"), new BigDecimal("10"),
                EstadoCamara.OPERACIONAL, 0, false, agora.plusMinutes(1));
        c.inativar(false, agora.plusMinutes(2));
        camaras.salvar(c);
        var resultado = consulta.listarLeituras(c.getId(), new PeriodoConsulta(agora, agora.plusHours(1)),
                Paginacao.padrao());
        assertEquals(1, resultado.totalElementos());
        assertEquals(1, resultado.itens().getFirst().politica().getVersao());
        assertEquals(0, new BigDecimal("8").compareTo(resultado.itens().getFirst().politica().getTemperaturaMaxima()));
        assertEquals(ClassificacaoTermica.INSEGURA, resultado.itens().getFirst().leitura().getResultado().classificacao());
    }

    @Test
    void detalheMantemOrdemDasEvidenciasETratamentoReal() {
        var c = camaras.salvar(Camara.nova("CAM-02", "Câmara", "UBS", 100,
                new BigDecimal("2"), new BigDecimal("8"), EstadoCamara.OPERACIONAL, agora));
        var l1 = leituras.salvar(Leitura.nova(c.getId(), "A", agora.minusMinutes(1), new BigDecimal("9"),
                "sensor", 1, ResultadoTermico.insegura(1, false), agora));
        var l2 = leituras.salvar(Leitura.nova(c.getId(), "B", agora, new BigDecimal("9"),
                "sensor", 1, ResultadoTermico.insegura(2, false), agora));
        var o = ocorrencias.salvar(Ocorrencia.abrir(c.getId(), 1, l2.getId(), List.of(l1.getId(), l2.getId()),
                List.of(new LoteAfetado(10, "LOTE", "Vacina", 100)), false, agora));
        assertNull(consulta.consultarOcorrencia(o.getId()).resumo().ocorrencia().resolvidaPor());
        var autor = UUID.randomUUID();
        o.resolver("Verificação concluída", l2.getId(), autor, agora.plusHours(1));
        ocorrencias.salvar(o);
        var detalhe = consulta.consultarOcorrencia(o.getId());
        assertEquals(List.of(l1.getId(), l2.getId()), detalhe.evidencias().stream().map(e -> e.leitura().getId()).toList());
        assertEquals(autor, detalhe.resumo().ocorrencia().resolvidaPor());
        assertEquals(100, detalhe.lotesAfetados().getFirst().quantidade());
    }

    @Test
    void inexistentesNaoViraramHistoricoVazio() {
        assertThrows(RecursoNaoEncontrado.class, () -> consulta.listarLeituras(99,
                new PeriodoConsulta(null, null), Paginacao.padrao()));
        assertThrows(RecursoNaoEncontrado.class, () -> consulta.consultarOcorrencia(99));
        assertEquals(0, consulta.listarOcorrencias(new OcorrenciaFiltro(99L, null,
                new PeriodoConsulta(null, null)), Paginacao.padrao()).totalElementos());
    }

    @Test
    void periodoComparaInstantesIncluiInicioEExcluiFim() {
        var p = new PeriodoConsulta(agora, agora.plusHours(1));
        assertTrue(p.contem(OffsetDateTime.parse("2026-11-19T13:00:00Z")));
        assertFalse(p.contem(agora.minusNanos(1)));
        assertFalse(p.contem(agora.plusHours(1)));
        assertThrows(IllegalArgumentException.class, () -> new PeriodoConsulta(agora,
                OffsetDateTime.parse("2026-11-19T13:00:00Z")));
        assertThrows(IllegalArgumentException.class, () -> new PeriodoConsulta(agora, agora.minusHours(1)));
    }
}

package br.ufrn.friovax.api.telemetria.persistencia;

import br.ufrn.friovax.api.compartilhado.dominio.Paginacao;
import br.ufrn.friovax.api.telemetria.dominio.*;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class HistoricoConsultaPostgresTest extends HistoricoPostgresFixture {
    @Test
    void filtraLeiturasNoBancoComContagemELimitesExatos() {
        QuarkusTransaction.requiringNew().run(() -> {
            var periodo = new PeriodoConsulta(INICIO, INICIO.plusMinutes(3));
            var pagina = leituras.listarDaCamara(camara, periodo, new Paginacao(0, 2));
            assertEquals(3, pagina.totalElementos());
            assertEquals(List.of("3", "2"), pagina.itens().stream().map(Leitura::getIdExterno).toList());
            assertEquals(List.of("1"), leituras.listarDaCamara(camara, periodo, new Paginacao(1, 2))
                    .itens().stream().map(Leitura::getIdExterno).toList());
            var fim = leituras.listarDaCamara(camara, periodo, new Paginacao(Integer.MAX_VALUE, 100));
            assertTrue(fim.itens().isEmpty());
            assertEquals(3, fim.totalElementos());
        });
    }

    @Test
    void filtraEstadoECamaraEDesempataAberturaPorId() {
        QuarkusTransaction.requiringNew().run(() -> {
            var periodo = new PeriodoConsulta(INICIO.plusMinutes(2), INICIO.plusMinutes(3));
            var todas = new OcorrenciaFiltro(null, null, periodo);
            assertEquals(aberta, ocorrencias.listar(todas, new Paginacao(0, 1)).itens().getFirst().id());
            assertEquals(resolvida, ocorrencias.listar(todas, new Paginacao(1, 1)).itens().getFirst().id());
            var pagina = ocorrencias.listar(new OcorrenciaFiltro(camara, EstadoOcorrencia.RESOLVIDA, periodo),
                    Paginacao.padrao());
            assertEquals(1, pagina.totalElementos());
            assertEquals(autor, pagina.itens().getFirst().resolvidaPor());
            assertEquals(0, ocorrencias.listar(new OcorrenciaFiltro(camara, EstadoOcorrencia.ABERTA, periodo),
                    Paginacao.padrao()).totalElementos());
        });
    }
}

package br.ufrn.friovax.api.suporte;

import br.ufrn.friovax.api.compartilhado.dominio.Paginacao;
import br.ufrn.friovax.api.telemetria.dominio.Leitura;
import br.ufrn.friovax.api.telemetria.dominio.LeituraForaDeOrdem;
import br.ufrn.friovax.api.telemetria.dominio.LeituraJaRegistrada;
import br.ufrn.friovax.api.telemetria.dominio.Ocorrencia;
import br.ufrn.friovax.api.telemetria.dominio.OcorrenciaJaAberta;
import br.ufrn.friovax.api.telemetria.dominio.ResultadoTermico;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetriaRepositoriosEmMemoriaTest {
    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-11-12T10:00:00-03:00");

    private final LeituraRepositoryEmMemoria leituras = new LeituraRepositoryEmMemoria();
    private final OcorrenciaRepositoryEmMemoria ocorrencias = new OcorrenciaRepositoryEmMemoria();

    @Test
    void travasDeChaveEDeInstante() {
        leituras.salvar(leitura("A", 0, 1));

        assertThrows(LeituraJaRegistrada.class, () -> leituras.salvar(leitura("A", 5, 1)));
        assertThrows(LeituraForaDeOrdem.class, () -> leituras.salvar(leitura("B", 0, 1)));
        leituras.salvar(Leitura.nova(2, "A", T0, new BigDecimal("5.0"), "s", 1, ResultadoTermico.segura(), T0));
    }

    @Test
    void contextoDaJanelaLimitadoNaVersaoEDepoisDoReinicio() {
        var a = leituras.salvar(leitura("A", 1, 1));
        leituras.salvar(leitura("B", 2, 1));
        leituras.salvar(leitura("C", 3, 1));
        leituras.salvar(leitura("D", 4, 2));

        assertEquals(List.of("B", "C"), ids(leituras.contextoDaJanela(1, 1, 2, null)));
        assertEquals(List.of("A", "B", "C"), ids(leituras.contextoDaJanela(1, 1, 99, null)));
        assertEquals(List.of("B", "C"), ids(leituras.contextoDaJanela(1, 1, 99, a.getId())));
        assertTrue(leituras.contextoDaJanela(1, 1, 0, null).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> leituras.contextoDaJanela(1, 1, 100, null));
    }

    @Test
    void listagemDaMaisRecenteParaAMaisAntiga() {
        leituras.salvar(leitura("A", 1, 1));
        leituras.salvar(leitura("B", 2, 1));
        leituras.salvar(leitura("C", 3, 1));

        var pagina = leituras.listarDaCamara(1, new Paginacao(0, 2));

        assertEquals(List.of("C", "B"), ids(pagina.itens()));
        assertEquals(3, pagina.totalElementos());
        assertEquals("C", leituras.ultimaDaCamara(1).orElseThrow().getIdExterno());
    }

    @Test
    void umaOcorrenciaAbertaPorCamaraENovaDepoisDaResolucao() {
        var primeira = ocorrencias.salvar(Ocorrencia.abrir(1, 1, 3, List.of(3L), List.of(), false, T0));

        assertThrows(OcorrenciaJaAberta.class,
                () -> ocorrencias.salvar(Ocorrencia.abrir(1, 1, 4, List.of(4L), List.of(), false, T0)));

        var gravada = ocorrencias.buscarPorId(primeira.getId()).orElseThrow();
        gravada.resolver("ok", 5, null, T0.plusHours(1));
        ocorrencias.salvar(gravada);

        ocorrencias.salvar(Ocorrencia.abrir(1, 1, 6, List.of(6L), List.of(), false, T0.plusHours(2)));
        assertEquals(primeira.getId(), ocorrencias.ultimaResolvidaDaCamara(1).orElseThrow().getId());
    }

    private static Leitura leitura(String idExterno, int minutos, int versao) {
        return Leitura.nova(1, idExterno, T0.plusMinutes(minutos), new BigDecimal("5.0"), "s", versao,
                ResultadoTermico.segura(), T0);
    }

    private static List<String> ids(List<Leitura> lista) {
        return lista.stream().map(Leitura::getIdExterno).toList();
    }
}

package br.ufrn.friovax.api.camara.aplicacao;

import br.ufrn.friovax.api.auditoria.dominio.Ator;
import br.ufrn.friovax.api.auditoria.dominio.ContextoDaOperacao;
import br.ufrn.friovax.api.auditoria.dominio.OperacaoAuditada;
import br.ufrn.friovax.api.autenticacao.dominio.Perfil;
import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import br.ufrn.friovax.api.camara.dominio.EstadoTermico;
import br.ufrn.friovax.api.compartilhado.aplicacao.MudancaConfirmada;
import br.ufrn.friovax.api.compartilhado.dominio.EstadoIncompativel;
import br.ufrn.friovax.api.compartilhado.dominio.Paginacao;
import br.ufrn.friovax.api.compartilhado.dominio.RecursoNaoEncontrado;
import br.ufrn.friovax.api.compartilhado.dominio.ValidacaoDeNegocio;
import br.ufrn.friovax.api.compartilhado.dominio.VersaoDesatualizada;
import br.ufrn.friovax.api.suporte.CamaraRepositoryEmMemoria;
import br.ufrn.friovax.api.suporte.LoteRepositoryEmMemoria;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Rota de política (S2-14) e PUT de câmara com a mesma regra de versão, autor e auditoria. Sem banco. */
class AlterarPoliticaTermicaTest {
    private static final OffsetDateTime CRIACAO = OffsetDateTime.parse("2026-11-22T10:00:00-03:00");
    private static final OffsetDateTime AGORA = CRIACAO.plusHours(1);
    private static final Clock RELOGIO = Clock.fixed(AGORA.toInstant(), AGORA.getOffset());

    private final UUID admin = UUID.randomUUID();
    private final ContextoDaOperacao contexto = ContextoDaOperacao.de(
            Ator.usuario(admin, "administrador", Perfil.ADMINISTRADOR));
    private final CamaraRepositoryEmMemoria camaras = new CamaraRepositoryEmMemoria();
    private final List<MudancaConfirmada> publicadas = new ArrayList<>();
    private final AlterarPoliticaTermica alterar = new AlterarPoliticaTermica(camaras, RELOGIO, publicadas::add,
            contexto);
    private final AtualizarCamara atualizarCamara = new AtualizarCamara(camaras, new LoteRepositoryEmMemoria(),
            RELOGIO, publicadas::add, contexto);
    private final ConsultarPoliticaTermica consultar = new ConsultarPoliticaTermica(camaras);

    @Test
    void mudancaRealCriaVersaoComAutorDoContextoEPublicaAuditoria() {
        long id = criar();

        var resultado = alterar.alterar(id, dados(1, "3.0", "7.0", 5));

        assertTrue(resultado.novaVersao());
        var politica = resultado.politica();
        assertEquals(2, politica.getVersao());
        assertEquals(new BigDecimal("3.0"), politica.getTemperaturaMinima());
        assertEquals(5, politica.getLimiar());
        assertEquals(admin, politica.getAutorId().orElseThrow());
        assertEquals(AGORA.toInstant(), politica.getCriadaEm().toInstant());

        var historico = camaras.historicoDePoliticas(id);
        assertEquals(2, historico.size());
        assertEquals(new BigDecimal("2.0"), historico.get(0).getTemperaturaMinima(), "a versão 1 é preservada");
        assertTrue(historico.get(0).getAutorId().isEmpty());

        assertEquals(1, publicadas.size());
        var mudanca = publicadas.get(0);
        assertEquals("politica_termica", mudanca.recurso());
        assertEquals(id, mudanca.recursoId());
        assertEquals(OperacaoAuditada.ALTERACAO_DE_POLITICA, mudanca.operacao());
        assertEquals(admin, mudanca.autor().orElseThrow());
        assertEquals("Política térmica da câmara CAM-01 passou da versão 1 para a 2: faixa 2.0 a 8.0 °C → "
                + "3.0 a 7.0 °C; limiar 3 → 5", mudanca.descricao());
    }

    @Test
    void novaVersaoReiniciaOEstadoTermico() {
        long id = criar();

        alterar.alterar(id, dados(1, "2.0", "8.0", 4));

        assertEquals(EstadoTermico.SEM_LEITURAS, camaras.buscarPorId(id).orElseThrow().getEstadoTermico());
        assertEquals("Política térmica da câmara CAM-01 passou da versão 1 para a 2: limiar 3 → 4",
                publicadas.get(0).descricao());
    }

    @Test
    void mesmosValoresNaoCriamVersaoNemEvento() {
        long id = criar();

        var resultado = alterar.alterar(id, dados(1, "2", "8.00", 3));

        assertFalse(resultado.novaVersao());
        assertEquals(1, resultado.politica().getVersao());
        assertEquals(1, camaras.historicoDePoliticas(id).size());
        assertTrue(publicadas.isEmpty());
    }

    @Test
    void segundaAlteracaoSobreAMesmaVersaoRecebeConflitoSemSobrescrever() {
        long id = criar();
        alterar.alterar(id, dados(1, "3.0", "7.0", 3));

        assertThrows(VersaoDesatualizada.class, () -> alterar.alterar(id, dados(1, "1.0", "9.0", 3)));
        // Mesmo um envio sem mudança sobre a versão antiga é recusado: o cliente não viu a versão 2.
        assertThrows(VersaoDesatualizada.class, () -> alterar.alterar(id, dados(1, "3.0", "7.0", 3)));

        var vigente = consultar.vigente(id);
        assertEquals(2, vigente.getVersao());
        assertEquals(new BigDecimal("3.0"), vigente.getTemperaturaMinima());
        assertEquals(1, publicadas.size());
    }

    @Test
    void valoresInvalidosNaoCriamVersao() {
        long id = criar();

        assertThrows(ValidacaoDeNegocio.class, () -> alterar.alterar(id, dados(1, "8.0", "2.0", 3)));
        assertThrows(ValidacaoDeNegocio.class, () -> alterar.alterar(id, dados(1, "2.05", "8.0", 3)));
        assertThrows(ValidacaoDeNegocio.class, () -> alterar.alterar(id, dados(1, "2.0", "8.0", 0)));
        assertThrows(ValidacaoDeNegocio.class, () -> alterar.alterar(id, dados(1, "2.0", "8.0", 101)));
        assertThrows(ValidacaoDeNegocio.class, () -> alterar.alterar(id, dados(1, "-1000.0", "8.0", 3)));

        assertEquals(1, camaras.historicoDePoliticas(id).size());
        assertTrue(publicadas.isEmpty());
    }

    @Test
    void camaraInexistenteOuInativa() {
        assertThrows(RecursoNaoEncontrado.class, () -> alterar.alterar(99, dados(1, "3.0", "7.0", 3)));
        assertThrows(RecursoNaoEncontrado.class, () -> consultar.vigente(99));
        assertThrows(RecursoNaoEncontrado.class, () -> consultar.historico(99, Paginacao.padrao()));

        long id = criar();
        var camara = camaras.buscarPorId(id).orElseThrow();
        camara.inativar(false, AGORA);
        camaras.salvar(camara);

        assertThrows(EstadoIncompativel.class, () -> alterar.alterar(id, dados(1, "3.0", "7.0", 3)));
        assertEquals(1, consultar.vigente(id).getVersao(), "câmara inativa continua consultável");
        assertTrue(publicadas.isEmpty());
    }

    @Test
    void historicoPaginadoDaVersaoMaisRecenteParaAMaisAntiga() {
        long id = criar();
        alterar.alterar(id, dados(1, "3.0", "7.0", 3));
        alterar.alterar(id, dados(2, "3.0", "7.0", 4));

        var primeira = consultar.historico(id, new Paginacao(0, 2));
        assertEquals(3, primeira.totalElementos());
        assertEquals(List.of(3, 2), primeira.itens().stream().map(p -> p.getVersao()).toList());
        var segunda = consultar.historico(id, new Paginacao(1, 2));
        assertEquals(List.of(1), segunda.itens().stream().map(p -> p.getVersao()).toList());
        assertTrue(consultar.historico(id, new Paginacao(5, 2)).itens().isEmpty());
    }

    @Test
    void putDaCamaraSegueAMesmaRegraDeVersaoEAutor() {
        long id = criar();

        atualizarCamara.atualizar(id, camara("3.0", 1));

        var vigente = consultar.vigente(id);
        assertEquals(2, vigente.getVersao());
        assertEquals(3, vigente.getLimiar(), "o PUT da câmara preserva o limiar");
        assertEquals(admin, vigente.getAutorId().orElseThrow());
        assertEquals(List.of("politica_termica"), publicadas.stream().map(MudancaConfirmada::recurso).toList());
        assertEquals(admin, publicadas.get(0).autor().orElseThrow());

        // Outro administrador que leu a versão 1 não sobrescreve a faixa nova pelo PUT da câmara.
        assertThrows(VersaoDesatualizada.class, () -> atualizarCamara.atualizar(id, camara("1.0", 1)));
        assertThrows(VersaoDesatualizada.class, () -> alterar.alterar(id, dados(1, "1.0", "8.0", 3)));
        assertEquals(new BigDecimal("3.0"), consultar.vigente(id).getTemperaturaMinima());

        // Sem versão esperada, o PUT da câmara só passa se mantiver a faixa vigente.
        atualizarCamara.atualizar(id, new AtualizarCamaraDTO("Câmara", "UBS Centro", 1000, new BigDecimal("3.0"),
                new BigDecimal("8.0"), EstadoCamara.OPERACIONAL));
        assertEquals(1, publicadas.size(), "nada mudou: nenhum evento novo");
    }

    @Test
    void putDaCamaraQueMudaAFaixaSemVersaoEsperadaEhRecusadoSemGravarNada() {
        long id = criar();
        alterar.alterar(id, dados(1, "3.0", "7.0", 3));
        publicadas.clear();

        // Edição antiga, feita antes da versão 2, sem versão: não pode voltar a faixa nem renomear.
        var erro = assertThrows(ValidacaoDeNegocio.class, () -> atualizarCamara.atualizar(id,
                new AtualizarCamaraDTO("Nome antigo", "UBS Centro", 1000, new BigDecimal("2.0"),
                        new BigDecimal("8.0"), EstadoCamara.OPERACIONAL)));
        assertEquals("versaoEsperada", erro.campo());

        var camara = camaras.buscarPorId(id).orElseThrow();
        assertEquals("Câmara", camara.getNome());
        assertEquals(2, camara.getPolitica().getVersao());
        assertEquals(new BigDecimal("3.0"), camara.getPolitica().getTemperaturaMinima());
        assertTrue(publicadas.isEmpty());

        // Mesma faixa em outra escala sem versão é aceita; outros campos mudam normalmente.
        atualizarCamara.atualizar(id, new AtualizarCamaraDTO("Câmara renomeada", "UBS Centro", 1000,
                new BigDecimal("3"), new BigDecimal("7.00"), EstadoCamara.OPERACIONAL));
        assertEquals(List.of("camara"), publicadas.stream().map(MudancaConfirmada::recurso).toList());
        assertEquals(2, consultar.vigente(id).getVersao());
    }

    @Test
    void putDaCamaraInativaComVersaoEsperadaContinuaComo409DeEstado() {
        long id = criar();
        var camara = camaras.buscarPorId(id).orElseThrow();
        camara.inativar(false, AGORA);
        camaras.salvar(camara);

        assertThrows(EstadoIncompativel.class, () -> atualizarCamara.atualizar(id, camara("3.0", 1)));
    }

    @Test
    void tarefaDoSistemaGravaVersaoSemAutor() {
        long id = criar();
        var sistema = new AlterarPoliticaTermica(camaras, RELOGIO, publicadas::add, ContextoDaOperacao.doSistema());

        sistema.alterar(id, dados(1, "3.0", "7.0", 3));

        assertTrue(consultar.vigente(id).getAutorId().isEmpty());
        assertTrue(publicadas.get(0).autor().isEmpty());
    }

    private long criar() {
        return camaras.salvar(Camara.nova("CAM-01", "Câmara", "UBS Centro", 1000, new BigDecimal("2.0"),
                new BigDecimal("8.0"), EstadoCamara.OPERACIONAL, CRIACAO)).getId();
    }

    private static AlterarPoliticaTermicaDTO dados(int versao, String minima, String maxima, int limiar) {
        return new AlterarPoliticaTermicaDTO(versao, new BigDecimal(minima), new BigDecimal(maxima), limiar);
    }

    private static AtualizarCamaraDTO camara(String minima, int versaoEsperada) {
        return new AtualizarCamaraDTO("Câmara", "UBS Centro", 1000, new BigDecimal(minima), new BigDecimal("8.0"),
                EstadoCamara.OPERACIONAL, versaoEsperada);
    }
}

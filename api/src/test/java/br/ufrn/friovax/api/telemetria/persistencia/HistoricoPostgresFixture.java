package br.ufrn.friovax.api.telemetria.persistencia;

import br.ufrn.friovax.api.camara.dominio.*;
import br.ufrn.friovax.api.lote.dominio.*;
import br.ufrn.friovax.api.telemetria.dominio.*;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Dados confirmados em transações próprias: os testes HTTP realmente leem PostgreSQL. */
public abstract class HistoricoPostgresFixture {
    protected static final OffsetDateTime INICIO = OffsetDateTime.parse("2026-11-19T10:00:00-03:00");
    @Inject protected CamaraRepository camaras;
    @Inject protected LoteRepository lotes;
    @Inject protected LeituraRepositoryPostgres leituras;
    @Inject protected OcorrenciaRepositoryPostgres ocorrencias;
    @Inject protected EntityManager em;
    protected long camara, outraCamara, lote, resolvida, aberta, primeiraLeitura, ultimaLeitura;
    protected UUID autor;

    @BeforeEach
    void prepararHistorico() {
        QuarkusTransaction.requiringNew().run(() -> {
            var sufixo = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            camara = criarCamara("A-" + sufixo);
            outraCamara = criarCamara("B-" + sufixo);
            autor = UUID.randomUUID();
            em.createNativeQuery("insert into usuarios (id, login, senha_hash, perfil) values (:id, :login, :hash, 'ADMINISTRADOR')")
                    .setParameter("id", autor).setParameter("login", "hist-" + autor)
                    .setParameter("hash", "$2a$10$" + "a".repeat(53)).executeUpdate();
            lote = lotes.salvar(Lote.novo("L-" + sufixo, "Vacina", "Fabricante", LocalDate.of(2030, 1, 1),
                    100, camara, INICIO.toLocalDate(), INICIO)).getId();
            primeiraLeitura = leitura(camara, "1", INICIO, ResultadoTermico.insegura(1, false));
            long segunda = leitura(camara, "2", INICIO.plusMinutes(1), ResultadoTermico.insegura(2, false));
            long terceira = leitura(camara, "3", INICIO.plusMinutes(2), ResultadoTermico.insegura(3, true));
            ultimaLeitura = leitura(camara, "4", INICIO.plusMinutes(3), ResultadoTermico.segura());
            var o = ocorrencias.salvar(Ocorrencia.abrir(camara, 1, terceira,
                    List.of(primeiraLeitura, segunda, terceira), List.of(new LoteAfetado(lote, "L-" + sufixo, "Vacina", 100)),
                    false, INICIO.plusMinutes(2)));
            o.resolver("Inspeção concluída", ultimaLeitura, autor, INICIO.plusMinutes(4));
            resolvida = ocorrencias.salvar(o).getId();
            long outra1 = leitura(outraCamara, "1", INICIO, ResultadoTermico.insegura(1, false));
            long outra2 = leitura(outraCamara, "2", INICIO.plusMinutes(1), ResultadoTermico.insegura(2, false));
            long outra3 = leitura(outraCamara, "3", INICIO.plusMinutes(2), ResultadoTermico.insegura(3, true));
            aberta = ocorrencias.salvar(Ocorrencia.abrir(outraCamara, 1, outra3, List.of(outra1, outra2, outra3),
                    List.of(), false, INICIO.plusMinutes(2))).getId();
        });
    }

    private long criarCamara(String codigo) {
        return camaras.salvar(Camara.nova(codigo, "Câmara", "UBS", 1000, new BigDecimal("2"),
                new BigDecimal("8"), EstadoCamara.OPERACIONAL, INICIO)).getId();
    }

    private long leitura(long id, String externa, OffsetDateTime instante, ResultadoTermico resultado) {
        return leituras.salvar(Leitura.nova(id, externa, instante,
                new BigDecimal(resultado.classificacao() == ClassificacaoTermica.SEGURA ? "4" : "9"),
                "sensor", 1, resultado, instante)).getId();
    }

    protected void alterarCadastros() {
        QuarkusTransaction.requiringNew().run(() -> {
            var c = camaras.buscarPorId(camara).orElseThrow();
            c.atualizar("Câmara", "UBS", 1000, new BigDecimal("2"), new BigDecimal("10"),
                    EstadoCamara.OPERACIONAL, 100, true, autor, INICIO.plusHours(1));
            camaras.salvar(c);
            var l = lotes.buscarPorId(lote).orElseThrow();
            l.atualizar("Vacina atualizada", "Fabricante", l.getValidade(), 100, outraCamara,
                    INICIO.toLocalDate(), INICIO.plusHours(1));
            l.inativar(INICIO.plusHours(1));
            lotes.salvar(l);
            c.inativar(false, INICIO.plusHours(1));
            camaras.salvar(c);
        });
    }

    @AfterEach
    void limparHistorico() {
        QuarkusTransaction.requiringNew().run(() -> {
            em.createNativeQuery("delete from ocorrencias where camara_id in (:ids)")
                    .setParameter("ids", List.of(camara, outraCamara)).executeUpdate();
            em.createNativeQuery("delete from lotes where id = :id").setParameter("id", lote).executeUpdate();
            em.createNativeQuery("delete from camaras where id in (:ids)")
                    .setParameter("ids", List.of(camara, outraCamara)).executeUpdate();
            em.createNativeQuery("delete from usuarios where id = :id").setParameter("id", autor).executeUpdate();
        });
    }
}

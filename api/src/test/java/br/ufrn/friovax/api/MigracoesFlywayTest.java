package br.ufrn.friovax.api;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Executa as migrações reais do Flyway no PostgreSQL do Dev Services. Cada teste usa um esquema próprio e vazio, então
 * não depende da ordem dos testes nem dos dados gravados pelo restante da suíte.
 */
@QuarkusTest
class MigracoesFlywayTest {

    @Inject
    DataSource dataSource;

    @Inject
    Flyway flyway;

    @Test
    void criaOEsquemaCompletoNumBancoVazio() throws SQLException {
        var esquema = esquemaNovo();
        try {
            assertEquals(Set.of(), tabelas(esquema));

            var resultado = flywayNoEsquema(esquema).migrate();

            assertTrue(resultado.success);
            assertEquals(versoesDisponiveis().size(), resultado.migrationsExecuted);
            assertEquals(Set.of("camaras", "lotes", "flyway_schema_history"), tabelas(esquema));
            assertTrue(restricoes(esquema).containsAll(Set.of("uk_camaras_codigo", "chk_camaras_estado",
                    "chk_faixa_termica", "uk_lotes_codigo", "fk_lotes_camara", "chk_lotes_quantidade",
                    "chk_lotes_estado", "chk_lotes_disponivel_com_doses", "chk_lotes_esgotado_sem_doses")));
            assertEquals(versoesDisponiveis(), versoesAplicadas(flywayNoEsquema(esquema)));
        } finally {
            apagar(esquema);
        }
    }

    @Test
    void novaExecucaoNaoReaplicaMigracoesNemPerdeDados() throws SQLException {
        var esquema = esquemaNovo();
        try {
            flywayNoEsquema(esquema).migrate();
            executar("""
                    insert into %1$s.camaras (codigo, nome, unidade, capacidade, temperatura_minima,
                        temperatura_maxima, estado, criado_em, atualizado_em)
                    values ('C-MIGRA', 'Câmara', 'UBS', 100, 2.0, 8.0, 'OPERACIONAL', now(), now());
                    insert into %1$s.lotes (codigo, imunobiologico, fabricante, validade, quantidade, camara_id,
                        estado, criado_em, atualizado_em)
                    select 'L-MIGRA', 'Febre amarela', 'Fabricante', date '2030-01-01', 40, id,
                        'DISPONIVEL', now(), now() from %1$s.camaras where codigo = 'C-MIGRA';
                    """.formatted(esquema));
            var historicoAntes = contar(esquema + ".flyway_schema_history");

            // Uma nova instância simula outra inicialização da aplicação apontando para o mesmo banco.
            var reexecucao = flywayNoEsquema(esquema);
            var resultado = reexecucao.migrate();
            reexecucao.validate();

            assertEquals(0, resultado.migrationsExecuted);
            assertEquals(historicoAntes, contar(esquema + ".flyway_schema_history"));
            assertEquals(1, contar(esquema + ".camaras"));
            assertEquals(1, contar(esquema + ".lotes"));
            assertEquals(versoesDisponiveis(), versoesAplicadas(reexecucao));
        } finally {
            apagar(esquema);
        }
    }

    @Test
    void bancoDaAplicacaoJaIniciaMigradoESemPendencias() {
        assertEquals(0, flyway.info().pending().length);
        assertEquals(versoesDisponiveis(), versoesAplicadas(flyway));
        assertEquals(0, flyway.migrate().migrationsExecuted);
    }

    /** Reaproveita a configuração do Quarkus (local das migrações, leitor de recursos) trocando apenas o esquema. */
    private Flyway flywayNoEsquema(String esquema) {
        return Flyway.configure()
                .configuration(flyway.getConfiguration())
                .schemas(esquema)
                .defaultSchema(esquema)
                .createSchemas(true)
                .load();
    }

    private List<String> versoesDisponiveis() {
        return Arrays.stream(flyway.info().all())
                .map(info -> info.getVersion().getVersion())
                .toList();
    }

    private static List<String> versoesAplicadas(Flyway flyway) {
        var aplicadas = flyway.info().applied();
        assertTrue(Arrays.stream(aplicadas).allMatch(info -> info.getState() == MigrationState.SUCCESS),
                "há migração aplicada sem sucesso");
        return Arrays.stream(aplicadas)
                .filter(info -> info.getVersion() != null)
                .map(MigrationInfo::getVersion)
                .map(versao -> versao.getVersion())
                .toList();
    }

    private static String esquemaNovo() {
        return "migracao_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private Set<String> tabelas(String esquema) throws SQLException {
        return nomes("select table_name from information_schema.tables where table_schema = ?", esquema);
    }

    private Set<String> restricoes(String esquema) throws SQLException {
        return nomes("select constraint_name from information_schema.table_constraints where table_schema = ?",
                esquema);
    }

    private Set<String> nomes(String sql, String esquema) throws SQLException {
        try (var conexao = dataSource.getConnection(); var consulta = conexao.prepareStatement(sql)) {
            consulta.setString(1, esquema);
            var nomes = new HashSet<String>();
            try (var linhas = consulta.executeQuery()) {
                while (linhas.next()) {
                    nomes.add(linhas.getString(1));
                }
            }
            return nomes;
        }
    }

    private long contar(String tabela) throws SQLException {
        try (var conexao = dataSource.getConnection(); var consulta = conexao.createStatement();
             var linhas = consulta.executeQuery("select count(*) from " + tabela)) {
            linhas.next();
            return linhas.getLong(1);
        }
    }

    private void executar(String sql) throws SQLException {
        try (var conexao = dataSource.getConnection(); var comando = conexao.createStatement()) {
            comando.execute(sql);
        }
    }

    private void apagar(String esquema) throws SQLException {
        executar("drop schema if exists " + esquema + " cascade");
    }
}

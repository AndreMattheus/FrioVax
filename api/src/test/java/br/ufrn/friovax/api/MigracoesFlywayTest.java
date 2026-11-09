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
            assertEquals(Set.of("camaras", "lotes", "usuarios", "sessoes", "politicas_termicas",
                "flyway_schema_history"), tabelas(esquema));
            assertTrue(restricoes(esquema).containsAll(Set.of("uk_camaras_codigo", "chk_camaras_estado",
                "chk_camaras_estado_termico", "uk_politicas_camara_versao", "fk_politicas_camara",
                "chk_politicas_faixa", "chk_politicas_limiar", "uk_lotes_codigo", "fk_lotes_camara", "chk_lotes_quantidade",
                "chk_lotes_estado", "chk_lotes_disponivel_com_doses", "chk_lotes_esgotado_sem_doses",
                "uk_usuarios_login", "chk_usuarios_senha_hash", "chk_usuarios_perfil",
                "chk_sessoes_token_hash", "fk_sessoes_usuario", "chk_sessoes_expiracao", "chk_sessoes_revogacao")));
            assertEquals(0, contar(esquema + ".usuarios"));
            assertEquals(0, contar(esquema + ".sessoes"));
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
                    insert into %1$s.camaras (codigo, nome, unidade, capacidade, estado_termico, estado,
                        criado_em, atualizado_em)
                    values ('C-MIGRA', 'Câmara', 'UBS', 100, 'SEM_LEITURAS', 'OPERACIONAL', now(), now());
                    insert into %1$s.politicas_termicas (camara_id, versao, temperatura_minima,
                        temperatura_maxima, limiar, criada_em)
                    select id, 1, 2.0, 8.0, 3, now() from %1$s.camaras where codigo = 'C-MIGRA';
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

    /**
     * S2-05: um banco que já estava na versão 3, com câmaras e lotes da Sprint 1, recebe a V4 sem perder dados. Cada
     * câmara vira a versão 1 da sua própria política, com a faixa que já tinha (inclusive uma faixa de freezer,
     * diferente da demonstração), limiar 3, sem autor e sem leituras inventadas.
     */
    @Test
    void migrarSobreDadosDaSprint1PreservaAFaixaDeCadaCamara() throws SQLException {
        var esquema = esquemaNovo();
        try {
            flywayNoEsquemaAte(esquema, "3").migrate();
            executar("""
                    insert into %1$s.camaras (codigo, nome, unidade, capacidade, temperatura_minima,
                        temperatura_maxima, estado, criado_em, atualizado_em)
                    values ('C-GELADEIRA', 'Geladeira', 'UBS', 100, 2.0, 8.0, 'OPERACIONAL',
                            timestamptz '2026-09-20 10:00:00-03', now()),
                           ('C-FREEZER', 'Freezer', 'UBS', 50, -25.0, -15.5, 'MANUTENCAO',
                            timestamptz '2026-09-21 11:30:00-03', now());
                    insert into %1$s.lotes (codigo, imunobiologico, fabricante, validade, quantidade, camara_id,
                        estado, criado_em, atualizado_em)
                    select 'L-SPRINT1', 'Febre amarela', 'Fabricante', date '2030-01-01', 40, id,
                        'DISPONIVEL', now(), now() from %1$s.camaras where codigo = 'C-GELADEIRA';
                    """.formatted(esquema));

            var resultado = flywayNoEsquema(esquema).migrate();

            assertTrue(resultado.success);
            assertEquals(1, resultado.migrationsExecuted);
            assertEquals(2, contar(esquema + ".politicas_termicas"));
            assertEquals(1, contar(esquema + ".lotes"));
            assertEquals("1|-25.0|-15.5|3|true|true|SEM_LEITURAS|MANUTENCAO", politicaMigrada(esquema, "C-FREEZER"));
            assertEquals("1|2.0|8.0|3|true|true|SEM_LEITURAS|OPERACIONAL", politicaMigrada(esquema, "C-GELADEIRA"));
            assertEquals(Set.of(), nomes("""
                    select column_name from information_schema.columns
                    where table_schema = ? and table_name = 'camaras' and column_name like 'temperatura%'
                    """, esquema), "a faixa deve ter uma única fonte: a política");
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

    /** Igual a {@link #flywayNoEsquema}, mas parando na versão informada, para simular um banco mais antigo. */
    private Flyway flywayNoEsquemaAte(String esquema, String versao) {
        return Flyway.configure()
            .configuration(flyway.getConfiguration())
            .schemas(esquema)
            .defaultSchema(esquema)
            .createSchemas(true)
            .target(versao)
            .load();
    }

    /**
     * Resume a política migrada de uma câmara como "versão|mínima|máxima|limiar|sem autor|criada na data da câmara|
     * estado térmico|estado operacional".
     */
    private String politicaMigrada(String esquema, String codigo) throws SQLException {
        var sql = """
                select p.versao, p.temperatura_minima, p.temperatura_maxima, p.limiar, p.autor_id is null,
                       p.criada_em = c.criado_em, c.estado_termico, c.estado
                from %1$s.politicas_termicas p join %1$s.camaras c on c.id = p.camara_id
                where c.codigo = ?
                """.formatted(esquema);
        try (var conexao = dataSource.getConnection(); var consulta = conexao.prepareStatement(sql)) {
            consulta.setString(1, codigo);
            try (var linha = consulta.executeQuery()) {
                assertTrue(linha.next(), "câmara sem política: " + codigo);
                var resumo = String.join("|", linha.getString(1), linha.getString(2), linha.getString(3),
                    linha.getString(4), String.valueOf(linha.getBoolean(5)), String.valueOf(linha.getBoolean(6)),
                    linha.getString(7), linha.getString(8));
                assertTrue(!linha.next(), "a migração deve criar só uma versão por câmara");
                return resumo;
            }
        }
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

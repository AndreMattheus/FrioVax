package br.ufrn.friovax.api.telemetria.persistencia;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Apoio da prova de persistência após reinício da API (#70), feita em duas classes de teste:
 * {@link PersistenciaAposReinicio1GravacaoTest} grava e {@link PersistenciaAposReinicio2ConferenciaTest} confere.
 *
 * <p>Cada classe usa um perfil diferente. Ao trocar de perfil, o Quarkus encerra a aplicação em execução (pool de
 * conexões, Hibernate e beans) e inicia outra. Os perfis não alteram nenhuma configuração. Tanto o nome dos perfis
 * quanto o das classes deixam a gravação antes da conferência.
 *
 * <p>O banco precisa ser o mesmo nas duas aplicações. Com uma URL de banco informada (PostgreSQL local), é. Sem URL,
 * o Dev Services cria um contêiner novo a cada reinício, a menos que o reaproveitamento de contêineres do
 * Testcontainers esteja habilitado ({@code TESTCONTAINERS_REUSE_ENABLE=true}, como no CI). Sem nenhuma das duas
 * condições, a conferência é ignorada em vez de falhar.
 *
 * <p>A conferência encontra o que foi gravado pelo próprio banco: a câmara tem código com {@link #PREFIXO_CODIGO} e
 * guarda no nome a identidade da aplicação que a gravou ({@link IdentidadeDaAplicacao}).
 */
public final class ReinicioDaApi {

    public static class AntesDoReinicio implements QuarkusTestProfile {
    }

    public static class DepoisDoReinicio implements QuarkusTestProfile {
    }

    static final String PREFIXO_CODIGO = "REINICIO-";

    /**
     * Imunobiológico que nenhum outro teste usa: entre a gravação e a conferência o lote fica no banco, e testes que
     * filtram todos os lotes (como o filtro por imunobiológico do LoteRepositoryPostgresTest) não podem enxergá-lo.
     */
    static final String IMUNOBIOLOGICO = "Teste de reinício da API";

    private ReinicioDaApi() {
    }

    static String nomeDaCamara(String identidadeDaAplicacao) {
        return "Reinício " + identidadeDaAplicacao;
    }

    /** Se a aplicação iniciada depois do reinício usa o mesmo banco da anterior. */
    static boolean bancoSobreviveAoReinicio() {
        return urlInformada() || reaproveitamentoDeConteineresHabilitado();
    }

    private static boolean urlInformada() {
        return System.getenv("QUARKUS_DATASOURCE_JDBC_URL") != null
                || System.getProperty("quarkus.datasource.jdbc.url") != null;
    }

    private static boolean reaproveitamentoDeConteineresHabilitado() {
        if ("true".equalsIgnoreCase(System.getenv("TESTCONTAINERS_REUSE_ENABLE"))) {
            return true;
        }
        var arquivo = Path.of(System.getProperty("user.home"), ".testcontainers.properties");
        try (var leitor = Files.newBufferedReader(arquivo)) {
            var propriedades = new Properties();
            propriedades.load(leitor);
            return "true".equalsIgnoreCase(propriedades.getProperty("testcontainers.reuse.enable"));
        } catch (IOException e) {
            return false;
        }
    }
}

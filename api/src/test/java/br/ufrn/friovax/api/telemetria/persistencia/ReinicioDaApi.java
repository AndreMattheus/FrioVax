package br.ufrn.friovax.api.telemetria.persistencia;

import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * Apoio da prova de persistência após reinício da API (#70), feita em duas classes de teste:
 * {@link PersistenciaAposReinicio1GravacaoTest} grava e {@link PersistenciaAposReinicio2ConferenciaTest} confere.
 *
 * <p>Cada classe usa um perfil diferente. Ao trocar de perfil, o Quarkus encerra a aplicação em execução (pool de
 * conexões, Hibernate e beans) e inicia outra. Os perfis não alteram nenhuma configuração, então a nova aplicação
 * usa o mesmo banco. Tanto o nome dos perfis quanto o das classes deixam a gravação antes da conferência.
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
}

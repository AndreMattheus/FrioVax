package br.ufrn.friovax.api.telemetria.integracao;

import br.ufrn.friovax.api.autenticacao.api.AutenticacaoPostgresProfile;
import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.List;
import java.util.Map;

/**
 * PostgreSQL real (Dev Services ou o banco do CI), serviço Go real em container e contas provisionadas pelo próprio
 * startup da API. A senha é a mesma do perfil de autenticação, porque as contas sobrevivem entre perfis no banco do CI.
 */
public class FluxoRealProfile implements QuarkusTestProfile {

    public static final String SENHA = AutenticacaoPostgresProfile.SENHA;

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("friovax.auth.demo.enabled", "true",
                "friovax.auth.demo.administrador", SENHA,
                "friovax.auth.demo.operador", SENHA,
                "friovax.auth.demo.auditor", SENHA);
    }

    @Override
    public List<TestResourceEntry> testResources() {
        return List.of(new TestResourceEntry(ServicoGoResource.class));
    }
}

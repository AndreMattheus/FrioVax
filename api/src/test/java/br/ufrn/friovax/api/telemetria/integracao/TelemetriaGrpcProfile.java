package br.ufrn.friovax.api.telemetria.integracao;

import br.ufrn.friovax.api.suporte.HttpEmMemoriaProfile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Perfil em memória com o cliente gRPC apontado para o servidor de teste e prazo próprio. */
public class TelemetriaGrpcProfile extends HttpEmMemoriaProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        var configuracao = new HashMap<>(super.getConfigOverrides());
        configuracao.remove("quarkus.grpc.clients.telemetria.test-port");
        return configuracao;
    }

    @Override
    public List<TestResourceEntry> testResources() {
        return List.of(new TestResourceEntry(ServidorTelemetriaResource.class));
    }
}

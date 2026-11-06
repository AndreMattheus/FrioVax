package br.ufrn.friovax.api.autenticacao.persistencia;

import io.quarkus.runtime.StartupEvent;
import io.smallrye.jwt.auth.principal.JWTAuthContextInfo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class ConfiguracaoJwt {
    @Inject JWTAuthContextInfo contexto;
    @ConfigProperty(name = "smallrye.jwt.sign.key.location") String chavePrivada;

    void iniciar(@Observes StartupEvent evento) {
        if (chavePrivada.isBlank()) throw new IllegalStateException("Chave privada JWT obrigatória.");
        // SmallRye JWT 4.6.3 ignora clock.skew=0 no producer e mantém o padrão de 60 s.
        // Ajustar o contexto do verificador preserva o limite de 15 min, sem tolerância extra.
        contexto.setClockSkew(0);
        contexto.setExpGracePeriodSecs(0);
    }
}

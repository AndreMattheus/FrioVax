package br.ufrn.friovax.api.autenticacao.persistencia;

import br.ufrn.friovax.api.autenticacao.dominio.EmissorAccessToken;
import br.ufrn.friovax.api.autenticacao.dominio.Identidade;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.time.Instant;
import java.util.Set;

@ApplicationScoped
public class EmissorAccessTokenSmallRye implements EmissorAccessToken {
    @ConfigProperty(name = "mp.jwt.verify.issuer") String emissor;

    @Override
    public String emitir(Identidade identidade, Instant emitidoEm, Instant expiraEm) {
        return Jwt.issuer(emissor).subject(identidade.id().toString()).upn(identidade.login())
                .groups(Set.of(identidade.perfil().name()))
                .issuedAt(emitidoEm).expiresAt(expiraEm).sign();
    }
}

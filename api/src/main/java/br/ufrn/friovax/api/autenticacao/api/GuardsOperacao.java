package br.ufrn.friovax.api.autenticacao.api;

import br.ufrn.friovax.api.autenticacao.dominio.MatrizAcesso;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

/** Associa permissões apenas aos perfis da identidade validada pelo mecanismo Bearer. */
@ApplicationScoped
public class GuardsOperacao implements SecurityIdentityAugmentor {
    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identidade, AuthenticationRequestContext contexto) {
        if (identidade.isAnonymous()) return Uni.createFrom().item(identidade);
        var builder = QuarkusSecurityIdentity.builder(identidade);
        for (String permissao : MatrizAcesso.permissoes()) {
            if (MatrizAcesso.permite(permissao, identidade.getRoles())) {
                builder.addPermissionAsString(permissao);
            }
        }
        return Uni.createFrom().item(builder.build());
    }
}

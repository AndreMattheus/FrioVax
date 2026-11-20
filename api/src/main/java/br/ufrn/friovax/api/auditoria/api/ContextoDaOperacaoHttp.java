package br.ufrn.friovax.api.auditoria.api;

import br.ufrn.friovax.api.auditoria.dominio.Ator;
import br.ufrn.friovax.api.auditoria.dominio.ContextoDaOperacao;
import br.ufrn.friovax.api.autenticacao.dominio.Perfil;
import br.ufrn.friovax.api.compartilhado.api.CorrelacaoDaRequisicao;
import io.quarkus.arc.Arc;
import io.quarkus.security.identity.CurrentIdentityAssociation;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.Arrays;
import java.util.UUID;

/**
 * Deriva o ator e a correlação da requisição atual. O ator vem só do JWT validado pelo Quarkus: {@code sub} (id),
 * {@code upn} (login) e o perfil em {@code groups}. Nada vem do body, então não dá para forjar o autor de uma
 * alteração. Sem requisição ou sem usuário autenticado, o ator é {@link Ator#SISTEMA}.
 */
@ApplicationScoped
public class ContextoDaOperacaoHttp implements ContextoDaOperacao {

    private final CurrentIdentityAssociation identidades;
    private final CorrelacaoDaRequisicao correlacao;

    @Inject
    public ContextoDaOperacaoHttp(CurrentIdentityAssociation identidades, CorrelacaoDaRequisicao correlacao) {
        this.identidades = identidades;
        this.correlacao = correlacao;
    }

    @Override
    public Ator ator() {
        if (!requisicaoAtiva()) {
            return Ator.SISTEMA;
        }
        var identidade = identidades.getIdentity();
        if (identidade == null || identidade.isAnonymous()
                || !(identidade.getPrincipal() instanceof JsonWebToken jwt) || jwt.getSubject() == null) {
            return Ator.SISTEMA;
        }
        var perfil = Arrays.stream(Perfil.values()).filter(p -> identidade.hasRole(p.name())).findFirst();
        if (perfil.isEmpty()) {
            return Ator.SISTEMA;
        }
        return Ator.usuario(UUID.fromString(jwt.getSubject()), jwt.getName(), perfil.get());
    }

    @Override
    public String correlacaoId() {
        if (requisicaoAtiva() && correlacao.id() != null) {
            return correlacao.id();
        }
        return "sistema-" + UUID.randomUUID();
    }

    private static boolean requisicaoAtiva() {
        return Arc.container().requestContext().isActive();
    }
}

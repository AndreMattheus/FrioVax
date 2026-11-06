package br.ufrn.friovax.api.autenticacao.aplicacao;

import br.ufrn.friovax.api.autenticacao.dominio.*;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ProvisionarContas {
    @Inject AutenticacaoRepository repositorio;
    @Inject ProtecaoCredenciais protecao;
    @ConfigProperty(name = "friovax.auth.demo.enabled", defaultValue = "false") boolean habilitado;
    @ConfigProperty(name = "friovax.auth.demo.administrador") Optional<String> administrador;
    @ConfigProperty(name = "friovax.auth.demo.operador") Optional<String> operador;
    @ConfigProperty(name = "friovax.auth.demo.auditor") Optional<String> auditor;

    @Transactional
    void iniciar(@Observes StartupEvent evento) {
        if (!habilitado) return;
        var senhas = new String[]{validar(administrador), validar(operador), validar(auditor)};
        var perfis = Perfil.values();
        for (int i = 0; i < perfis.length; i++) {
            var login = perfis[i].name().toLowerCase(java.util.Locale.ROOT);
            if (repositorio.buscarPorLogin(login).isEmpty()) {
                repositorio.adicionar(new Credencial(new Identidade(UUID.randomUUID(), login, perfis[i]),
                        protecao.protegerSenha(senhas[i])));
            }
        }
    }

    private String validar(Optional<String> senha) {
        if (senha.isEmpty() || senha.get().isBlank() || senha.get().length() < 12
                || senha.get().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("Provisionamento exige três senhas de 12 caracteres a 72 bytes.");
        }
        return senha.get();
    }
}

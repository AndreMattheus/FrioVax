package br.ufrn.friovax.api.arquitetura;

import br.ufrn.friovax.api.autenticacao.dominio.MatrizAcesso;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.quarkus.security.Authenticated;
import io.quarkus.security.PermissionsAllowed;
import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.Path;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Toda operação HTTP de produção precisa escolher uma permissão explicitamente. */
class AutorizacaoRotasTest {
    private static final Set<String> PUBLICAS = Set.of(
            "GET /api/status", "POST /api/auth/login", "POST /api/auth/renovar", "POST /api/auth/logout");

    @Test
    void exigeGuardaPorMetodoEExcecoesPublicasRestritas() {
        var guards = MatrizAcesso.permissoes();
        var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("br.ufrn.friovax.api");
        int verificadas = 0;
        for (var tipo : classes) {
            Class<?> classe = tipo.reflect();
            if (!classe.isAnnotationPresent(Path.class)) continue;
            assertFalse(classe.isAnnotationPresent(PermitAll.class), classe.getName());
            assertFalse(classe.isAnnotationPresent(Authenticated.class), classe.getName());
            assertFalse(classe.isAnnotationPresent(PermissionsAllowed.class), classe.getName());
            for (var metodo : classe.getDeclaredMethods()) {
                var verbo = Arrays.stream(metodo.getAnnotations())
                        .map(a -> a.annotationType().getAnnotation(HttpMethod.class))
                        .filter(a -> a != null).findFirst();
                if (verbo.isEmpty()) continue;
                verificadas++;
                String rota = verbo.get().value() + " " + classe.getAnnotation(Path.class).value()
                        + (metodo.isAnnotationPresent(Path.class) ? metodo.getAnnotation(Path.class).value() : "");
                if (PUBLICAS.contains(rota)) {
                    assertTrue(metodo.isAnnotationPresent(PermitAll.class), rota);
                } else if (rota.equals("GET /api/auth/identidade")) {
                    assertTrue(metodo.isAnnotationPresent(Authenticated.class), rota);
                } else {
                    assertFalse(metodo.isAnnotationPresent(PermitAll.class), rota);
                    assertTrue(metodo.isAnnotationPresent(PermissionsAllowed.class), rota);
                    for (String permissao : metodo.getAnnotation(PermissionsAllowed.class).value()) {
                        assertTrue(guards.contains(permissao), rota + ": guarda desconhecida " + permissao);
                    }
                }
            }
        }
        assertTrue(verificadas >= 17, "A verificação deve incluir todas as rotas de produção");
    }
}

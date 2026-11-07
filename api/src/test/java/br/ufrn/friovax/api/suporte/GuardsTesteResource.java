package br.ufrn.friovax.api.suporte;

import io.quarkus.security.PermissionsAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;

import java.util.concurrent.atomic.AtomicInteger;

import static br.ufrn.friovax.api.autenticacao.dominio.MatrizAcesso.*;

/** Rotas apenas do classpath de teste para as fatias ainda não implementadas. */
@Path("/teste/guards")
public class GuardsTesteResource {
    public static final AtomicInteger EXECUCOES = new AtomicInteger();
    public static final AtomicInteger CHAMADAS_GO = new AtomicInteger();

    @POST @Path("/leitura") @PermissionsAllowed(REGISTRAR_LEITURA)
    @Operation(hidden = true)
    public void leitura() {
        EXECUCOES.incrementAndGet();
        CHAMADAS_GO.incrementAndGet();
    }

    @POST @Path("/politica") @PermissionsAllowed(ALTERAR_POLITICA)
    @Operation(hidden = true)
    public void politica() {
        EXECUCOES.incrementAndGet();
    }

    @POST @Path("/resolucao") @PermissionsAllowed(RESOLVER_INCIDENTE)
    @Operation(hidden = true)
    public void resolucao() {
        EXECUCOES.incrementAndGet();
    }

    @GET @Path("/trilha") @PermissionsAllowed(CONSULTAR_TRILHA)
    @Operation(hidden = true)
    public void trilha() {
        EXECUCOES.incrementAndGet();
    }

    @POST @Path("/sem-guarda")
    @Operation(hidden = true)
    public void semGuarda() {
        EXECUCOES.incrementAndGet();
    }
}

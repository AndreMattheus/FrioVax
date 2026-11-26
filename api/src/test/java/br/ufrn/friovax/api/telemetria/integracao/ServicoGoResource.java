package br.ufrn.friovax.api.telemetria.integracao;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Constrói a imagem de {@code services/telemetry} pelo mesmo Dockerfile do Compose e a executa com Testcontainers,
 * apontando o cliente gRPC da API para ela. Nada substitui o transporte: a API fala com o binário Go real.
 *
 * <p>As portas do host são fixadas na criação do container, para que {@link #parar()} seguido de {@link #iniciar()}
 * devolva o serviço no mesmo endereço que o Quarkus já configurou. {@link #congelar()} suspende os processos do
 * container sem fechar a porta: a conexão continua aceita, mas nenhuma resposta chega, o que reproduz um Go lento.
 */
public class ServicoGoResource implements QuarkusTestResourceLifecycleManager {

    static final int PORTA_HTTP = 8081;
    static final int PORTA_GRPC = 9090;
    private static final Pattern ANALISE = Pattern.compile(
            "AnalyzeReadings correlacao=(\\S+) codigo=(\\S+) duracao=(\\S+)");

    private static volatile GenericContainer<?> go;
    private static volatile int portaHttp;

    @Override
    public Map<String, String> start() {
        var contexto = Path.of(System.getProperty("user.dir")).resolve("../services/telemetry").normalize();
        if (!Files.isRegularFile(contexto.resolve("Dockerfile"))) {
            throw new IllegalStateException("Dockerfile do serviço Go não encontrado em " + contexto);
        }
        int portaGrpc = portaLivre();
        portaHttp = portaLivre();
        var container = new GenericContainer<>(new ImageFromDockerfile().withDockerfile(contexto.resolve("Dockerfile")))
                .withEnv(Map.of("PORT", String.valueOf(PORTA_HTTP), "GRPC_PORT", String.valueOf(PORTA_GRPC),
                        "SHUTDOWN_TIMEOUT", "2s"))
                .withExposedPorts(PORTA_HTTP, PORTA_GRPC)
                .withCreateContainerCmdModifier(comando -> comando.getHostConfig().withPortBindings(
                        new PortBinding(Ports.Binding.bindIpAndPort("127.0.0.1", portaGrpc),
                                ExposedPort.tcp(PORTA_GRPC)),
                        new PortBinding(Ports.Binding.bindIpAndPort("127.0.0.1", portaHttp),
                                ExposedPort.tcp(PORTA_HTTP))))
                .waitingFor(Wait.forHttp("/health").forPort(PORTA_HTTP).forStatusCode(200))
                .withStartupTimeout(Duration.ofMinutes(5));
        container.start();
        go = container;
        // Sem sobrescrever friovax.telemetria.prazo: vale o mesmo prazo de produção (800 ms).
        return Map.of(
                "quarkus.grpc.clients.telemetria.host", "127.0.0.1",
                "quarkus.grpc.clients.telemetria.test-port", String.valueOf(portaGrpc));
    }

    @Override
    public void stop() {
        if (go != null) {
            go.stop();
            go = null;
        }
    }

    /** Encerra o processo Go (SIGTERM, como {@code docker stop}); a porta passa a recusar conexões. */
    public static void parar() {
        docker().stopContainerCmd(go.getContainerId()).withTimeout(5).exec();
    }

    /** Inicia de novo o container parado e espera o {@code /health} responder 200. */
    public static void iniciar() {
        docker().startContainerCmd(go.getContainerId()).exec();
        aguardarSaude(Duration.ofSeconds(60));
    }

    /** Congela os processos do container: conexões continuam aceitas, mas nenhuma RPC é respondida. */
    public static void congelar() {
        docker().pauseContainerCmd(go.getContainerId()).exec();
    }

    public static void descongelar() {
        docker().unpauseContainerCmd(go.getContainerId()).exec();
    }

    /** Análises registradas pelo próprio Go no log ({@code metodo=... correlacao=... codigo=...}). */
    public static List<AnaliseNoLog> analisesNoLog() {
        var analises = new ArrayList<AnaliseNoLog>();
        var busca = ANALISE.matcher(go.getLogs());
        while (busca.find()) {
            analises.add(new AnaliseNoLog(busca.group(1), busca.group(2), busca.group(3)));
        }
        return analises;
    }

    public static String imagem() {
        return go.getDockerImageName();
    }

    public record AnaliseNoLog(String correlacao, String codigo, String duracao) {
    }

    private static com.github.dockerjava.api.DockerClient docker() {
        return DockerClientFactory.instance().client();
    }

    private static void aguardarSaude(Duration espera) {
        var cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
        var saude = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + portaHttp + "/health"))
                .timeout(Duration.ofSeconds(2)).build();
        long limite = System.nanoTime() + espera.toNanos();
        while (System.nanoTime() < limite) {
            try {
                if (cliente.send(saude, HttpResponse.BodyHandlers.discarding()).statusCode() == 200) {
                    return;
                }
            } catch (IOException ainda) {
                // O processo ainda não abriu a porta.
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            dormir(200);
        }
        throw new IllegalStateException("O serviço Go não voltou a responder /health em " + espera);
    }

    private static int portaLivre() {
        try (var socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void dormir(long milissegundos) {
        try {
            Thread.sleep(milissegundos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}

package servidor

import (
	"bytes"
	"context"
	"fmt"
	"io"
	"log"
	"net"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	telemetryv1 "github.com/AndreMattheus/FrioVax/services/telemetry/gen/friovax/telemetry/v1"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/credentials/insecure"
	healthv1 "google.golang.org/grpc/health/grpc_health_v1"
	reflectionv1 "google.golang.org/grpc/reflection/grpc_reflection_v1"
	"google.golang.org/grpc/status"
	"google.golang.org/grpc/test/bufconn"
)

type servicoTeste struct {
	telemetryv1.UnimplementedTelemetryServiceServer
	analisar func(context.Context, *telemetryv1.AnalyzeReadingsRequest) (*telemetryv1.AnalyzeReadingsResponse, error)
}

func (s servicoTeste) AnalyzeReadings(ctx context.Context, r *telemetryv1.AnalyzeReadingsRequest) (*telemetryv1.AnalyzeReadingsResponse, error) {
	return s.analisar(ctx, r)
}

func sucesso(_ context.Context, r *telemetryv1.AnalyzeReadingsRequest) (*telemetryv1.AnalyzeReadingsResponse, error) {
	return &telemetryv1.AnalyzeReadingsResponse{CorrelationId: r.GetCorrelationId()}, nil
}

type registro struct {
	mu sync.Mutex
	bytes.Buffer
}

func (r *registro) Write(p []byte) (int, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	return r.Buffer.Write(p)
}
func (r *registro) texto() string { r.mu.Lock(); defer r.mu.Unlock(); return r.Buffer.String() }

type ambienteTeste struct {
	s            *servidor
	listener     *bufconn.Listener
	httpListener net.Listener
	conexao      *grpc.ClientConn
	cancelar     context.CancelFunc
	done         chan struct{}
	err          error
	logs         *registro
}

func configuracaoTeste(t *testing.T) Configuracao {
	t.Helper()
	c, err := LerConfiguracao(func(string) string { return "" })
	if err != nil {
		t.Fatal(err)
	}
	return c
}

func iniciar(t *testing.T, c Configuracao, servico servicoTeste) *ambienteTeste {
	t.Helper()
	a := &ambienteTeste{listener: bufconn.Listen(1024 * 1024), done: make(chan struct{}), logs: &registro{}}
	var err error
	a.httpListener, err = net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	a.s = novo(c, servico, log.New(a.logs, "", 0))
	ctx, cancelar := context.WithCancel(t.Context())
	a.cancelar = cancelar
	go func() { a.err = a.s.servir(ctx, a.httpListener, a.listener); close(a.done) }()
	t.Cleanup(func() { cancelar(); aguardar(t, a.done); _ = a.listener.Close(); _ = a.httpListener.Close() })
	a.conexao = a.conectar(t)
	ctxSaude, fim := context.WithTimeout(t.Context(), 3*time.Second)
	defer fim()
	if _, err := healthv1.NewHealthClient(a.conexao).Check(ctxSaude, &healthv1.HealthCheckRequest{}, grpc.WaitForReady(true)); err != nil {
		t.Fatal(err)
	}
	return a
}

func (a *ambienteTeste) conectar(t *testing.T) *grpc.ClientConn {
	t.Helper()
	c, err := grpc.NewClient("passthrough:///teste", grpc.WithTransportCredentials(insecure.NewCredentials()), grpc.WithContextDialer(func(ctx context.Context, _ string) (net.Conn, error) { return a.listener.DialContext(ctx) }))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = c.Close() })
	return c
}

func aguardar(t *testing.T, ch <-chan struct{}) {
	t.Helper()
	select {
	case <-ch:
	case <-time.After(3 * time.Second):
		t.Fatal("operação não terminou no prazo do teste")
	}
}

func chamar(t *testing.T, c *grpc.ClientConn, r *telemetryv1.AnalyzeReadingsRequest) error {
	t.Helper()
	ctx, fim := context.WithTimeout(t.Context(), 2*time.Second)
	defer fim()
	_, err := telemetryv1.NewTelemetryServiceClient(c).AnalyzeReadings(ctx, r)
	return err
}

func TestConfiguracao(t *testing.T) {
	c := configuracaoTeste(t)
	if c.PortaHTTP != 8081 || c.PortaGRPC != 9090 || c.MaximoBytes != 4194304 || c.MaximoAnalises != 16 || c.PrazoEncerramento != 5*time.Second {
		t.Fatal(c)
	}
	valores := map[string]string{"PORT": "8082", "GRPC_PORT": "9091", "GRPC_MAX_MESSAGE_BYTES": "2048", "GRPC_MAX_CONCURRENT_ANALYSES": "2", "SHUTDOWN_TIMEOUT": "100ms"}
	c, err := LerConfiguracao(func(nome string) string { return valores[nome] })
	if err != nil || c.PortaHTTP != 8082 || c.PortaGRPC != 9091 || c.MaximoBytes != 2048 || c.MaximoAnalises != 2 || c.PrazoEncerramento != 100*time.Millisecond {
		t.Fatalf("%+v %v", c, err)
	}
	for _, caso := range []struct{ nome, valor string }{
		{"PORT", "0"}, {"PORT", "65536"}, {"PORT", "9090"}, {"GRPC_PORT", "8081"}, {"GRPC_PORT", "abc"},
		{"GRPC_MAX_MESSAGE_BYTES", "0"}, {"GRPC_MAX_MESSAGE_BYTES", "999999999999999999999999"},
		{"GRPC_MAX_CONCURRENT_ANALYSES", "-1"}, {"SHUTDOWN_TIMEOUT", "0s"}, {"SHUTDOWN_TIMEOUT", "-1s"}, {"SHUTDOWN_TIMEOUT", "abc"},
	} {
		t.Run(caso.nome+caso.valor, func(t *testing.T) {
			if _, err := LerConfiguracao(func(nome string) string {
				if nome == caso.nome {
					return caso.valor
				}
				return ""
			}); err == nil {
				t.Fatal("configuração inválida aceita")
			}
		})
	}
}

func TestSaudeHTTPGRPCReflectionELogs(t *testing.T) {
	a := iniciar(t, configuracaoTeste(t), servicoTeste{analisar: sucesso})
	ctx, fim := context.WithTimeout(t.Context(), 2*time.Second)
	defer fim()
	for _, nome := range []string{"", telemetryv1.TelemetryService_ServiceDesc.ServiceName} {
		r, err := healthv1.NewHealthClient(a.conexao).Check(ctx, &healthv1.HealthCheckRequest{Service: nome})
		if err != nil || r.GetStatus() != healthv1.HealthCheckResponse_SERVING {
			t.Fatalf("saúde: %v %v", r, err)
		}
	}
	clienteHTTP := &http.Client{Timeout: 2 * time.Second}
	r, err := clienteHTTP.Get("http://" + a.httpListener.Addr().String() + "/health")
	if err != nil {
		t.Fatal(err)
	}
	corpo, err := io.ReadAll(r.Body)
	_ = r.Body.Close()
	if err != nil || r.StatusCode != 200 || strings.TrimSpace(string(corpo)) != `{"status":"UP"}` {
		t.Fatalf("HTTP: %v %s %v", r.StatusCode, corpo, err)
	}
	stream, err := reflectionv1.NewServerReflectionClient(a.conexao).ServerReflectionInfo(ctx)
	if err != nil {
		t.Fatal(err)
	}
	if err := stream.Send(&reflectionv1.ServerReflectionRequest{MessageRequest: &reflectionv1.ServerReflectionRequest_ListServices{ListServices: ""}}); err != nil {
		t.Fatal(err)
	}
	lista, err := stream.Recv()
	if err != nil {
		t.Fatal(err)
	}
	encontrou := false
	for _, servico := range lista.GetListServicesResponse().GetService() {
		if servico.Name == telemetryv1.TelemetryService_ServiceDesc.ServiceName {
			encontrou = true
		}
	}
	if !encontrou {
		t.Fatal("reflection não listou TelemetryService")
	}
	_ = stream.CloseSend()
	for _, correlacao := range []string{"teste-72", "injetar\nsegredo"} {
		if err := chamar(t, a.conexao, &telemetryv1.AnalyzeReadingsRequest{CorrelationId: correlacao, Windows: []*telemetryv1.ReadingWindow{{Candidates: []*telemetryv1.Reading{{ExternalId: "payload-secreto"}}}}}); err != nil {
			t.Fatal(err)
		}
	}
	texto := a.logs.texto()
	if !strings.Contains(texto, "correlacao=teste-72 codigo=OK duracao=") || !strings.Contains(texto, "correlacao=ausente-ou-invalida") || strings.Contains(texto, "segredo") || strings.Contains(texto, "payload-secreto") {
		t.Fatal(texto)
	}
}

func TestLimiteGlobalELiberacaoDeVagas(t *testing.T) {
	for _, modo := range []string{"sucesso", "erro", "panic", "cancelamento"} {
		t.Run(modo, func(t *testing.T) {
			iniciou, liberar, terminou := make(chan struct{}), make(chan struct{}), make(chan struct{})
			var chamadas atomic.Int32
			c := configuracaoTeste(t)
			c.MaximoAnalises = 1
			a := iniciar(t, c, servicoTeste{analisar: func(ctx context.Context, r *telemetryv1.AnalyzeReadingsRequest) (*telemetryv1.AnalyzeReadingsResponse, error) {
				if chamadas.Add(1) > 1 {
					return sucesso(ctx, r)
				}
				defer close(terminou)
				close(iniciou)
				select {
				case <-liberar:
				case <-ctx.Done():
					return nil, status.FromContextError(ctx.Err()).Err()
				}
				switch modo {
				case "erro":
					return nil, status.Error(codes.InvalidArgument, "entrada inválida")
				case "panic":
					panic("segredo")
				}
				return sucesso(ctx, r)
			}})
			outra := a.conectar(t)
			ctx, cancelar := context.WithCancel(t.Context())
			defer cancelar()
			resultado := make(chan error, 1)
			go func() {
				_, err := telemetryv1.NewTelemetryServiceClient(a.conexao).AnalyzeReadings(ctx, &telemetryv1.AnalyzeReadingsRequest{CorrelationId: "ocupada"})
				resultado <- err
			}()
			aguardar(t, iniciou)
			if err := chamar(t, outra, &telemetryv1.AnalyzeReadingsRequest{CorrelationId: "excesso"}); status.Code(err) != codes.ResourceExhausted {
				t.Fatal(err)
			}
			ctxSaude, fim := context.WithTimeout(t.Context(), time.Second)
			_, err := healthv1.NewHealthClient(outra).Check(ctxSaude, &healthv1.HealthCheckRequest{})
			fim()
			if err != nil {
				t.Fatal("saúde bloqueada pelo limite", err)
			}
			if modo == "cancelamento" {
				cancelar()
			} else {
				close(liberar)
			}
			aguardar(t, terminou)
			esperado := map[string]codes.Code{"sucesso": codes.OK, "erro": codes.InvalidArgument, "panic": codes.Internal, "cancelamento": codes.Canceled}[modo]
			select {
			case err := <-resultado:
				if status.Code(err) != esperado {
					t.Fatal(err)
				}
			case <-time.After(3 * time.Second):
				t.Fatal("chamada não terminou")
			}
			// O cancelamento pode chegar ao cliente antes do defer que libera a vaga.
			prazo := time.Now().Add(time.Second)
			for len(a.s.vagas) != 0 && time.Now().Before(prazo) {
				time.Sleep(time.Millisecond)
			}
			if err := chamar(t, outra, &telemetryv1.AnalyzeReadingsRequest{CorrelationId: "nova"}); err != nil {
				t.Fatal("vaga não liberada", err)
			}
			if strings.Contains(a.logs.texto(), "segredo") {
				t.Fatal("panic exposto no log")
			}
		})
	}
}

func TestLimitesDeMensagem(t *testing.T) {
	for _, envio := range []bool{false, true} {
		t.Run(fmt.Sprintf("resposta=%t", envio), func(t *testing.T) {
			c := configuracaoTeste(t)
			c.MaximoBytes = 256
			a := iniciar(t, c, servicoTeste{analisar: func(ctx context.Context, r *telemetryv1.AnalyzeReadingsRequest) (*telemetryv1.AnalyzeReadingsResponse, error) {
				if envio {
					return &telemetryv1.AnalyzeReadingsResponse{CorrelationId: strings.Repeat("x", 512)}, nil
				}
				return sucesso(ctx, r)
			}})
			r := &telemetryv1.AnalyzeReadingsRequest{CorrelationId: "pequena"}
			if !envio {
				r.CorrelationId = strings.Repeat("x", 512)
			}
			if err := chamar(t, a.conexao, r); status.Code(err) != codes.ResourceExhausted {
				t.Fatal(err)
			}
		})
	}
}

func TestEncerramentoGraciosoEForcado(t *testing.T) {
	for _, forcar := range []bool{false, true} {
		t.Run(fmt.Sprintf("forcado=%t", forcar), func(t *testing.T) {
			iniciou, liberar, interrompeu := make(chan struct{}), make(chan struct{}), make(chan struct{})
			c := configuracaoTeste(t)
			c.PrazoEncerramento = 200 * time.Millisecond
			a := iniciar(t, c, servicoTeste{analisar: func(ctx context.Context, r *telemetryv1.AnalyzeReadingsRequest) (*telemetryv1.AnalyzeReadingsResponse, error) {
				close(iniciou)
				select {
				case <-liberar:
					return sucesso(ctx, r)
				case <-ctx.Done():
					close(interrompeu)
					return nil, status.FromContextError(ctx.Err()).Err()
				}
			}})
			resultado := make(chan error, 1)
			go func() {
				resultado <- chamar(t, a.conexao, &telemetryv1.AnalyzeReadingsRequest{CorrelationId: "em-andamento"})
			}()
			aguardar(t, iniciou)
			inicio := time.Now()
			a.cancelar()
			for {
				a.s.mu.Lock()
				pronto := a.s.pronto
				a.s.mu.Unlock()
				if !pronto {
					break
				}
				if time.Since(inicio) > time.Second {
					t.Fatal("servidor não iniciou encerramento")
				}
				time.Sleep(time.Millisecond)
			}
			gravador := httptest.NewRecorder()
			a.s.http.Handler.ServeHTTP(gravador, httptest.NewRequest("GET", "/health", nil))
			if gravador.Code != 503 {
				t.Fatal("HTTP continuou pronto")
			}
			r, _ := a.s.saude.Check(t.Context(), &healthv1.HealthCheckRequest{})
			if r.GetStatus() != healthv1.HealthCheckResponse_NOT_SERVING {
				t.Fatal("gRPC continuou pronto")
			}
			if err := chamar(t, a.conexao, &telemetryv1.AnalyzeReadingsRequest{}); status.Code(err) != codes.Unavailable {
				t.Fatal("nova análise aceita", err)
			}
			if !forcar {
				close(liberar)
			}
			aguardar(t, a.done)
			if a.err != nil {
				t.Fatal(a.err)
			}
			if forcar {
				aguardar(t, interrompeu)
				if time.Since(inicio) < c.PrazoEncerramento || !strings.Contains(a.logs.texto(), "encerramento forçado") {
					t.Fatal(a.logs.texto())
				}
			} else if !strings.Contains(a.logs.texto(), "encerramento concluído") {
				t.Fatal(a.logs.texto())
			}
			select {
			case err := <-resultado:
				if !forcar && err != nil {
					t.Fatal(err)
				}
			case <-time.After(time.Second):
				t.Fatal("cliente não terminou")
			}
		})
	}
}

func TestFalhaDeListenerEncerraOutroServidor(t *testing.T) {
	for _, httpFalha := range []bool{false, true} {
		t.Run(fmt.Sprintf("http=%t", httpFalha), func(t *testing.T) {
			a := iniciar(t, configuracaoTeste(t), servicoTeste{analisar: sucesso})
			if httpFalha {
				_ = a.httpListener.Close()
			} else {
				_ = a.listener.Close()
			}
			aguardar(t, a.done)
			if a.err == nil {
				t.Fatal("falha de listener ignorada")
			}
			if err := chamar(t, a.conexao, &telemetryv1.AnalyzeReadingsRequest{}); status.Code(err) != codes.Unavailable {
				t.Fatal(err)
			}
			cliente := &http.Client{Timeout: time.Second}
			if r, err := cliente.Get("http://" + a.httpListener.Addr().String() + "/health"); err == nil {
				_ = r.Body.Close()
				t.Fatal("HTTP permaneceu aberto")
			}
		})
	}
}

func TestEncerramentoForcaConexaoHTTPAtiva(t *testing.T) {
	c := configuracaoTeste(t)
	c.PrazoEncerramento = 100 * time.Millisecond
	a := iniciar(t, c, servicoTeste{analisar: sucesso})
	iniciou, interrompeu, clienteTerminou := make(chan struct{}), make(chan struct{}), make(chan struct{})
	a.s.http.Handler.(*http.ServeMux).HandleFunc("GET /ocupado", func(_ http.ResponseWriter, r *http.Request) {
		close(iniciou)
		<-r.Context().Done()
		close(interrompeu)
	})
	go func() {
		defer close(clienteTerminou)
		cliente := &http.Client{Timeout: 2 * time.Second}
		if r, err := cliente.Get("http://" + a.httpListener.Addr().String() + "/ocupado"); err == nil {
			_ = r.Body.Close()
		}
	}()
	aguardar(t, iniciou)
	a.cancelar()
	aguardar(t, a.done)
	aguardar(t, interrompeu)
	aguardar(t, clienteTerminou)
	if !strings.Contains(a.logs.texto(), "encerramento forçado") {
		t.Fatal(a.logs.texto())
	}
}

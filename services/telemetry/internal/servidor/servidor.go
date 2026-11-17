package servidor

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"net"
	"net/http"
	"regexp"
	"sync"
	"time"

	telemetryv1 "github.com/AndreMattheus/FrioVax/services/telemetry/gen/friovax/telemetry/v1"
	adaptador "github.com/AndreMattheus/FrioVax/services/telemetry/internal/grpc"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/health"
	healthv1 "google.golang.org/grpc/health/grpc_health_v1"
	"google.golang.org/grpc/reflection"
	"google.golang.org/grpc/status"
)

var correlacaoValida = regexp.MustCompile(`^[A-Za-z0-9._:-]{1,128}$`)

type servidor struct {
	grpc   *grpc.Server
	http   *http.Server
	saude  *health.Server
	logger *log.Logger
	config Configuracao
	mu     sync.Mutex
	pronto bool
	vagas  chan struct{}
}

func Executar(ctx context.Context, config Configuracao, logger *log.Logger) error {
	if err := config.validar(); err != nil {
		return err
	}
	httpListener, err := net.Listen("tcp", fmt.Sprintf(":%d", config.PortaHTTP))
	if err != nil {
		return fmt.Errorf("abrir porta HTTP: %w", err)
	}
	defer httpListener.Close()
	grpcListener, err := net.Listen("tcp", fmt.Sprintf(":%d", config.PortaGRPC))
	if err != nil {
		return fmt.Errorf("abrir porta gRPC: %w", err)
	}
	defer grpcListener.Close()
	return novo(config, adaptador.NovoServico(), logger).servir(ctx, httpListener, grpcListener)
}

func novo(config Configuracao, servico telemetryv1.TelemetryServiceServer, logger *log.Logger) *servidor {
	s := &servidor{config: config, logger: logger, saude: health.NewServer(), vagas: make(chan struct{}, config.MaximoAnalises)}
	s.grpc = grpc.NewServer(grpc.MaxRecvMsgSize(config.MaximoBytes), grpc.MaxSendMsgSize(config.MaximoBytes), grpc.UnaryInterceptor(s.interceptar))
	telemetryv1.RegisterTelemetryServiceServer(s.grpc, servico)
	healthv1.RegisterHealthServer(s.grpc, s.saude)
	reflection.Register(s.grpc)
	s.saude.SetServingStatus("", healthv1.HealthCheckResponse_NOT_SERVING)
	s.saude.SetServingStatus(telemetryv1.TelemetryService_ServiceDesc.ServiceName, healthv1.HealthCheckResponse_NOT_SERVING)
	mux := http.NewServeMux()
	mux.HandleFunc("GET /health", func(w http.ResponseWriter, _ *http.Request) {
		s.mu.Lock()
		pronto := s.pronto
		s.mu.Unlock()
		w.Header().Set("Content-Type", "application/json")
		estado := "UP"
		if !pronto {
			estado = "DOWN"
			w.WriteHeader(http.StatusServiceUnavailable)
		}
		_ = json.NewEncoder(w).Encode(map[string]string{"status": estado})
	})
	s.http = &http.Server{Handler: mux, ReadHeaderTimeout: 5 * time.Second}
	return s
}

func (s *servidor) interceptar(ctx context.Context, request any, info *grpc.UnaryServerInfo, handler grpc.UnaryHandler) (resposta any, err error) {
	if info.FullMethod != telemetryv1.TelemetryService_AnalyzeReadings_FullMethodName {
		return handler(ctx, request)
	}
	inicio := time.Now()
	correlacao := "ausente-ou-invalida"
	if mensagem, ok := request.(*telemetryv1.AnalyzeReadingsRequest); ok && correlacaoValida.MatchString(mensagem.GetCorrelationId()) {
		correlacao = mensagem.GetCorrelationId()
	}
	defer func() {
		if recover() != nil {
			resposta, err = nil, status.Error(codes.Internal, "falha interna na análise")
		}
		s.logger.Printf("metodo=%s correlacao=%s codigo=%s duracao=%s", info.FullMethod, correlacao, status.Code(err), time.Since(inicio))
	}()
	if err := ctx.Err(); err != nil {
		return nil, status.FromContextError(err).Err()
	}
	// A admissão e a mudança para indisponível compartilham o mesmo lock.
	s.mu.Lock()
	if !s.pronto {
		s.mu.Unlock()
		return nil, status.Error(codes.Unavailable, "servidor indisponível")
	}
	select {
	case s.vagas <- struct{}{}:
		s.mu.Unlock()
		defer func() { <-s.vagas }()
		return handler(ctx, request)
	default:
		s.mu.Unlock()
		return nil, status.Error(codes.ResourceExhausted, "limite de análises simultâneas atingido")
	}
}

func (s *servidor) servir(ctx context.Context, httpListener, grpcListener net.Listener) error {
	if err := ctx.Err(); err != nil {
		_ = httpListener.Close()
		_ = grpcListener.Close()
		return err
	}
	s.mu.Lock()
	s.pronto = true
	s.mu.Unlock()
	s.saude.SetServingStatus("", healthv1.HealthCheckResponse_SERVING)
	s.saude.SetServingStatus(telemetryv1.TelemetryService_ServiceDesc.ServiceName, healthv1.HealthCheckResponse_SERVING)
	erros := make(chan error, 2)
	go func() { erros <- fmt.Errorf("servidor HTTP encerrou: %w", s.http.Serve(httpListener)) }()
	go func() { erros <- fmt.Errorf("servidor gRPC encerrou: %w", s.grpc.Serve(grpcListener)) }()
	s.logger.Printf("servidor pronto http=%s grpc=%s", httpListener.Addr(), grpcListener.Addr())
	var falha error
	select {
	case <-ctx.Done():
	case falha = <-erros:
	}
	s.encerrar()
	return falha
}

func (s *servidor) encerrar() {
	ctx, cancelar := context.WithTimeout(context.Background(), s.config.PrazoEncerramento)
	defer cancelar()
	s.mu.Lock()
	s.saude.Shutdown()
	s.pronto = false
	s.mu.Unlock()
	s.logger.Print("encerramento iniciado")
	grpcEncerrado := make(chan struct{})
	httpEncerrado := make(chan error, 1)
	go func() { s.grpc.GracefulStop(); close(grpcEncerrado) }()
	go func() { httpEncerrado <- s.http.Shutdown(ctx) }()
	for grpcEncerrado != nil || httpEncerrado != nil {
		select {
		case <-grpcEncerrado:
			grpcEncerrado = nil
		case err := <-httpEncerrado:
			httpEncerrado = nil
			if err != nil {
				s.forcarEncerramento()
				return
			}
		case <-ctx.Done():
			s.forcarEncerramento()
			return
		}
	}
	s.logger.Print("encerramento concluído")
}

func (s *servidor) forcarEncerramento() {
	s.grpc.Stop()
	_ = s.http.Close()
	s.logger.Print("encerramento forçado por prazo esgotado")
}

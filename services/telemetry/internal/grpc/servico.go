package grpc

import (
	"context"
	"errors"

	telemetryv1 "github.com/AndreMattheus/FrioVax/services/telemetry/gen/friovax/telemetry/v1"
	"github.com/AndreMattheus/FrioVax/services/telemetry/internal/dominio"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

type analise func(context.Context, dominio.Solicitacao) (dominio.ResultadoAnalise, error)

type Servico struct {
	telemetryv1.UnimplementedTelemetryServiceServer
	analisar analise
}

func NovoServico() *Servico {
	return &Servico{analisar: dominio.Analisar}
}

func (s *Servico) AnalyzeReadings(ctx context.Context, request *telemetryv1.AnalyzeReadingsRequest) (*telemetryv1.AnalyzeReadingsResponse, error) {
	if err := ctx.Err(); err != nil {
		return nil, erroRPC(err)
	}
	resultado, err := s.analisar(ctx, converterSolicitacao(request))
	if err != nil {
		return nil, erroRPC(err)
	}
	if err := ctx.Err(); err != nil {
		return nil, erroRPC(err)
	}
	return converterResultado(resultado)
}

func erroRPC(err error) error {
	switch {
	case errors.Is(err, context.Canceled):
		return status.Error(codes.Canceled, "análise cancelada")
	case errors.Is(err, context.DeadlineExceeded):
		return status.Error(codes.DeadlineExceeded, "prazo da análise esgotado")
	case errors.Is(err, dominio.ErrSolicitacaoInvalida):
		return status.Error(codes.InvalidArgument, "solicitação de análise inválida")
	default:
		return status.Error(codes.Internal, "falha interna na análise")
	}
}

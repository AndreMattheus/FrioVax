package grpc

import (
	"errors"
	"slices"

	telemetryv1 "github.com/AndreMattheus/FrioVax/services/telemetry/gen/friovax/telemetry/v1"
	"github.com/AndreMattheus/FrioVax/services/telemetry/internal/dominio"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

func converterSolicitacao(request *telemetryv1.AnalyzeReadingsRequest) dominio.Solicitacao {
	solicitacao := dominio.Solicitacao{CorrelacaoID: request.GetCorrelationId(), Janelas: make([]dominio.Janela, len(request.GetWindows()))}
	for i, janela := range request.GetWindows() {
		convertida := dominio.Janela{
			CamaraID:   janela.GetChamberId(),
			Contexto:   converterLeituras(janela.GetContext()),
			Candidatas: converterLeituras(janela.GetCandidates()),
		}
		if politica := janela.GetPolicy(); politica != nil {
			convertida.Politica = &dominio.Politica{
				Versao: politica.GetVersion(), Minima: politica.MinimumTemperatureTenthsCelsius,
				Maxima: politica.MaximumTemperatureTenthsCelsius, Limiar: int(politica.GetConsecutiveUnsafeThreshold()),
			}
		}
		solicitacao.Janelas[i] = convertida
	}
	return solicitacao
}

func converterLeituras(leituras []*telemetryv1.Reading) []dominio.Leitura {
	convertidas := make([]dominio.Leitura, len(leituras))
	for i, leitura := range leituras {
		if leitura == nil {
			continue
		}
		convertida := dominio.Leitura{
			IDExterno: leitura.GetExternalId(), CamaraID: leitura.GetChamberId(),
			PoliticaVersao: leitura.GetPolicyVersion(), Temperatura: leitura.TemperatureTenthsCelsius,
		}
		// AsTime normaliza timestamps inválidos. Mantê-los sem instante permite
		// ao domínio rejeitar somente a janela, sem transformar a entrada em válida.
		if instante := leitura.GetMeasuredAt(); instante != nil && instante.CheckValid() == nil {
			utc := instante.AsTime().UTC()
			convertida.Instante = &utc
		}
		convertidas[i] = convertida
	}
	return convertidas
}

func converterResultado(resultado dominio.ResultadoAnalise) (*telemetryv1.AnalyzeReadingsResponse, error) {
	resposta := &telemetryv1.AnalyzeReadingsResponse{CorrelationId: resultado.CorrelacaoID, Results: make([]*telemetryv1.WindowResult, len(resultado.Janelas))}
	for i, janela := range resultado.Janelas {
		convertida := &telemetryv1.WindowResult{ChamberId: janela.CamaraID, PolicyVersion: janela.PoliticaVersao}
		if janela.Erro != nil {
			erro, err := converterErroJanela(janela.Erro)
			if err != nil {
				return nil, err
			}
			convertida.Outcome = &telemetryv1.WindowResult_Error{Error: erro}
		} else {
			candidatas := make([]*telemetryv1.CandidateResult, len(janela.Candidatas))
			for j, candidata := range janela.Candidatas {
				var classificacao telemetryv1.ReadingClassification
				switch candidata.Classificacao {
				case dominio.Segura:
					classificacao = telemetryv1.ReadingClassification_READING_CLASSIFICATION_SAFE
				case dominio.Insegura:
					classificacao = telemetryv1.ReadingClassification_READING_CLASSIFICATION_UNSAFE
				default:
					return nil, status.Error(codes.Internal, "classificação interna inválida")
				}
				candidatas[j] = &telemetryv1.CandidateResult{
					ExternalId: candidata.IDExterno, Classification: classificacao,
					ConsecutiveUnsafeCount: int32(candidata.Sequencia), ThresholdReached: candidata.LimiarAtingido,
					EvidenceExternalIds: slices.Clone(candidata.Evidencias),
				}
			}
			convertida.Outcome = &telemetryv1.WindowResult_Analysis{Analysis: &telemetryv1.WindowAnalysis{Candidates: candidatas}}
		}
		resposta.Results[i] = convertida
	}
	return resposta, nil
}

func converterErroJanela(err error) (*telemetryv1.WindowError, error) {
	var codigo telemetryv1.WindowErrorCode
	var descricao string
	switch {
	case errors.Is(err, dominio.ErrJanelaInvalida):
		codigo, descricao = telemetryv1.WindowErrorCode_WINDOW_ERROR_CODE_INVALID_WINDOW, "janela inválida"
	case errors.Is(err, dominio.ErrPoliticaInvalida):
		codigo, descricao = telemetryv1.WindowErrorCode_WINDOW_ERROR_CODE_INVALID_POLICY, "política inválida"
	case errors.Is(err, dominio.ErrCamaraDivergente):
		codigo, descricao = telemetryv1.WindowErrorCode_WINDOW_ERROR_CODE_CHAMBER_MISMATCH, "câmara divergente"
	case errors.Is(err, dominio.ErrVersaoDivergente):
		codigo, descricao = telemetryv1.WindowErrorCode_WINDOW_ERROR_CODE_POLICY_VERSION_MISMATCH, "versão de política divergente"
	default:
		return nil, erroRPC(err)
	}
	return &telemetryv1.WindowError{Code: codigo, Description: descricao}, nil
}

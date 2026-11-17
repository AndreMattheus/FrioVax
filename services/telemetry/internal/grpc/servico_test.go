package grpc

import (
	"context"
	"errors"
	"fmt"
	"net"
	"slices"
	"strings"
	"sync"
	"testing"
	"time"

	telemetryv1 "github.com/AndreMattheus/FrioVax/services/telemetry/gen/friovax/telemetry/v1"
	"github.com/AndreMattheus/FrioVax/services/telemetry/internal/dominio"
	grpcgo "google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/status"
	"google.golang.org/grpc/test/bufconn"
	"google.golang.org/protobuf/proto"
	"google.golang.org/protobuf/types/known/timestamppb"
)

func clienteTeste(t *testing.T, servico *Servico) telemetryv1.TelemetryServiceClient {
	t.Helper()
	listener := bufconn.Listen(1024 * 1024)
	servidor := grpcgo.NewServer()
	telemetryv1.RegisterTelemetryServiceServer(servidor, servico)
	encerrado := make(chan struct{})
	go func() {
		defer close(encerrado)
		if err := servidor.Serve(listener); err != nil {
			t.Errorf("servir gRPC: %v", err)
		}
	}()
	conexao, err := grpcgo.NewClient("passthrough:///bufconn",
		grpcgo.WithTransportCredentials(insecure.NewCredentials()),
		grpcgo.WithContextDialer(func(ctx context.Context, _ string) (net.Conn, error) { return listener.DialContext(ctx) }))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		_ = conexao.Close()
		servidor.Stop()
		_ = listener.Close()
		<-encerrado
	})
	return telemetryv1.NewTelemetryServiceClient(conexao)
}

func ponteiro[T any](valor T) *T { return &valor }

func janelaTeste(camara int64, temperaturas ...int32) *telemetryv1.ReadingWindow {
	janela := &telemetryv1.ReadingWindow{ChamberId: camara, Policy: &telemetryv1.ThermalPolicy{
		Version: 1, MinimumTemperatureTenthsCelsius: ponteiro(int32(20)),
		MaximumTemperatureTenthsCelsius: ponteiro(int32(80)), ConsecutiveUnsafeThreshold: 3,
	}}
	for i, temperatura := range temperaturas {
		janela.Candidates = append(janela.Candidates, &telemetryv1.Reading{
			ExternalId: fmt.Sprintf("r%d", i+1), ChamberId: camara, PolicyVersion: 1,
			TemperatureTenthsCelsius: ponteiro(temperatura),
			MeasuredAt:               timestamppb.New(time.Date(2026, 11, 17, 12, i, 0, 123456000, time.UTC)),
		})
	}
	return janela
}

func requisicao(janelas ...*telemetryv1.ReadingWindow) *telemetryv1.AnalyzeReadingsRequest {
	return &telemetryv1.AnalyzeReadingsRequest{CorrelationId: "teste-72", Windows: janelas}
}

func chamar(t *testing.T, cliente telemetryv1.TelemetryServiceClient, request *telemetryv1.AnalyzeReadingsRequest) (*telemetryv1.AnalyzeReadingsResponse, error) {
	t.Helper()
	ctx, cancelar := context.WithTimeout(t.Context(), 5*time.Second)
	defer cancelar()
	return cliente.AnalyzeReadings(ctx, request)
}

func TestResultadosPreservamOrdemContextoESinalIntermediario(t *testing.T) {
	cliente := clienteTeste(t, NovoServico())
	comContexto := janelaTeste(2, 90, 90, 100)
	comContexto.Context, comContexto.Candidates = comContexto.Candidates[:2], comContexto.Candidates[2:]
	request := requisicao(janelaTeste(9, 90, 90, 90, 40), comContexto, janelaTeste(1, 20, 80))
	antes := proto.Clone(request)
	resposta, err := chamar(t, cliente, request)
	if err != nil {
		t.Fatal(err)
	}
	if resposta.CorrelationId != request.CorrelationId || len(resposta.Results) != 3 || !proto.Equal(request, antes) {
		t.Fatalf("correlação, quantidade ou entrada alterada: %v", resposta)
	}
	for i, sequencias := range [][]int32{{1, 2, 3, 0}, {3}, {0, 0}} {
		resultado := resposta.Results[i]
		if resultado.ChamberId != request.Windows[i].ChamberId || resultado.PolicyVersion != 1 || resultado.GetError() != nil || resultado.GetAnalysis() == nil {
			t.Fatalf("janela %d: %v", i, resultado)
		}
		candidatas := resultado.GetAnalysis().Candidates
		if len(candidatas) != len(sequencias) {
			t.Fatalf("janela %d: %v", i, candidatas)
		}
		for j, sequencia := range sequencias {
			r := candidatas[j]
			classificacao := telemetryv1.ReadingClassification_READING_CLASSIFICATION_UNSAFE
			if sequencia == 0 {
				classificacao = telemetryv1.ReadingClassification_READING_CLASSIFICATION_SAFE
			}
			if r.ExternalId != request.Windows[i].Candidates[j].ExternalId || r.ConsecutiveUnsafeCount != sequencia || r.ThresholdReached != (sequencia == 3) || r.Classification != classificacao {
				t.Fatalf("candidata %d/%d: %v", i, j, r)
			}
		}
	}
	if !slices.Equal(resposta.Results[1].GetAnalysis().Candidates[0].EvidenceExternalIds, []string{"r1", "r2", "r3"}) || len(resposta.Results[0].GetAnalysis().Candidates[3].EvidenceExternalIds) != 0 {
		t.Fatal("evidências de contexto ou de leitura segura incorretas", resposta)
	}
}

func TestErroDeJanelaNaoCancelaOutraCamara(t *testing.T) {
	cliente := clienteTeste(t, NovoServico())
	for _, caso := range []struct {
		nome    string
		alterar func(*telemetryv1.ReadingWindow)
		codigo  telemetryv1.WindowErrorCode
	}{
		{"temperatura ausente", func(j *telemetryv1.ReadingWindow) { j.Candidates[0].TemperatureTenthsCelsius = nil }, telemetryv1.WindowErrorCode_WINDOW_ERROR_CODE_INVALID_WINDOW},
		{"instante ausente", func(j *telemetryv1.ReadingWindow) { j.Candidates[0].MeasuredAt = nil }, telemetryv1.WindowErrorCode_WINDOW_ERROR_CODE_INVALID_WINDOW},
		{"nanos negativos", func(j *telemetryv1.ReadingWindow) { j.Candidates[0].MeasuredAt.Nanos = -1000 }, telemetryv1.WindowErrorCode_WINDOW_ERROR_CODE_INVALID_WINDOW},
		{"nanos normalizáveis", func(j *telemetryv1.ReadingWindow) { j.Candidates[0].MeasuredAt.Nanos = 1000000000 }, telemetryv1.WindowErrorCode_WINDOW_ERROR_CODE_INVALID_WINDOW},
		{"fora do intervalo", func(j *telemetryv1.ReadingWindow) { j.Candidates[0].MeasuredAt.Seconds = 253402300800 }, telemetryv1.WindowErrorCode_WINDOW_ERROR_CODE_INVALID_WINDOW},
		{"precisão excessiva", func(j *telemetryv1.ReadingWindow) { j.Candidates[0].MeasuredAt.Nanos = 1 }, telemetryv1.WindowErrorCode_WINDOW_ERROR_CODE_INVALID_WINDOW},
		{"contexto inválido", func(j *telemetryv1.ReadingWindow) {
			j.Context = []*telemetryv1.Reading{proto.Clone(j.Candidates[0]).(*telemetryv1.Reading)}
			j.Context[0].MeasuredAt.Nanos = -1
		}, telemetryv1.WindowErrorCode_WINDOW_ERROR_CODE_INVALID_WINDOW},
		{"sem candidatas", func(j *telemetryv1.ReadingWindow) { j.Candidates = nil }, telemetryv1.WindowErrorCode_WINDOW_ERROR_CODE_INVALID_WINDOW},
		{"sem política", func(j *telemetryv1.ReadingWindow) { j.Policy = nil }, telemetryv1.WindowErrorCode_WINDOW_ERROR_CODE_INVALID_POLICY},
		{"mínimo ausente", func(j *telemetryv1.ReadingWindow) { j.Policy.MinimumTemperatureTenthsCelsius = nil }, telemetryv1.WindowErrorCode_WINDOW_ERROR_CODE_INVALID_POLICY},
		{"câmara divergente", func(j *telemetryv1.ReadingWindow) { j.Candidates[0].ChamberId = 3 }, telemetryv1.WindowErrorCode_WINDOW_ERROR_CODE_CHAMBER_MISMATCH},
		{"versão divergente", func(j *telemetryv1.ReadingWindow) { j.Candidates[0].PolicyVersion = 2 }, telemetryv1.WindowErrorCode_WINDOW_ERROR_CODE_POLICY_VERSION_MISMATCH},
	} {
		t.Run(caso.nome, func(t *testing.T) {
			invalida := janelaTeste(1, 90)
			caso.alterar(invalida)
			resposta, err := chamar(t, cliente, requisicao(invalida, janelaTeste(2, 40)))
			if err != nil {
				t.Fatal(err)
			}
			if len(resposta.Results) != 2 || resposta.Results[0].GetError().GetCode() != caso.codigo || resposta.Results[0].GetAnalysis() != nil || resposta.Results[1].GetAnalysis() == nil || resposta.Results[1].GetError() != nil {
				t.Fatalf("isolamento de erros: %v", resposta)
			}
		})
	}
}

func TestZeroEPrecisaoPreservados(t *testing.T) {
	servico := NovoServico()
	servico.analisar = func(ctx context.Context, s dominio.Solicitacao) (dominio.ResultadoAnalise, error) {
		leitura := s.Janelas[0].Candidatas[0]
		if leitura.Instante.Location() != time.UTC || leitura.Instante.Nanosecond() != 123456000 || leitura.Temperatura == nil || *leitura.Temperatura != 0 || s.Janelas[0].Politica.Minima == nil {
			t.Error("conversão perdeu zero, precisão ou UTC", leitura)
		}
		return dominio.Analisar(ctx, s)
	}
	janela := janelaTeste(1, 0)
	janela.Policy.MinimumTemperatureTenthsCelsius = ponteiro(int32(0))
	resposta, err := chamar(t, clienteTeste(t, servico), requisicao(janela))
	if err != nil || resposta.GetResults()[0].GetAnalysis().GetCandidates()[0].GetClassification() != telemetryv1.ReadingClassification_READING_CLASSIFICATION_SAFE {
		t.Fatalf("zero válido: resposta=%v erro=%v", resposta, err)
	}
}

func TestSolicitacoesInvalidas(t *testing.T) {
	cliente := clienteTeste(t, NovoServico())
	for _, request := range []*telemetryv1.AnalyzeReadingsRequest{
		{}, requisicao(), requisicao(nil), requisicao(janelaTeste(0, 90)),
		requisicao(janelaTeste(1, 90), janelaTeste(1, 40)),
		requisicao(janelaTeste(1)), requisicao(janelaTeste(1, make([]int32, 51)...)),
		{CorrelationId: "inválida\n", Windows: []*telemetryv1.ReadingWindow{janelaTeste(1, 90)}},
	} {
		resposta, err := chamar(t, cliente, request)
		if resposta != nil || status.Code(err) != codes.InvalidArgument {
			t.Fatalf("resposta=%v erro=%v", resposta, err)
		}
	}
	if _, err := NovoServico().AnalyzeReadings(t.Context(), nil); status.Code(err) != codes.InvalidArgument {
		t.Fatalf("requisição nil: %v", err)
	}
}

func TestErrosEncapsuladosNaoExpoemDetalhes(t *testing.T) {
	for _, caso := range []struct {
		erro   error
		codigo codes.Code
	}{
		{dominio.ErrSolicitacaoInvalida, codes.InvalidArgument}, {context.Canceled, codes.Canceled},
		{context.DeadlineExceeded, codes.DeadlineExceeded}, {errors.New("segredo"), codes.Internal},
	} {
		t.Run(caso.codigo.String(), func(t *testing.T) {
			servico := NovoServico()
			servico.analisar = func(context.Context, dominio.Solicitacao) (dominio.ResultadoAnalise, error) {
				return dominio.ResultadoAnalise{}, fmt.Errorf("detalhe-sensível: %w", caso.erro)
			}
			resposta, err := chamar(t, clienteTeste(t, servico), requisicao(janelaTeste(1, 40)))
			if resposta != nil || status.Code(err) != caso.codigo || strings.Contains(err.Error(), "detalhe-sensível") || strings.Contains(err.Error(), "segredo") {
				t.Fatalf("resposta=%v erro=%v", resposta, err)
			}
		})
	}
}

func TestResultadoInternoInvalidoFalhaSemRespostaParcial(t *testing.T) {
	for _, caso := range []struct {
		nome   string
		janela dominio.ResultadoJanela
	}{
		{"erro desconhecido", dominio.ResultadoJanela{CamaraID: 2, Erro: errors.New("detalhe-sensível")}},
		{"classificação desconhecida", dominio.ResultadoJanela{CamaraID: 2, Candidatas: []dominio.ResultadoCandidata{{Classificacao: "DESCONHECIDA"}}}},
	} {
		t.Run(caso.nome, func(t *testing.T) {
			servico := NovoServico()
			servico.analisar = func(ctx context.Context, s dominio.Solicitacao) (dominio.ResultadoAnalise, error) {
				resultado, err := dominio.Analisar(ctx, s)
				resultado.Janelas[1] = caso.janela
				return resultado, err
			}
			resposta, err := chamar(t, clienteTeste(t, servico), requisicao(janelaTeste(1, 40), janelaTeste(2, 40)))
			if resposta != nil || status.Code(err) != codes.Internal || strings.Contains(err.Error(), "detalhe-sensível") {
				t.Fatalf("resposta=%v erro=%v", resposta, err)
			}
		})
	}
}

func TestMensagensAusentesEPoliticaInvalida(t *testing.T) {
	cliente := clienteTeste(t, NovoServico())
	for _, caso := range []struct {
		nome    string
		alterar func(*telemetryv1.ReadingWindow)
		codigo  telemetryv1.WindowErrorCode
		versao  int64
	}{
		{"leitura ausente", func(j *telemetryv1.ReadingWindow) { j.Candidates[0] = nil }, telemetryv1.WindowErrorCode_WINDOW_ERROR_CODE_CHAMBER_MISMATCH, 1},
		{"versão negativa", func(j *telemetryv1.ReadingWindow) { j.Policy.Version = -1 }, telemetryv1.WindowErrorCode_WINDOW_ERROR_CODE_INVALID_POLICY, 0},
		{"política ausente", func(j *telemetryv1.ReadingWindow) { j.Policy = nil }, telemetryv1.WindowErrorCode_WINDOW_ERROR_CODE_INVALID_POLICY, 0},
	} {
		t.Run(caso.nome, func(t *testing.T) {
			janela := janelaTeste(1, 90)
			caso.alterar(janela)
			resposta, err := chamar(t, cliente, requisicao(janela))
			if err != nil {
				t.Fatal(err)
			}
			r := resposta.Results[0]
			if r.GetError().GetCode() != caso.codigo || r.PolicyVersion != caso.versao || r.GetAnalysis() != nil {
				t.Fatal(r)
			}
		})
	}
}

func TestCancelamentoEPrazoDuranteAnalise(t *testing.T) {
	for _, prazo := range []bool{false, true} {
		t.Run(fmt.Sprintf("deadline=%t", prazo), func(t *testing.T) {
			iniciou := make(chan struct{})
			observado := make(chan error, 1)
			servico := NovoServico()
			servico.analisar = func(ctx context.Context, _ dominio.Solicitacao) (dominio.ResultadoAnalise, error) {
				close(iniciou)
				<-ctx.Done()
				observado <- ctx.Err()
				return dominio.ResultadoAnalise{}, fmt.Errorf("interrompida: %w", ctx.Err())
			}
			cliente := clienteTeste(t, servico)
			ctx, cancelar := context.WithTimeout(t.Context(), time.Second)
			defer cancelar()
			concluiu := make(chan error, 1)
			go func() { _, err := cliente.AnalyzeReadings(ctx, requisicao(janelaTeste(1, 90))); concluiu <- err }()
			select {
			case <-iniciou:
			case <-time.After(5 * time.Second):
				t.Fatal("análise não iniciou")
			}
			codigo, erroContexto := codes.DeadlineExceeded, context.DeadlineExceeded
			if !prazo {
				codigo, erroContexto = codes.Canceled, context.Canceled
				cancelar()
			}
			select {
			case err := <-concluiu:
				if status.Code(err) != codigo {
					t.Fatalf("cliente: %v", err)
				}
			case <-time.After(5 * time.Second):
				t.Fatal("cliente não terminou")
			}
			select {
			case err := <-observado:
				// No vencimento, o cliente também pode cancelar o stream antes do
				// temporizador remoto; ambos interrompem o trabalho no servidor.
				if !errors.Is(err, erroContexto) && !(prazo && errors.Is(err, context.Canceled)) {
					t.Fatalf("servidor: %v", err)
				}
			case <-time.After(5 * time.Second):
				t.Fatal("contexto não propagado")
			}
		})
	}
}

func TestChamadasConcorrentesIsoladas(t *testing.T) {
	cliente := clienteTeste(t, NovoServico())
	var grupo sync.WaitGroup
	inicio := make(chan struct{})
	for i := range 20 {
		grupo.Go(func() {
			<-inicio
			request := requisicao(janelaTeste(int64(i+1), 90, 90, 90, 40))
			request.CorrelationId = fmt.Sprintf("chamada-%d", i)
			resposta, err := chamar(t, cliente, request)
			if err != nil {
				t.Error(err)
				return
			}
			if resposta.CorrelationId != request.CorrelationId || len(resposta.Results) != 1 || resposta.Results[0].ChamberId != int64(i+1) || len(resposta.Results[0].GetAnalysis().GetCandidates()) != 4 {
				t.Errorf("resultados misturados: %v", resposta)
			}
		})
	}
	close(inicio)
	grupo.Wait()
}

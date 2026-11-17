package dominio

import (
	"context"
	"errors"
	"reflect"
	"strings"
	"testing"
	"testing/synctest"
	"time"
)

type analiseJanelaFunc func(context.Context, Janela) ([]ResultadoCandidata, error)

func (f analiseJanelaFunc) Analisar(ctx context.Context, janela Janela) ([]ResultadoCandidata, error) {
	return f(ctx, janela)
}

func solicitacaoTeste() Solicitacao {
	a, b := janelaTeste(90, 90, 90), janelaTeste(40, 90)
	b.CamaraID = 2
	for i := range b.Candidatas {
		b.Candidatas[i].CamaraID = b.CamaraID
	}
	return Solicitacao{CorrelacaoID: "lote:1_A.2-3", Janelas: []Janela{a, b}}
}

func TestAnaliseConcorrentePreservaOrdemEIsolaCamaras(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		liberarA := make(chan struct{})
		aIniciada := make(chan struct{})
		bCalculada := make(chan struct{})
		analisador := analiseJanelaFunc(func(ctx context.Context, j Janela) ([]ResultadoCandidata, error) {
			if j.CamaraID == 1 {
				close(aIniciada)
				<-liberarA
				return (AnalisadorJanela{}).Analisar(ctx, j)
			}
			<-aIniciada
			candidatas, err := (AnalisadorJanela{}).Analisar(ctx, j)
			close(bCalculada)
			return candidatas, err
		})
		go func() {
			<-bCalculada
			// A só avança depois que B terminou o cálculo.
			close(liberarA)
		}()

		s := solicitacaoTeste()
		r, err := analisar(t.Context(), s, analisador)
		if err != nil || !reflect.DeepEqual(r, resultadoEsperado()) {
			t.Fatalf("resultado=%+v erro=%v", r, err)
		}
	})
}

func resultadoEsperado() ResultadoAnalise {
	return ResultadoAnalise{CorrelacaoID: "lote:1_A.2-3", Janelas: []ResultadoJanela{
		{CamaraID: 1, PoliticaVersao: 1, Candidatas: []ResultadoCandidata{
			{IDExterno: "r1", Classificacao: Insegura, Sequencia: 1, Evidencias: []string{"r1"}},
			{IDExterno: "r2", Classificacao: Insegura, Sequencia: 2, Evidencias: []string{"r1", "r2"}},
			{IDExterno: "r3", Classificacao: Insegura, Sequencia: 3, LimiarAtingido: true, Evidencias: []string{"r1", "r2", "r3"}},
		}},
		{CamaraID: 2, PoliticaVersao: 1, Candidatas: []ResultadoCandidata{
			{IDExterno: "r1", Classificacao: Segura, Evidencias: []string{}},
			{IDExterno: "r2", Classificacao: Insegura, Sequencia: 1, Evidencias: []string{"r2"}},
		}},
	}}
}

func TestAnalisesSimultaneasSaoDeterministicas(t *testing.T) {
	s := solicitacaoTeste()
	for i := range 8 {
		t.Run(string(rune('A'+i)), func(t *testing.T) {
			t.Parallel()
			r, err := Analisar(t.Context(), s)
			if err != nil || !reflect.DeepEqual(r, resultadoEsperado()) {
				t.Fatalf("resultado=%+v erro=%v", r, err)
			}
		})
	}
}

func TestRejeicaoDeJanelaPreservaOutraCamara(t *testing.T) {
	for _, caso := range []struct {
		nome    string
		alterar func(*Janela)
		erro    error
	}{
		{"política ausente", func(j *Janela) { j.Politica = nil }, ErrPoliticaInvalida},
		{"janela vazia", func(j *Janela) { j.Candidatas = nil }, ErrJanelaInvalida},
		{"câmara divergente", func(j *Janela) { j.Candidatas[0].CamaraID++ }, ErrCamaraDivergente},
		{"versão divergente", func(j *Janela) { j.Candidatas[0].PoliticaVersao++ }, ErrVersaoDivergente},
	} {
		t.Run(caso.nome, func(t *testing.T) {
			s := solicitacaoTeste()
			caso.alterar(&s.Janelas[0])
			r, err := Analisar(t.Context(), s)
			if err != nil || len(r.Janelas) != 2 {
				t.Fatalf("resultado=%+v erro=%v", r, err)
			}
			a, b := r.Janelas[0], r.Janelas[1]
			if a.CamaraID != 1 || !errors.Is(a.Erro, caso.erro) || a.Candidatas != nil ||
				!reflect.DeepEqual(b, resultadoEsperado().Janelas[1]) {
				t.Fatalf("resultado=%+v", r)
			}
		})
	}
}

func TestEstruturaGlobalInvalidaNaoIniciaTrabalho(t *testing.T) {
	for _, caso := range []struct {
		nome    string
		alterar func(*Solicitacao)
	}{
		{"sem janelas", func(s *Solicitacao) { s.Janelas = nil }},
		{"sem candidatas", func(s *Solicitacao) {
			for i := range s.Janelas {
				s.Janelas[i].Candidatas = nil
			}
		}},
		{"correlação vazia", func(s *Solicitacao) { s.CorrelacaoID = "" }},
		{"correlação com caractere inválido", func(s *Solicitacao) { s.CorrelacaoID = "lote\n" }},
		{"correlação longa", func(s *Solicitacao) { s.CorrelacaoID = strings.Repeat("a", 129) }},
		{"câmara repetida", func(s *Solicitacao) { s.Janelas[1].CamaraID = 1 }},
		{"câmara sem identificação", func(s *Solicitacao) { s.Janelas[1].CamaraID = 0 }},
		{"excesso global", func(s *Solicitacao) { s.Janelas[0].Candidatas = make([]Leitura, 49) }},
	} {
		t.Run(caso.nome, func(t *testing.T) {
			s := solicitacaoTeste()
			caso.alterar(&s)
			analisador := analiseJanelaFunc(func(context.Context, Janela) ([]ResultadoCandidata, error) {
				t.Error("estrutura inválida iniciou um trabalhador")
				return nil, nil
			})
			r, err := analisar(t.Context(), s, analisador)
			exigirFalhaGlobal(t, r, err, ErrSolicitacaoInvalida)
		})
	}
}

func TestCancelamentoEPrazoEncerramTrabalhadores(t *testing.T) {
	for _, caso := range []struct {
		nome  string
		prazo bool
		erro  error
	}{
		{"cancelamento", false, context.Canceled},
		{"prazo", true, context.DeadlineExceeded},
	} {
		t.Run(caso.nome, func(t *testing.T) {
			synctest.Test(t, func(t *testing.T) {
				var ctx context.Context
				var cancelar context.CancelFunc
				if caso.prazo {
					ctx, cancelar = context.WithTimeout(t.Context(), time.Second)
				} else {
					ctx, cancelar = context.WithCancel(t.Context())
				}
				defer cancelar()

				iniciados := make(chan struct{}, 2)
				finalizados := make(chan struct{}, 2)
				analisador := analiseJanelaFunc(func(ctx context.Context, _ Janela) ([]ResultadoCandidata, error) {
					iniciados <- struct{}{}
					<-ctx.Done()
					finalizados <- struct{}{}
					return nil, ctx.Err()
				})
				if !caso.prazo {
					go func() {
						<-iniciados
						<-iniciados
						cancelar()
					}()
				}
				r, err := analisar(ctx, solicitacaoTeste(), analisador)
				exigirFalhaGlobal(t, r, err, caso.erro)
				if len(finalizados) != 2 {
					t.Fatalf("retorno com %d trabalhadores finalizados, esperado 2", len(finalizados))
				}
			})
		})
	}
}

func TestContextoEncerradoAntesDaAnalise(t *testing.T) {
	cancelado, cancelar := context.WithCancel(t.Context())
	cancelar()
	expirado, encerrar := context.WithDeadline(t.Context(), time.Unix(0, 0))
	defer encerrar()
	for _, caso := range []struct {
		ctx  context.Context
		erro error
	}{{cancelado, context.Canceled}, {expirado, context.DeadlineExceeded}} {
		r, err := analisar(caso.ctx, solicitacaoTeste(), analiseJanelaFunc(func(context.Context, Janela) ([]ResultadoCandidata, error) {
			t.Error("contexto encerrado iniciou um trabalhador")
			return nil, nil
		}))
		exigirFalhaGlobal(t, r, err, caso.erro)
	}
}

func TestErroInternoCancelaOutraCamara(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		falha := errors.New("falha interna de teste")
		bIniciada, bFinalizada := make(chan struct{}), make(chan struct{})
		analisador := analiseJanelaFunc(func(ctx context.Context, j Janela) ([]ResultadoCandidata, error) {
			if j.CamaraID == 1 {
				<-bIniciada
				return nil, falha
			}
			close(bIniciada)
			<-ctx.Done()
			close(bFinalizada)
			return nil, ctx.Err()
		})
		r, err := analisar(t.Context(), solicitacaoTeste(), analisador)
		exigirFalhaGlobal(t, r, err, falha)
		select {
		case <-bFinalizada:
		default:
			t.Fatal("retorno antes de B finalizar")
		}
	})
}

func exigirFalhaGlobal(t *testing.T, r ResultadoAnalise, err, esperado error) {
	t.Helper()
	if r.Janelas != nil || r.CorrelacaoID != "" || !errors.Is(err, esperado) {
		t.Fatalf("resultado=%+v erro=%v, esperada falha global %v", r, err, esperado)
	}
}

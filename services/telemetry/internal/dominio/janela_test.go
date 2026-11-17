package dominio

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"reflect"
	"slices"
	"testing"
	"time"
)

func ponteiro[T any](valor T) *T { return &valor }

func janelaTeste(temperaturas ...int32) Janela {
	j := Janela{CamaraID: 1, Politica: &Politica{Versao: 1, Minima: ponteiro(int32(20)), Maxima: ponteiro(int32(80)), Limiar: 3}}
	for i, temperatura := range temperaturas {
		j.Candidatas = append(j.Candidatas, Leitura{
			IDExterno: fmt.Sprintf("r%d", i+1), CamaraID: 1, PoliticaVersao: 1,
			Temperatura: ponteiro(temperatura), Instante: ponteiro(time.Date(2026, 11, 17, 12, i, 0, 0, time.UTC)),
		})
	}
	return j
}

func TestAnalisarJanela(t *testing.T) {
	for _, caso := range []struct {
		nome             string
		temperaturas     []int32
		limiar, contexto int
		sequencias       []int
		evidencias       [][]string
	}{
		{"limites inclusivos", []int32{20, 80, 19, 81}, 3, 0, []int{0, 0, 1, 2}, [][]string{nil, nil, {"r3"}, {"r3", "r4"}}},
		{"sinal intermediário", []int32{90, 90, 90, 40}, 3, 0, []int{1, 2, 3, 0}, [][]string{{"r1"}, {"r1", "r2"}, {"r1", "r2", "r3"}, nil}},
		{"segura intercalada", []int32{90, 40, 90}, 3, 0, []int{1, 0, 1}, [][]string{{"r1"}, nil, {"r3"}}},
		{"saturação e direções", []int32{90, 10, 100, 110}, 3, 0, []int{1, 2, 3, 3}, [][]string{{"r1"}, {"r1", "r2"}, {"r1", "r2", "r3"}, {"r2", "r3", "r4"}}},
		{"contexto persistido", []int32{90, 90, 100, 40}, 3, 2, []int{3, 0}, [][]string{{"r1", "r2", "r3"}, nil}},
		{"contexto com segura", []int32{90, 40, 90}, 3, 2, []int{1}, [][]string{{"r3"}}},
		{"limiar um", []int32{90, 100, 40}, 1, 0, []int{1, 1, 0}, [][]string{{"r1"}, {"r2"}, nil}},
		{"zero e negativos", []int32{0, -15, -9999, 9999}, 3, 0, []int{1, 2, 3, 3}, [][]string{{"r1"}, {"r1", "r2"}, {"r1", "r2", "r3"}, {"r2", "r3", "r4"}}},
	} {
		t.Run(caso.nome, func(t *testing.T) {
			j := janelaTeste(caso.temperaturas...)
			j.Politica.Limiar = caso.limiar
			j.Contexto, j.Candidatas = j.Candidatas[:caso.contexto], j.Candidatas[caso.contexto:]
			antes, _ := json.Marshal(j)
			resultados, err := (AnalisadorJanela{}).Analisar(context.Background(), j)
			if err != nil {
				t.Fatal(err)
			}
			if len(resultados) != len(caso.sequencias) {
				t.Fatalf("resultados: %v", resultados)
			}
			for i, r := range resultados {
				classificacao := Segura
				if caso.sequencias[i] > 0 {
					classificacao = Insegura
				}
				if r.IDExterno != j.Candidatas[i].IDExterno || r.Sequencia != caso.sequencias[i] || r.Classificacao != classificacao || r.LimiarAtingido != (caso.sequencias[i] == caso.limiar) || !slices.Equal(r.Evidencias, caso.evidencias[i]) {
					t.Fatalf("candidata %d: %+v; sequência %d, evidências %v", i, r, caso.sequencias[i], caso.evidencias[i])
				}
			}
			depois, _ := json.Marshal(j)
			if string(antes) != string(depois) {
				t.Fatal("entrada modificada")
			}
			repetidos, err := (AnalisadorJanela{}).Analisar(context.Background(), j)
			if err != nil || !reflect.DeepEqual(resultados, repetidos) {
				t.Fatal("análise não determinística", err)
			}
		})
	}
}

func TestJanelaInvalidaNaoRetornaResultadosParciais(t *testing.T) {
	for _, caso := range []struct {
		nome    string
		alterar func(*Janela)
		erro    error
	}{
		{"câmara zero", func(j *Janela) { j.CamaraID = 0 }, ErrJanelaInvalida},
		{"política ausente", func(j *Janela) { j.Politica = nil }, ErrPoliticaInvalida},
		{"versão inválida", func(j *Janela) { j.Politica.Versao = 0 }, ErrPoliticaInvalida},
		{"mínimo ausente", func(j *Janela) { j.Politica.Minima = nil }, ErrPoliticaInvalida},
		{"máximo ausente", func(j *Janela) { j.Politica.Maxima = nil }, ErrPoliticaInvalida},
		{"faixa igual", func(j *Janela) { j.Politica.Minima = ponteiro(int32(80)) }, ErrPoliticaInvalida},
		{"faixa invertida", func(j *Janela) { j.Politica.Minima = ponteiro(int32(90)) }, ErrPoliticaInvalida},
		{"mínimo fora", func(j *Janela) { j.Politica.Minima = ponteiro(int32(-10000)) }, ErrPoliticaInvalida},
		{"máximo fora", func(j *Janela) { j.Politica.Maxima = ponteiro(int32(10000)) }, ErrPoliticaInvalida},
		{"limiar zero", func(j *Janela) { j.Politica.Limiar = 0 }, ErrPoliticaInvalida},
		{"limiar excessivo", func(j *Janela) { j.Politica.Limiar = 101 }, ErrPoliticaInvalida},
		{"sem candidatas", func(j *Janela) { j.Candidatas = nil }, ErrJanelaInvalida},
		{"candidatas excessivas", func(j *Janela) { j.Candidatas = make([]Leitura, 51) }, ErrJanelaInvalida},
		{"contexto excessivo", func(j *Janela) { j.Contexto = make([]Leitura, 3) }, ErrJanelaInvalida},
		{"contexto com N um", func(j *Janela) { j.Politica.Limiar = 1; j.Contexto = j.Candidatas[:1]; j.Candidatas = j.Candidatas[1:] }, ErrJanelaInvalida},
		{"mistura câmaras", func(j *Janela) { j.Candidatas[1].CamaraID = 2 }, ErrCamaraDivergente},
		{"mistura versões", func(j *Janela) { j.Candidatas[1].PoliticaVersao = 2 }, ErrVersaoDivergente},
		{"contexto antigo", func(j *Janela) {
			j.Contexto = j.Candidatas[:1]
			j.Candidatas = j.Candidatas[1:]
			j.Contexto[0].PoliticaVersao = 2
		}, ErrVersaoDivergente},
		{"id vazio", func(j *Janela) { j.Candidatas[1].IDExterno = " \t" }, ErrJanelaInvalida},
		{"id repetido", func(j *Janela) { j.Candidatas[1].IDExterno = "r1" }, ErrJanelaInvalida},
		{"id repetido no contexto", func(j *Janela) {
			j.Contexto = j.Candidatas[:1]
			j.Candidatas = j.Candidatas[1:]
			j.Candidatas[0].IDExterno = "r1"
		}, ErrJanelaInvalida},
		{"temperatura ausente", func(j *Janela) { j.Candidatas[1].Temperatura = nil }, ErrJanelaInvalida},
		{"temperatura alta", func(j *Janela) { j.Candidatas[1].Temperatura = ponteiro(int32(10000)) }, ErrJanelaInvalida},
		{"temperatura baixa", func(j *Janela) { j.Candidatas[1].Temperatura = ponteiro(int32(-10000)) }, ErrJanelaInvalida},
		{"instante ausente", func(j *Janela) { j.Candidatas[1].Instante = nil }, ErrJanelaInvalida},
		{"instante igual", func(j *Janela) { j.Candidatas[1].Instante = j.Candidatas[0].Instante }, ErrJanelaInvalida},
		{"instante anterior", func(j *Janela) { j.Candidatas[1].Instante = ponteiro(j.Candidatas[0].Instante.Add(-time.Second)) }, ErrJanelaInvalida},
		{"contexto posterior", func(j *Janela) { j.Contexto = j.Candidatas[1:]; j.Candidatas = j.Candidatas[:1] }, ErrJanelaInvalida},
		{"precisão excessiva", func(j *Janela) { j.Candidatas[1].Instante = ponteiro(j.Candidatas[1].Instante.Add(time.Nanosecond)) }, ErrJanelaInvalida},
		{"ano fora do contrato", func(j *Janela) { j.Candidatas[1].Instante = ponteiro(time.Date(10000, 1, 1, 0, 0, 0, 0, time.UTC)) }, ErrJanelaInvalida},
	} {
		t.Run(caso.nome, func(t *testing.T) {
			j := janelaTeste(90, 90)
			caso.alterar(&j)
			resultados, err := (AnalisadorJanela{}).Analisar(context.Background(), j)
			if !errors.Is(err, caso.erro) || resultados != nil {
				t.Fatalf("resultados=%v erro=%v; esperado %v", resultados, err, caso.erro)
			}
			if err == caso.erro {
				t.Fatal("erro sem contexto da câmara")
			}
		})
	}
}

func TestNovaPoliticaEValoresValidos(t *testing.T) {
	j := janelaTeste(-15, 0, 15, 16)
	j.Politica = &Politica{Versao: 2, Minima: ponteiro(int32(-15)), Maxima: ponteiro(int32(15)), Limiar: 100}
	for i := range j.Candidatas {
		j.Candidatas[i].PoliticaVersao = 2
	}
	j.Candidatas[3].IDExterno = " r4 "
	j.Candidatas[3].Instante = ponteiro(j.Candidatas[3].Instante.In(time.FixedZone("offset", -3*3600)).Add(time.Microsecond))
	r, err := (AnalisadorJanela{}).Analisar(context.Background(), j)
	if err != nil {
		t.Fatal(err)
	}
	for _, resultado := range r[:3] {
		if resultado.Classificacao != Segura {
			t.Fatal(resultado)
		}
	}
	if r[3].Sequencia != 1 || r[3].IDExterno != " r4 " {
		t.Fatal(r[3])
	}
}

func TestEvidenciasIndependentes(t *testing.T) {
	j := janelaTeste(90, 90, 90, 90)
	r, err := (AnalisadorJanela{}).Analisar(context.Background(), j)
	if err != nil {
		t.Fatal(err)
	}
	r[0].Evidencias[0] = "alterada"
	if r[1].Evidencias[0] != "r1" || j.Candidatas[0].IDExterno != "r1" {
		t.Fatal("evidências compartilham memória")
	}
}

func TestLimitesMaximosDeContextoECandidatas(t *testing.T) {
	temperaturas := make([]int32, 149)
	for i := range temperaturas {
		temperaturas[i] = 90
	}
	j := janelaTeste(temperaturas...)
	j.Politica.Limiar = 100
	j.Contexto, j.Candidatas = j.Candidatas[:99], j.Candidatas[99:]
	r, err := (AnalisadorJanela{}).Analisar(context.Background(), j)
	if err != nil {
		t.Fatal(err)
	}
	if len(r) != 50 {
		t.Fatalf("esperadas 50 candidatas, recebido %d", len(r))
	}
	for i, resultado := range r {
		if resultado.Sequencia != 100 || !resultado.LimiarAtingido || len(resultado.Evidencias) != 100 || resultado.Evidencias[0] != fmt.Sprintf("r%d", i+1) || resultado.Evidencias[99] != resultado.IDExterno {
			t.Fatalf("candidata %d: %+v", i, resultado)
		}
	}
}

func TestIntervaloLongoNaoZeraSequencia(t *testing.T) {
	j := janelaTeste(90, 90, 90)
	j.Candidatas[2].Instante = ponteiro(j.Candidatas[2].Instante.AddDate(0, 0, 30))
	r, err := (AnalisadorJanela{}).Analisar(context.Background(), j)
	if err != nil || len(r) != 3 {
		t.Fatalf("resultado=%v erro=%v", r, err)
	}
	if !r[2].LimiarAtingido {
		t.Fatal("intervalo longo interrompeu a sequência", r[2])
	}
}

func TestJanelaRespeitaContexto(t *testing.T) {
	cancelado, cancelar := context.WithCancel(context.Background())
	cancelar()
	expirado, encerrar := context.WithDeadline(context.Background(), time.Unix(0, 0))
	defer encerrar()
	for _, caso := range []struct {
		ctx  context.Context
		erro error
	}{{cancelado, context.Canceled}, {expirado, context.DeadlineExceeded}} {
		r, err := (AnalisadorJanela{}).Analisar(caso.ctx, janelaTeste(90))
		if r != nil || !errors.Is(err, caso.erro) {
			t.Fatalf("resultado=%v erro=%v", r, err)
		}
	}
}

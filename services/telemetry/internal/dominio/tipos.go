package dominio

import "time"

const (
	TemperaturaMinima = -9999
	TemperaturaMaxima = 9999
	CandidatasMaximas = 50
)

// Temperaturas são inteiros em décimos de grau. nil indica ausência; zero é válido.
type Politica struct {
	Versao int64
	Minima *int32
	Maxima *int32
	Limiar int
}

type Leitura struct {
	IDExterno      string
	CamaraID       int64
	PoliticaVersao int64
	Temperatura    *int32
	Instante       *time.Time
}

// Contexto contém leituras já aceitas; somente candidatas produzem resultados.
// O chamador não deve modificar a janela enquanto a análise estiver em execução.
type Janela struct {
	CamaraID   int64
	Politica   *Politica
	Contexto   []Leitura
	Candidatas []Leitura
}

type Classificacao string

const (
	Segura   Classificacao = "SEGURA"
	Insegura Classificacao = "INSEGURA"
)

type ResultadoCandidata struct {
	IDExterno      string
	Classificacao  Classificacao
	Sequencia      int
	LimiarAtingido bool
	Evidencias     []string
}

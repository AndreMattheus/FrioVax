package dominio

import (
	"context"
	"fmt"
	"slices"
	"strings"
	"time"
)

// AnalisadorJanela calcula sem persistência nem estado entre chamadas.
type AnalisadorJanela struct{}

// Analisar rejeita a janela inteira se qualquer leitura for inválida.
// Erros e cancelamento nunca retornam resultados parciais.
func (AnalisadorJanela) Analisar(ctx context.Context, janela Janela) ([]ResultadoCandidata, error) {
	if err := validarJanela(ctx, janela); err != nil {
		return nil, fmt.Errorf("analisar câmara %d: %w", janela.CamaraID, err)
	}
	politica := janela.Politica
	evidencias := make([]string, 0, politica.Limiar)
	resultados := make([]ResultadoCandidata, 0, len(janela.Candidatas))
	for grupo, leituras := range [][]Leitura{janela.Contexto, janela.Candidatas} {
		for _, leitura := range leituras {
			if err := ctx.Err(); err != nil {
				return nil, fmt.Errorf("analisar câmara %d: %w", janela.CamaraID, err)
			}
			classificacao := Segura
			if *leitura.Temperatura < *politica.Minima || *leitura.Temperatura > *politica.Maxima {
				classificacao = Insegura
				if len(evidencias) == politica.Limiar {
					copy(evidencias, evidencias[1:])
					evidencias = evidencias[:len(evidencias)-1]
				}
				evidencias = append(evidencias, leitura.IDExterno)
			} else {
				evidencias = evidencias[:0]
			}
			if grupo == 1 {
				resultados = append(resultados, ResultadoCandidata{
					IDExterno:      leitura.IDExterno,
					Classificacao:  classificacao,
					Sequencia:      len(evidencias),
					LimiarAtingido: len(evidencias) == politica.Limiar,
					Evidencias:     slices.Clone(evidencias),
				})
			}
		}
	}
	if err := ctx.Err(); err != nil {
		return nil, fmt.Errorf("concluir câmara %d: %w", janela.CamaraID, err)
	}
	return resultados, nil
}

func validarJanela(ctx context.Context, janela Janela) error {
	if err := ctx.Err(); err != nil {
		return err
	}
	if janela.CamaraID <= 0 {
		return fmt.Errorf("%w: identificador da câmara deve ser positivo", ErrJanelaInvalida)
	}
	p := janela.Politica
	if p == nil || p.Versao <= 0 || !temperaturaValida(p.Minima) || !temperaturaValida(p.Maxima) || *p.Minima >= *p.Maxima || p.Limiar < 1 || p.Limiar > 100 {
		return ErrPoliticaInvalida
	}
	if len(janela.Contexto) > p.Limiar-1 || len(janela.Candidatas) == 0 || len(janela.Candidatas) > CandidatasMaximas {
		return fmt.Errorf("%w: quantidade de contexto ou candidatas fora dos limites", ErrJanelaInvalida)
	}
	ids := make(map[string]struct{}, len(janela.Contexto)+len(janela.Candidatas))
	var anterior *time.Time
	for grupo, leituras := range [][]Leitura{janela.Contexto, janela.Candidatas} {
		for indice, leitura := range leituras {
			if err := ctx.Err(); err != nil {
				return err
			}
			if err := validarLeitura(leitura, janela); err != nil {
				return fmt.Errorf("grupo %d, leitura %d: %w", grupo, indice, err)
			}
			if _, existe := ids[leitura.IDExterno]; existe {
				return fmt.Errorf("%w: identificador externo repetido", ErrJanelaInvalida)
			}
			if anterior != nil && !leitura.Instante.After(*anterior) {
				return fmt.Errorf("%w: instantes devem ser estritamente crescentes", ErrJanelaInvalida)
			}
			ids[leitura.IDExterno] = struct{}{}
			anterior = leitura.Instante
		}
	}
	return nil
}

func validarLeitura(leitura Leitura, janela Janela) error {
	if leitura.CamaraID != janela.CamaraID {
		return ErrCamaraDivergente
	}
	if leitura.PoliticaVersao != janela.Politica.Versao {
		return ErrVersaoDivergente
	}
	if strings.TrimSpace(leitura.IDExterno) == "" || !temperaturaValida(leitura.Temperatura) {
		return fmt.Errorf("%w: identificador externo ou temperatura inválidos", ErrJanelaInvalida)
	}
	if leitura.Instante == nil {
		return fmt.Errorf("%w: instante obrigatório", ErrJanelaInvalida)
	}
	instante := leitura.Instante.UTC()
	if instante.Year() < 1 || instante.Year() > 9999 || instante.Nanosecond()%1000 != 0 {
		return fmt.Errorf("%w: instante fora do intervalo ou da precisão permitidos", ErrJanelaInvalida)
	}
	return nil
}

func temperaturaValida(valor *int32) bool {
	return valor != nil && *valor >= TemperaturaMinima && *valor <= TemperaturaMaxima
}

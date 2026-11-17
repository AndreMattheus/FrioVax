package dominio

import (
	"context"
	"errors"
	"fmt"
	"regexp"

	"golang.org/x/sync/errgroup"
)

var ErrSolicitacaoInvalida = errors.New("solicitação de análise inválida")

var correlacaoValida = regexp.MustCompile(`^[A-Za-z0-9._:-]{1,128}$`)

// Solicitacao agrupa as janelas de uma chamada. O chamador deve manter a entrada
// somente para leitura até Analisar concluir.
type Solicitacao struct {
	CorrelacaoID string
	Janelas      []Janela
}

type ResultadoAnalise struct {
	CorrelacaoID string
	Janelas      []ResultadoJanela
}

// ResultadoJanela contém os resultados de todas as candidatas ou o erro da janela.
type ResultadoJanela struct {
	CamaraID       int64
	PoliticaVersao int64
	Candidatas     []ResultadoCandidata
	Erro           error
}

type analiseJanela interface {
	Analisar(context.Context, Janela) ([]ResultadoCandidata, error)
}

// Analisar processa câmaras concorrentemente e devolve os resultados na ordem
// da solicitação. Falhas globais não retornam resultados parciais.
func Analisar(ctx context.Context, solicitacao Solicitacao) (ResultadoAnalise, error) {
	return analisar(ctx, solicitacao, AnalisadorJanela{})
}

func analisar(ctx context.Context, solicitacao Solicitacao, analisador analiseJanela) (ResultadoAnalise, error) {
	if err := validarSolicitacao(ctx, solicitacao); err != nil {
		return ResultadoAnalise{}, fmt.Errorf("validar solicitação: %w", err)
	}

	resultados := make([]ResultadoJanela, len(solicitacao.Janelas))
	grupo, ctxGrupo := errgroup.WithContext(ctx)
	for i, janela := range solicitacao.Janelas {
		if ctxGrupo.Err() != nil {
			break
		}
		grupo.Go(func() error {
			candidatas, err := analisador.Analisar(ctxGrupo, janela)
			if err != nil && !erroDeJanela(err) {
				return fmt.Errorf("analisar janela da câmara %d: %w", janela.CamaraID, err)
			}
			resultado := ResultadoJanela{CamaraID: janela.CamaraID, Erro: err}
			if janela.Politica != nil && janela.Politica.Versao > 0 {
				resultado.PoliticaVersao = janela.Politica.Versao
			}
			if err == nil {
				resultado.Candidatas = candidatas
			}
			resultados[i] = resultado
			return nil
		})
	}
	if err := grupo.Wait(); err != nil {
		return ResultadoAnalise{}, err
	}
	// Wait cancela ctxGrupo também no sucesso. Consulte o contexto da chamada.
	if err := ctx.Err(); err != nil {
		return ResultadoAnalise{}, fmt.Errorf("concluir solicitação: %w", err)
	}
	return ResultadoAnalise{CorrelacaoID: solicitacao.CorrelacaoID, Janelas: resultados}, nil
}

func erroDeJanela(err error) bool {
	if errors.Is(err, context.Canceled) || errors.Is(err, context.DeadlineExceeded) {
		return false
	}
	return errors.Is(err, ErrJanelaInvalida) ||
		errors.Is(err, ErrPoliticaInvalida) ||
		errors.Is(err, ErrCamaraDivergente) ||
		errors.Is(err, ErrVersaoDivergente)
}

func validarSolicitacao(ctx context.Context, solicitacao Solicitacao) error {
	if err := ctx.Err(); err != nil {
		return err
	}
	if !correlacaoValida.MatchString(solicitacao.CorrelacaoID) || len(solicitacao.Janelas) == 0 {
		return fmt.Errorf("%w: correlação inválida ou nenhuma janela", ErrSolicitacaoInvalida)
	}
	camaras := make(map[int64]struct{})
	total := 0
	for _, janela := range solicitacao.Janelas {
		if err := ctx.Err(); err != nil {
			return err
		}
		if janela.CamaraID <= 0 {
			return fmt.Errorf("%w: identificador de câmara deve ser positivo", ErrSolicitacaoInvalida)
		}
		if _, existe := camaras[janela.CamaraID]; existe {
			return fmt.Errorf("%w: câmara %d repetida", ErrSolicitacaoInvalida, janela.CamaraID)
		}
		camaras[janela.CamaraID] = struct{}{}
		if len(janela.Candidatas) > CandidatasMaximas-total {
			return fmt.Errorf("%w: máximo de %d candidatas", ErrSolicitacaoInvalida, CandidatasMaximas)
		}
		total += len(janela.Candidatas)
	}
	if total == 0 {
		return fmt.Errorf("%w: nenhuma candidata", ErrSolicitacaoInvalida)
	}
	return nil
}

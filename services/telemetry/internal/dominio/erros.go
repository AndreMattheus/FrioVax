package dominio

import "errors"

var (
	ErrJanelaInvalida   = errors.New("janela inválida")
	ErrPoliticaInvalida = errors.New("política inválida")
	ErrCamaraDivergente = errors.New("câmara divergente")
	ErrVersaoDivergente = errors.New("versão de política divergente")
)

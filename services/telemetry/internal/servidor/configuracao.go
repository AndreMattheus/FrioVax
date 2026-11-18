package servidor

import (
	"fmt"
	"strconv"
	"time"
)

type Configuracao struct {
	PortaHTTP         int
	PortaGRPC         int
	MaximoBytes       int
	MaximoAnalises    int
	PrazoEncerramento time.Duration
}

func LerConfiguracao(ambiente func(string) string) (Configuracao, error) {
	c := Configuracao{PortaHTTP: 8081, PortaGRPC: 9090, MaximoBytes: 4 * 1024 * 1024, MaximoAnalises: 16, PrazoEncerramento: 5 * time.Second}
	for _, campo := range []struct {
		nome    string
		destino *int
	}{
		{"PORT", &c.PortaHTTP}, {"GRPC_PORT", &c.PortaGRPC},
		{"GRPC_MAX_MESSAGE_BYTES", &c.MaximoBytes}, {"GRPC_MAX_CONCURRENT_ANALYSES", &c.MaximoAnalises},
	} {
		if valor := ambiente(campo.nome); valor != "" {
			numero, err := strconv.Atoi(valor)
			if err != nil {
				return c, fmt.Errorf("%s deve ser um inteiro", campo.nome)
			}
			*campo.destino = numero
		}
	}
	if valor := ambiente("SHUTDOWN_TIMEOUT"); valor != "" {
		prazo, err := time.ParseDuration(valor)
		if err != nil {
			return c, fmt.Errorf("SHUTDOWN_TIMEOUT deve ser uma duração, como 5s")
		}
		c.PrazoEncerramento = prazo
	}
	return c, c.validar()
}

func (c Configuracao) validar() error {
	if c.PortaHTTP < 1 || c.PortaHTTP > 65535 || c.PortaGRPC < 1 || c.PortaGRPC > 65535 || c.PortaHTTP == c.PortaGRPC {
		return fmt.Errorf("PORT e GRPC_PORT devem ser portas distintas entre 1 e 65535")
	}
	if c.MaximoBytes <= 0 || c.MaximoAnalises <= 0 || c.PrazoEncerramento <= 0 {
		return fmt.Errorf("GRPC_MAX_MESSAGE_BYTES, GRPC_MAX_CONCURRENT_ANALYSES e SHUTDOWN_TIMEOUT devem ser positivos")
	}
	return nil
}

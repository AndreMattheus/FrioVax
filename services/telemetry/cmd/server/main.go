package main

import (
	"context"
	"log"
	"os"
	"os/signal"
	"syscall"

	"github.com/AndreMattheus/FrioVax/services/telemetry/internal/servidor"
)

func main() {
	config, err := servidor.LerConfiguracao(os.Getenv)
	if err != nil {
		log.Fatal(err)
	}
	ctx, cancelar := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer cancelar()
	if err := servidor.Executar(ctx, config, log.Default()); err != nil {
		log.Fatal(err)
	}
}

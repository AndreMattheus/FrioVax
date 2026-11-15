package br.ufrn.friovax.api.resumo.aplicacao;

import br.ufrn.friovax.api.resumo.dominio.TotaisResumo;
import java.time.Instant;

public record Resumo(TotaisResumo totais, Instant geradoEm, Instant validade) { }

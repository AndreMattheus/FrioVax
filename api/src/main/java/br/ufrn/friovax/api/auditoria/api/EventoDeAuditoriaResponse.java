package br.ufrn.friovax.api.auditoria.api;

import br.ufrn.friovax.api.auditoria.dominio.EventoDeAuditoria;
import br.ufrn.friovax.api.auditoria.dominio.OperacaoAuditada;
import br.ufrn.friovax.api.auditoria.dominio.RecursoAuditado;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(name = "EventoDeAuditoria", description = "Alteração confirmada registrada na trilha. Nunca muda depois "
    + "de gravada.")
public record EventoDeAuditoriaResponse(
        @Schema(description = "Identificador técnico do evento; define a ordem da trilha.", examples = "1")
        long id,
        @Schema(description = "Momento da alteração, em UTC.", examples = "2026-11-19T17:05:00.123456Z")
        OffsetDateTime instante,
        @Schema(description = "Quem fez a alteração, tirado do token validado.")
        AtorResponse ator,
        @Schema(description = "Tipo do recurso alterado.")
        RecursoAuditado recurso,
        @Schema(description = "Identificador técnico do recurso alterado.", examples = "1")
        long recursoId,
        @Schema(description = "O que foi feito.")
        OperacaoAuditada operacao,
        @Schema(description = "Resumo legível da alteração.",
            examples = "Câmara CAM-01 cadastrada com capacidade 1000 e faixa 2.0 a 8.0 °C")
        String descricao,
        @Schema(description = "Correlação da requisição que fez a alteração (cabeçalho `X-Correlation-Id`).",
            examples = "8c6f2b1e-4a7d-4f3c-9b0e-1d2c3b4a5f60")
        String correlacaoId) {

    @Schema(name = "AtorDeAuditoria")
    public record AtorResponse(
            @Schema(description = "UUID do usuário (`sub` do token); nulo para alterações do próprio sistema.",
                nullable = true)
            UUID id,
            @Schema(description = "Login do usuário no momento da alteração.", examples = "administrador")
            String login,
            @Schema(description = "Perfil usado na alteração: `ADMINISTRADOR`, `OPERADOR`, `AUDITOR` ou `SISTEMA`.",
                examples = "ADMINISTRADOR")
            String perfil) {
    }

    public static EventoDeAuditoriaResponse de(EventoDeAuditoria evento) {
        var ator = evento.ator();
        return new EventoDeAuditoriaResponse(evento.id(), evento.instante(),
            new AtorResponse(ator.id(), ator.login(), ator.perfil()), evento.recurso(), evento.recursoId(),
            evento.operacao(), evento.descricao(), evento.correlacaoId());
    }
}

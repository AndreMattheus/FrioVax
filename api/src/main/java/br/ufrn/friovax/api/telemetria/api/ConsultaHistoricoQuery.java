package br.ufrn.friovax.api.telemetria.api;

import br.ufrn.friovax.api.compartilhado.dominio.Paginacao;
import br.ufrn.friovax.api.telemetria.dominio.PeriodoConsulta;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/** Conversão de parâmetros HTTP; invariantes permanecem nos tipos de domínio. */
public class ConsultaHistoricoQuery {
    @QueryParam("de")
    @Parameter(description = "Início inclusivo ISO 8601 com offset; opcional.", example = "2026-11-19T10:00:00-03:00",
            schema = @Schema(type = SchemaType.STRING, format = "date-time"))
    public String de;
    @QueryParam("ate")
    @Parameter(description = "Fim exclusivo ISO 8601 com offset; deve ser posterior a de; opcional.",
            schema = @Schema(type = SchemaType.STRING, format = "date-time"))
    public String ate;
    @QueryParam("page")
    @Parameter(description = "Página a partir de zero; padrão 0.", example = "0",
            schema = @Schema(type = SchemaType.INTEGER, format = "int32", minimum = "0", defaultValue = "0"))
    public String page;
    @QueryParam("size")
    @Parameter(description = "Tamanho entre 1 e 100; padrão 20.", example = "20",
            schema = @Schema(type = SchemaType.INTEGER, format = "int32", minimum = "1", maximum = "100", defaultValue = "20"))
    public String size;

    void validarParametros(UriInfo uriInfo, String... adicionais) {
        var nomes = new ArrayList<>(List.of("de", "ate", "page", "size"));
        nomes.addAll(List.of(adicionais));
        for (var nome : nomes) {
            var valores = uriInfo.getQueryParameters().get(nome);
            if (valores != null && valores.stream().anyMatch(v -> v == null || v.isBlank())) {
                throw new BadRequestException(nome + " não pode ser vazio.");
            }
        }
    }

    PeriodoConsulta periodo() {
        try {
            return new PeriodoConsulta(de == null ? null : OffsetDateTime.parse(de),
                    ate == null ? null : OffsetDateTime.parse(ate));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new BadRequestException("Período inválido: use instantes com offset e de anterior a ate.", e);
        }
    }

    Paginacao paginacao() {
        try {
            return new Paginacao(page == null ? Paginacao.PAGINA_PADRAO : Integer.parseInt(page),
                    size == null ? Paginacao.TAMANHO_PADRAO : Integer.parseInt(size));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Paginação inválida: page >= 0 e size entre 1 e 100.", e);
        }
    }
}

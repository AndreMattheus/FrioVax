package br.ufrn.friovax.api.camara.aplicacao;

import br.ufrn.friovax.api.auditoria.dominio.OperacaoAuditada;
import br.ufrn.friovax.api.auditoria.dominio.RecursoAuditado;
import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.PoliticaTermica;
import br.ufrn.friovax.api.compartilhado.aplicacao.MudancaConfirmada;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.UUID;

final class MudancaDePolitica {

    private MudancaDePolitica() {
    }

    static MudancaConfirmada aviso(Camara camara, PoliticaTermica anterior, UUID autorId, OffsetDateTime agora) {
        var nova = camara.getPolitica();
        var partes = new ArrayList<String>();
        if (anterior.getTemperaturaMinima().compareTo(nova.getTemperaturaMinima()) != 0
                || anterior.getTemperaturaMaxima().compareTo(nova.getTemperaturaMaxima()) != 0) {
            partes.add("faixa " + faixa(anterior) + " → " + faixa(nova));
        }
        if (anterior.getLimiar() != nova.getLimiar()) {
            partes.add("limiar " + anterior.getLimiar() + " → " + nova.getLimiar());
        }
        var descricao = "Política térmica da câmara " + camara.getCodigo() + " passou da versão "
                + anterior.getVersao() + " para a " + nova.getVersao() + ": " + String.join("; ", partes);
        return MudancaConfirmada.auditada(RecursoAuditado.POLITICA_TERMICA, camara.getId(),
                OperacaoAuditada.ALTERACAO_DE_POLITICA, descricao, autorId, agora);
    }

    private static String faixa(PoliticaTermica politica) {
        return politica.getTemperaturaMinima().toPlainString() + " a "
                + politica.getTemperaturaMaxima().toPlainString() + " °C";
    }
}

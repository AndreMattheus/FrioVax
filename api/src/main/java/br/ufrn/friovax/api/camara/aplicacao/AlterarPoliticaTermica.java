package br.ufrn.friovax.api.camara.aplicacao;

import br.ufrn.friovax.api.auditoria.dominio.ContextoDaOperacao;
import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.CamaraRepository;
import br.ufrn.friovax.api.camara.dominio.PoliticaTermica;
import br.ufrn.friovax.api.compartilhado.aplicacao.PublicadorDeMudancas;
import br.ufrn.friovax.api.compartilhado.dominio.RecursoNaoEncontrado;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;

@ApplicationScoped
public class AlterarPoliticaTermica {
    private final CamaraRepository camaras;
    private final Clock relogio;
    private final PublicadorDeMudancas publicador;
    private final ContextoDaOperacao contexto;

    @Inject
    public AlterarPoliticaTermica(CamaraRepository camaras, Clock relogio, PublicadorDeMudancas publicador,
                                  ContextoDaOperacao contexto) {
        this.camaras = camaras;
        this.relogio = relogio;
        this.publicador = publicador;
        this.contexto = contexto;
    }

    @Transactional
    public Resultado alterar(long camaraId, AlterarPoliticaTermicaDTO dados) {
        Camara camara = camaras.buscarPorIdParaAlteracao(camaraId)
            .orElseThrow(() -> new RecursoNaoEncontrado("Câmara", camaraId));
        camara.exigirVersaoDaPolitica(dados.versaoEsperada());

        var anterior = camara.getPolitica();
        var autorId = contexto.ator().id();
        var agora = OffsetDateTime.now(relogio);
        boolean novaVersao = camara.alterarPolitica(dados.temperaturaMinima(), dados.temperaturaMaxima(),
            dados.limiar(), autorId, agora);
        if (novaVersao) {
            camaras.salvar(camara);
            publicador.publicar(MudancaDePolitica.aviso(camara, anterior, autorId, agora));
        }
        return new Resultado(camaraId, camara.getPolitica(), novaVersao);
    }

    public record Resultado(long camaraId, PoliticaTermica politica, boolean novaVersao) {
    }
}

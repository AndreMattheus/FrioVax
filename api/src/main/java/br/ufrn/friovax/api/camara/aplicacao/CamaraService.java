package br.ufrn.friovax.api.camara.aplicacao;

import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.CamaraRepository;
import br.ufrn.friovax.api.compartilhado.dominio.CodigoDuplicado;
import br.ufrn.friovax.api.auditoria.dominio.OperacaoAuditada;
import br.ufrn.friovax.api.auditoria.dominio.RecursoAuditado;
import br.ufrn.friovax.api.compartilhado.aplicacao.MudancaConfirmada;
import br.ufrn.friovax.api.compartilhado.aplicacao.PublicadorDeMudancas;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;

@ApplicationScoped
public class CamaraService {

    private final CamaraRepository repositorio;
    private final Clock relogio;
    private final PublicadorDeMudancas publicador;

    @Inject
    public CamaraService(CamaraRepository repositorio, Clock relogio, PublicadorDeMudancas publicador) {
        this.repositorio = repositorio;
        this.relogio = relogio;
        this.publicador = publicador;
    }

    public CamaraService(CamaraRepository repositorio, Clock relogio) {
        this(repositorio, relogio, PublicadorDeMudancas.nenhum());
    }

    @Transactional
    public CamaraResultado cadastrar(CadastrarCamaraDTO dados) {
        var agora = OffsetDateTime.now(relogio);
        var camara = Camara.nova(dados.codigo(), dados.nome(), dados.unidade(), dados.capacidade(),
            dados.temperaturaMinima(), dados.temperaturaMaxima(), dados.estado(), agora);

        if (repositorio.existePorCodigo(camara.getCodigo())) {
            throw new CodigoDuplicado("câmara", camara.getCodigo());
        }

        var salva = repositorio.salvar(camara);
        publicador.publicar(MudancaConfirmada.auditada(RecursoAuditado.CAMARA, salva.getId(),
            OperacaoAuditada.CADASTRO, "Câmara " + salva.getCodigo() + " cadastrada com capacidade " + salva.getCapacidade() + " e faixa "
                + salva.getTemperaturaMinima() + " a " + salva.getTemperaturaMaxima() + " °C", agora));
        return new CamaraResultado(salva.getId(), salva.getCodigo(), salva.getNome(), salva.getUnidade(),
            salva.getCapacidade(), salva.getTemperaturaMinima(), salva.getTemperaturaMaxima(),
            salva.getEstado(), salva.getEstadoTermico(), 0, salva.isAtivo(), salva.getCriadoEm(), salva.getAtualizadoEm());
    }
}

package br.ufrn.friovax.api.telemetria.aplicacao;

import br.ufrn.friovax.api.telemetria.dominio.AnaliseTermica;
import br.ufrn.friovax.api.telemetria.dominio.FalhaDeIntegracao;
import br.ufrn.friovax.api.telemetria.dominio.JanelaDeAnalise;
import br.ufrn.friovax.api.telemetria.dominio.LeituraConcorrente;
import br.ufrn.friovax.api.telemetria.dominio.NovaLeitura;
import br.ufrn.friovax.api.telemetria.dominio.ResultadoDaCandidata;
import br.ufrn.friovax.api.telemetria.dominio.ResultadoDaJanela;
import br.ufrn.friovax.api.telemetria.dominio.SolicitacaoDeAnalise;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Recepção de uma leitura individual com revalidação otimista (ADR 0003, seção 5):
 *
 * <ol>
 *     <li>(a) transação curta com a câmara bloqueada para conferir idempotência, ordem e elegibilidade e montar o
 *     snapshot ({@link EtapasDaRecepcao#preparar});</li>
 *     <li>(b) análise no Go fora de transação, sem lock, com o prazo do adaptador;</li>
 *     <li>(c) nova transação curta com o mesmo lock para revalidar e gravar ({@link EtapasDaRecepcao#confirmar}).</li>
 * </ol>
 *
 * <p>Se o snapshot mudou entre (a) e (c), o ciclo é refeito uma única vez; persistindo a mudança, a leitura é recusada
 * com {@link LeituraConcorrente}. {@link TxType#NEVER} impede que a chamada ao Go aconteça dentro de uma transação
 * aberta por quem chama: nenhum lock da câmara fica retido durante a RPC.
 */
@ApplicationScoped
public class ReceberLeitura {

    /** Primeira análise mais a única repetição por snapshot alterado. */
    static final int ANALISES_MAXIMAS = 2;

    private final EtapasDaRecepcao etapas;
    private final AnaliseTermica analise;
    private final Clock relogio;

    @Inject
    public ReceberLeitura(EtapasDaRecepcao etapas, AnaliseTermica analise, Clock relogio) {
        this.etapas = etapas;
        this.analise = analise;
        this.relogio = relogio;
    }

    /**
     * @throws br.ufrn.friovax.api.compartilhado.dominio.ValidacaoDeNegocio se um campo for inválido ou a medição
     *         estiver mais de 5 minutos no futuro
     * @throws br.ufrn.friovax.api.compartilhado.dominio.RecursoNaoEncontrado se a câmara não existir
     * @throws br.ufrn.friovax.api.compartilhado.dominio.ConflitoDeNegocio se a chave já foi usada com outro
     *         conteúdo, a medição estiver fora de ordem, a câmara não estiver apta ou o snapshot mudar duas vezes
     * @throws br.ufrn.friovax.api.telemetria.dominio.AnaliseIndisponivel se o Go estiver ausente ou lento; nada é
     *         gravado
     * @throws FalhaDeIntegracao se o Go recusar a janela ou responder fora do contrato; nada é gravado
     */
    @Transactional(TxType.NEVER)
    public LeituraRecebida receber(long camaraId, String idExterno, BigDecimal temperatura, OffsetDateTime medidaEm,
                                   String origem) {
        var nova = NovaLeitura.de(camaraId, idExterno, medidaEm, temperatura, origem, OffsetDateTime.now(relogio));
        var correlacao = "leitura-" + UUID.randomUUID();
        for (int analises = 1; analises <= ANALISES_MAXIMAS; analises++) {
            var preparacao = etapas.preparar(nova);
            if (preparacao instanceof Preparacao.Repetida(var original, var ocorrencia)) {
                return new LeituraRecebida(original, false, ocorrencia);
            }
            var snapshot = ((Preparacao.Pronta) preparacao).snapshot();
            var resultado = analisar(nova, snapshot, correlacao + "-" + analises);
            switch (etapas.confirmar(nova, snapshot, resultado)) {
                case Confirmacao.Aceita(var aceita, var ocorrencia) -> {
                    return new LeituraRecebida(aceita, true, ocorrencia);
                }
                case Confirmacao.Repetida(var original, var ocorrencia) -> {
                    return new LeituraRecebida(original, false, ocorrencia);
                }
                case Confirmacao.SnapshotAlterado() -> {
                    // Refaz (a), (b) e (c) com os dados atuais.
                }
            }
        }
        throw new LeituraConcorrente(camaraId);
    }

    /** Resultado da candidata com as evidências: a confirmação precisa delas para abrir a ocorrência (S2-11). */
    private ResultadoDaCandidata analisar(NovaLeitura nova, SnapshotDaCamara snapshot, String correlacao) {
        var janela = new JanelaDeAnalise(snapshot.camaraId(), snapshot.politica(), snapshot.contexto(),
                List.of(nova.paraAnalise()));
        var resposta = analise.analisar(SolicitacaoDeAnalise.de(correlacao, janela));
        return switch (resposta.janelas().getFirst()) {
            case ResultadoDaJanela.Analisada analisada -> analisada.candidatas().getFirst();
            case ResultadoDaJanela.Rejeitada rejeitada -> throw new FalhaDeIntegracao(
                    "O serviço de análise térmica recusou a janela da câmara " + rejeitada.camaraId()
                            + " (" + rejeitada.motivo() + ").");
        };
    }
}

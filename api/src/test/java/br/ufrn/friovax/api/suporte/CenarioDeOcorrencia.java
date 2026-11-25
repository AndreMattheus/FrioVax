package br.ufrn.friovax.api.suporte;

import br.ufrn.friovax.api.camara.dominio.CamaraRepository;
import br.ufrn.friovax.api.telemetria.dominio.Leitura;
import br.ufrn.friovax.api.telemetria.dominio.LeituraRepository;
import br.ufrn.friovax.api.telemetria.dominio.Ocorrencia;
import br.ufrn.friovax.api.telemetria.dominio.OcorrenciaRepository;
import br.ufrn.friovax.api.telemetria.dominio.ResultadoTermico;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Monta pelos repositórios, sem Go, o estado que a recepção deixaria depois de três leituras inseguras com N=3: a
 * ocorrência aberta pela terceira e a câmara {@code EM_ALERTA}. Funciona com os repositórios em memória e, dentro de
 * uma transação, com os do PostgreSQL. A câmara deve ter faixa 2–8 °C.
 */
public final class CenarioDeOcorrencia {

    private final CamaraRepository camaras;
    private final LeituraRepository leituras;
    private final OcorrenciaRepository ocorrencias;
    private final long camaraId;
    private final OffsetDateTime inicio;
    private int minuto;

    public CenarioDeOcorrencia(CamaraRepository camaras, LeituraRepository leituras, OcorrenciaRepository ocorrencias,
                               long camaraId, OffsetDateTime inicio) {
        this.camaras = camaras;
        this.leituras = leituras;
        this.ocorrencias = ocorrencias;
        this.camaraId = camaraId;
        this.inicio = inicio;
    }

    /** Leituras S-1 a S-3 inseguras (9,0 °C), a terceira abre a ocorrência; devolve o id dela. */
    public long abrirOcorrencia() {
        var s1 = insegura(1, false);
        var s2 = insegura(2, false);
        var s3 = insegura(3, true);
        var ocorrencia = ocorrencias.salvar(Ocorrencia.abrir(camaraId, versaoVigente(), s3.getId(),
                List.of(s1.getId(), s2.getId(), s3.getId()), List.of(), false, s3.getAceitaEm()));
        var camara = camaras.buscarPorIdParaAlteracao(camaraId).orElseThrow();
        camara.registrarResultadoTermico(true, true);
        camaras.salvar(camara);
        return ocorrencia.getId();
    }

    /** Próxima leitura segura (5,0 °C) na política vigente. */
    public Leitura segura() {
        return gravar("5.0", ResultadoTermico.segura());
    }

    /** Próxima leitura insegura (9,0 °C) na política vigente. */
    public Leitura insegura(int sequencia, boolean limiarAtingido) {
        return gravar("9.0", ResultadoTermico.insegura(sequencia, limiarAtingido));
    }

    private Leitura gravar(String temperatura, ResultadoTermico resultado) {
        int atual = minuto++;
        var instante = inicio.plusMinutes(atual);
        return leituras.salvar(Leitura.nova(camaraId, "S-" + (atual + 1), instante, new BigDecimal(temperatura),
                "sensor-teste", versaoVigente(), resultado, instante));
    }

    private int versaoVigente() {
        return camaras.buscarPorId(camaraId).orElseThrow().getPolitica().getVersao();
    }
}

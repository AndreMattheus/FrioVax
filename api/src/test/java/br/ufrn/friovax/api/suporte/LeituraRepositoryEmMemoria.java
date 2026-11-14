package br.ufrn.friovax.api.suporte;

import br.ufrn.friovax.api.compartilhado.dominio.Pagina;
import br.ufrn.friovax.api.compartilhado.dominio.Paginacao;
import br.ufrn.friovax.api.telemetria.dominio.Leitura;
import br.ufrn.friovax.api.telemetria.dominio.LeituraForaDeOrdem;
import br.ufrn.friovax.api.telemetria.dominio.LeituraJaRegistrada;
import br.ufrn.friovax.api.telemetria.dominio.LeituraRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

public class LeituraRepositoryEmMemoria implements LeituraRepository {
    private static final Comparator<Leitura> MAIS_RECENTE_PRIMEIRO =
            Comparator.comparing(Leitura::getInstante).thenComparing(Leitura::getId).reversed();

    private final Map<Long, Leitura> leituras = new TreeMap<>();
    private final AtomicLong sequencia = new AtomicLong();

    public synchronized void limpar() {
        leituras.clear();
    }

    @Override
    public synchronized Leitura salvar(Leitura leitura) {
        if (leitura.getId() != null) {
            throw new IllegalStateException("Leituras não são alteradas depois de gravadas");
        }
        for (var gravada : leituras.values()) {
            if (gravada.getCamaraId() != leitura.getCamaraId()) {
                continue;
            }
            if (gravada.getIdExterno().equals(leitura.getIdExterno())) {
                throw new LeituraJaRegistrada(leitura.getCamaraId(), leitura.getIdExterno());
            }
            if (gravada.getInstante().isEqual(leitura.getInstante())) {
                throw new LeituraForaDeOrdem(leitura.getCamaraId());
            }
        }
        leitura.atribuirId(sequencia.incrementAndGet());
        leituras.put(leitura.getId(), leitura);
        return leitura;
    }

    @Override
    public synchronized Optional<Leitura> buscarPorId(long id) {
        return Optional.ofNullable(leituras.get(id));
    }

    @Override
    public synchronized Optional<Leitura> buscarPorChave(long camaraId, String idExterno) {
        return daCamara(camaraId).stream().filter(l -> l.getIdExterno().equals(idExterno)).findFirst();
    }

    @Override
    public synchronized Optional<Leitura> ultimaDaCamara(long camaraId) {
        return daCamara(camaraId).stream().min(MAIS_RECENTE_PRIMEIRO);
    }

    @Override
    public synchronized List<Leitura> contextoDaJanela(long camaraId, int politicaVersao, int limite,
                                                     Long aposLeituraId) {
        if (limite < 0 || limite > CONTEXTO_MAXIMO) {
            throw new IllegalArgumentException("O contexto da janela vai de 0 a " + CONTEXTO_MAXIMO + " leituras.");
        }
        var recentes = new ArrayList<>(daCamara(camaraId).stream()
                .filter(l -> l.getPoliticaVersao() == politicaVersao)
                .filter(l -> aposLeituraId == null || l.getId() > aposLeituraId)
                .sorted(MAIS_RECENTE_PRIMEIRO)
                .limit(limite)
                .toList());
        Collections.reverse(recentes);
        return recentes;
    }

    @Override
    public synchronized Pagina<Leitura> listarDaCamara(long camaraId, Paginacao paginacao) {
        return Paginador.paginar(daCamara(camaraId).stream().sorted(MAIS_RECENTE_PRIMEIRO).toList(), paginacao);
    }

    private List<Leitura> daCamara(long camaraId) {
        return leituras.values().stream().filter(l -> l.getCamaraId() == camaraId).toList();
    }
}

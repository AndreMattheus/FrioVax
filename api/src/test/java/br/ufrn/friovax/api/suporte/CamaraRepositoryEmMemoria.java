package br.ufrn.friovax.api.suporte;

import br.ufrn.friovax.api.camara.dominio.Camara;
import br.ufrn.friovax.api.camara.dominio.CamaraFiltro;
import br.ufrn.friovax.api.camara.dominio.CamaraRepository;
import br.ufrn.friovax.api.compartilhado.dominio.CodigoDuplicado;
import br.ufrn.friovax.api.compartilhado.dominio.Pagina;
import br.ufrn.friovax.api.compartilhado.dominio.Paginacao;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@link CamaraRepository} em memória para testes sem banco. Guarda cópias, como o banco faria: alterações num
 * objeto só valem depois de {@link #salvar(Camara)}.
 */
public class CamaraRepositoryEmMemoria implements CamaraRepository {

    private final Map<Long, Camara> camaras = new TreeMap<>();
    private final AtomicLong sequencia = new AtomicLong();

    @Override
    public synchronized Camara salvar(Camara camara) {
        if (camara.getId() == null) {
            if (existePorCodigo(camara.getCodigo())) {
                throw new CodigoDuplicado("câmara", camara.getCodigo());
            }
            camara.atribuirId(sequencia.incrementAndGet());
        } else if (!camaras.containsKey(camara.getId())) {
            throw new IllegalStateException("Câmara " + camara.getId() + " não está gravada");
        }
        camaras.put(camara.getId(), copia(camara));
        return camara;
    }

    @Override
    public synchronized Optional<Camara> buscarPorId(long id) {
        return Optional.ofNullable(camaras.get(id)).map(CamaraRepositoryEmMemoria::copia);
    }

    @Override
    public Optional<Camara> buscarPorIdParaAlteracao(long id) {
        return buscarPorId(id);
    }

    @Override
    public synchronized boolean existePorCodigo(String codigo) {
        return camaras.values().stream().anyMatch(camara -> camara.getCodigo().equals(codigo));
    }

    @Override
    public synchronized Pagina<Camara> listar(CamaraFiltro filtro, Paginacao paginacao) {
        var filtradas = camaras.values().stream()
                .filter(camara -> camara.isAtivo() == filtro.ativo())
                .filter(camara -> filtro.unidade() == null || camara.getUnidade().equalsIgnoreCase(filtro.unidade()))
                .filter(camara -> filtro.estado() == null || camara.getEstado() == filtro.estado())
                .map(CamaraRepositoryEmMemoria::copia)
                .toList();
        return Paginador.paginar(filtradas, paginacao);
    }

    private static Camara copia(Camara camara) {
        return Camara.reconstituir(camara.getId(), camara.getCodigo(), camara.getNome(), camara.getUnidade(),
                camara.getCapacidade(), camara.getTemperaturaMinima(), camara.getTemperaturaMaxima(),
                camara.getEstado(), camara.isAtivo(), camara.getCriadoEm(), camara.getAtualizadoEm());
    }
}

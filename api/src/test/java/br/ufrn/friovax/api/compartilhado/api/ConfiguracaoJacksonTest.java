package br.ufrn.friovax.api.compartilhado.api;

import br.ufrn.friovax.api.camara.api.CamaraRequest;
import br.ufrn.friovax.api.camara.dominio.EstadoCamara;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfiguracaoJacksonTest {

    private final ObjectMapper objectMapper = criarObjectMapper();

    private static ObjectMapper criarObjectMapper() {
        var objectMapper = new ObjectMapper();
        new ConfiguracaoJackson().customize(objectMapper);
        return objectMapper;
    }

    @ParameterizedTest
    @ValueSource(strings = {"10.5", "10.0"})
    void rejeitaCapacidadeEnviadaComoDecimal(String capacidade) {
        var erro = assertThrows(MismatchedInputException.class, () -> objectMapper.readValue(
                "{\"capacidade\":" + capacidade + "}", CamaraRequest.class));

        assertEquals("capacidade", erro.getPath().getLast().getFieldName());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void rejeitaPosicaoNumericaDoEnum(int estado) {
        var erro = assertThrows(MismatchedInputException.class, () -> objectMapper.readValue(
                "{\"estado\":" + estado + "}", CamaraRequest.class));

        assertEquals("estado", erro.getPath().getLast().getFieldName());
    }

    @Test
    void preservaInteirosEnumPorNomeETemperaturasDecimais() throws Exception {
        var request = objectMapper.readValue("""
                {
                  "codigo": "CAM-01",
                  "nome": "Câmara principal",
                  "unidade": "UBS Centro",
                  "capacidade": 10,
                  "temperaturaMinima": 2.5,
                  "temperaturaMaxima": 8.0,
                  "estado": "OPERACIONAL"
                }
                """, CamaraRequest.class);

        assertEquals(10, request.capacidade());
        assertEquals(EstadoCamara.OPERACIONAL, request.estado());
        assertEquals(new BigDecimal("2.5"), request.temperaturaMinima());
        assertEquals(new BigDecimal("8.0"), request.temperaturaMaxima());
    }
}

package br.ufrn.friovax.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatusResourceTest {

    @Test
    void shouldReportServiceAsUp() {
        var status = new StatusResource().status();

        assertEquals("friovax-api", status.service());
        assertEquals("UP", status.status());
    }
}

package pe.org.beneficencia.legalcontrol.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import pe.org.beneficencia.legalcontrol.integration.PostgresIntegrationTest;

@AutoConfigureMockMvc
class HealthContractTest extends PostgresIntegrationTest {
    @Autowired private MockMvc mvc;

    @Test
    void pingPublicoSinSesion() throws Exception {
        mvc.perform(get("/ping"))
                .andExpect(status().isOk())
                .andExpect(content().string("pong"))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void actuatorSaludSinDetalles() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void rutasInternasSiguenProtegidas() throws Exception {
        for (String ruta : new String[]{"/actuator/env", "/actuator/metrics", "/pendientes"}) {
            mvc.perform(get(ruta)).andExpect(status().is3xxRedirection());
        }
    }
}

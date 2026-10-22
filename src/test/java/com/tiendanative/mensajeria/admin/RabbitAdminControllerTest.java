package com.tiendanative.mensajeria.admin;

import com.tiendanative.mensajeria.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RabbitAdminController.class)
@Import(SecurityConfig.class)
class RabbitAdminControllerTest {

    private static final String COLA_VALIDA = """
        {"nombre":"pagos.queue","tipo":"QUORUM","durable":true,"conDlq":true}""";

    @Autowired
    private MockMvc mvc;

    @MockBean
    private RabbitAdminService rabbitAdmin;
    @MockBean
    private ListenerAdminService listeners;
    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void sinTokenResponde401() throws Exception {
        mvc.perform(post("/api/v1/admin/queues").contentType(MediaType.APPLICATION_JSON).content(COLA_VALIDA))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSinScopeDeEscrituraResponde403() throws Exception {
        mvc.perform(post("/api/v1/admin/queues").with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_productos/read")))
                .contentType(MediaType.APPLICATION_JSON).content(COLA_VALIDA))
            .andExpect(status().isForbidden());
    }

    @Test
    void colaValidaResponde201() throws Exception {
        mvc.perform(post("/api/v1/admin/queues").with(escritor())
                .contentType(MediaType.APPLICATION_JSON).content(COLA_VALIDA))
            .andExpect(status().isCreated());
    }

    @Test
    void nombreVacioResponde400ConElCampoQueFalla() throws Exception {
        mvc.perform(post("/api/v1/admin/queues").with(escritor())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"\",\"tipo\":\"CLASSIC\",\"durable\":true}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errores.nombre").exists());
        verify(rabbitAdmin, never()).crearCola(any());
    }

    @Test
    void colaQuorumNoDurableEsConfiguracionInvalida() throws Exception {
        mvc.perform(post("/api/v1/admin/queues").with(escritor())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"pagos.queue\",\"tipo\":\"QUORUM\",\"durable\":false}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errores.configuracionValida").exists());
    }

    @Test
    void nombreConPrefijoReservadoResponde400() throws Exception {
        mvc.perform(post("/api/v1/admin/exchanges").with(escritor())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"amq.mio\",\"tipo\":\"TOPIC\",\"durable\":true}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void tipoDeExchangeDesconocidoResponde400() throws Exception {
        mvc.perform(post("/api/v1/admin/exchanges").with(escritor())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"pagos.exchange\",\"tipo\":\"OTRO\",\"durable\":true}"))
            .andExpect(status().isBadRequest());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor escritor() {
        return jwt().authorities(new SimpleGrantedAuthority("SCOPE_productos/write"));
    }
}

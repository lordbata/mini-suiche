package com.minisuiche.vault.card;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Prueba de integración: levanta Spring completo con H2 y las migraciones Flyway. */
@SpringBootTest
@AutoConfigureMockMvc
class CardControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    StoredCardRepository repository;

    @Test
    void tokenizaYNuncaDevuelveNiGuardaElPanEnClaro() throws Exception {
        String body = mvc.perform(post("/api/v1/cards/tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pan":"4111111111111111","expMonth":12,"expYear":2099,"cvv":"123"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.last4").value("1111"))
                .andExpect(jsonPath("$.brand").value("VISA"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("4111111111111111").doesNotContain("123\"");
        assertThat(repository.findAll())
                .allSatisfy(c -> assertThat(c.getPanCiphertext()).doesNotContain("4111111111111111"));
    }

    @Test
    void laMismaTarjetaDevuelveElMismoToken() throws Exception {
        String req = """
                {"pan":"5555555555554444","expMonth":1,"expYear":2099,"cvv":"999"}
                """;
        String a = mvc.perform(post("/api/v1/cards/tokens").contentType(MediaType.APPLICATION_JSON).content(req))
                .andReturn().getResponse().getContentAsString();
        String b = mvc.perform(post("/api/v1/cards/tokens").contentType(MediaType.APPLICATION_JSON).content(req))
                .andReturn().getResponse().getContentAsString();
        assertThat(a).isEqualTo(b);
    }

    @Test
    void rechazaPanQueNoPasaLuhn() throws Exception {
        mvc.perform(post("/api/v1/cards/tokens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pan":"4111111111111112","expMonth":12,"expYear":2099,"cvv":"123"}
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void tokenInexistenteDa404() throws Exception {
        mvc.perform(get("/internal/v1/cards/tok_no_existe")).andExpect(status().isNotFound());
    }
}

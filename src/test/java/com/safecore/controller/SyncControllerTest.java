package com.safecore.controller;

import com.safecore.config.SecurityConfig;
import com.safecore.security.JwtService;
import com.safecore.security.UserDetailsServiceImpl;
import com.safecore.service.SyncService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SyncController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class SyncControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean SyncService syncService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsServiceImpl userDetailsService;

    @Test
    @WithMockUser(roles = "ENGENHEIRO")
    void deveRetornar400_quandoNcDoBatchTemLocalizacaoIdNula() throws Exception {
        String json = """
            {
              "items": [
                {
                  "localId": "local-1",
                  "tipo": "NC",
                  "nc": {
                    "estabelecimentoId": "%s",
                    "titulo": "Titulo",
                    "localizacaoId": null,
                    "empresaContratadaId": "%s",
                    "normaIds": [],
                    "emailsManuais": [],
                    "emailsPadraoExcluidos": []
                  }
                }
              ]
            }
            """.formatted(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/api/sync/ocorrencias")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ENGENHEIRO")
    void deveRetornar400_quandoLocalIdEmBranco() throws Exception {
        String json = """
            {
              "items": [
                {
                  "localId": "",
                  "tipo": "NC",
                  "nc": {
                    "estabelecimentoId": "%s",
                    "titulo": "Titulo",
                    "localizacaoId": "%s",
                    "severidade": 3,
                    "probabilidade": 2,
                    "empresaContratadaId": "%s",
                    "normaIds": [],
                    "emailsManuais": [],
                    "emailsPadraoExcluidos": []
                  }
                }
              ]
            }
            """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/api/sync/ocorrencias")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ENGENHEIRO")
    void deveRetornar400_quandoTipoNulo() throws Exception {
        String json = """
            {
              "items": [
                {
                  "localId": "%s",
                  "tipo": null,
                  "nc": {
                    "estabelecimentoId": "%s",
                    "titulo": "Titulo",
                    "localizacaoId": "%s",
                    "severidade": 3,
                    "probabilidade": 2,
                    "empresaContratadaId": "%s",
                    "normaIds": [],
                    "emailsManuais": [],
                    "emailsPadraoExcluidos": []
                  }
                }
              ]
            }
            """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/api/sync/ocorrencias")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ENGENHEIRO")
    void deveRetornar400_quandoLocalIdNaoEhUuidValido() throws Exception {
        String json = """
            {
              "items": [
                {
                  "localId": "nao-sou-um-uuid",
                  "tipo": "NC",
                  "nc": {
                    "estabelecimentoId": "%s",
                    "titulo": "Titulo",
                    "localizacaoId": "%s",
                    "severidade": 3,
                    "probabilidade": 2,
                    "empresaContratadaId": "%s",
                    "normaIds": [],
                    "emailsManuais": [],
                    "emailsPadraoExcluidos": []
                  }
                }
              ]
            }
            """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/api/sync/ocorrencias")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }
}

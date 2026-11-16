package com.searchagro.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.searchagro.dto.AnaliseUmidadeResponseDTO;
import com.searchagro.dto.AnalysisJobDTO;
import com.searchagro.interfaces.UmidadeSoloService;
import com.searchagro.controllers.ApiExceptionHandler;
import com.searchagro.controllers.UmidadeController;

@WebMvcTest(controllers = {UmidadeController.class, ApiExceptionHandler.class})
class UmidadeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UmidadeSoloService umidadeSoloService;

    @Test
    void startsAnalysisAndReturnsAnAcceptedJobId() throws Exception {
        when(umidadeSoloService.iniciarAnalise(any())).thenReturn(new AnalysisJobDTO(
                "job-test",
                "QUEUED",
                0,
                "Na fila",
                "AgroSearch",
                "Aguardando.",
                null,
                null));

        mockMvc.perform(post("/api/umidade/analisar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"poligono\":{\"coordenadas\":["
                                + "{\"latitude\":-16.68,\"longitude\":-49.25},"
                                + "{\"latitude\":-16.681,\"longitude\":-49.24},"
                                + "{\"latitude\":-16.69,\"longitude\":-49.24},"
                                + "{\"latitude\":-16.69,\"longitude\":-49.25},"
                                + "{\"latitude\":-16.68,\"longitude\":-49.25}]}}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value("job-test"))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.percentage").value(0));
    }

    @Test
    void returnsAnalysisProgressAndResultByJobId() throws Exception {
        when(umidadeSoloService.consultarAnalise("job-test")).thenReturn(new AnalysisJobDTO(
                "job-test",
                "COMPLETED",
                100,
                "Análise concluída",
                "AgroSearch",
                "Pronta.",
                new AnaliseUmidadeResponseDTO(
                        LocalDate.of(2026, 11, 14),
                        9.0,
                        "SPL3SMP_E.006",
                        "m³/m³",
                        false,
                        "Sem CC e PMP.",
                        Collections.emptyList()),
                null));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/umidade/analisar/job-test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.percentage").value(100))
                .andExpect(jsonPath("$.result.dataReferencia").value("2026-11-14"));
    }

    @Test
    void rejectsAnalysisWithoutPolygon() throws Exception {
        mockMvc.perform(post("/api/umidade/analisar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Origin", "https://appauma-tenley-accusably.ngrok-free.dev")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(
                        "Access-Control-Allow-Origin",
                        "https://appauma-tenley-accusably.ngrok-free.dev"))
                .andExpect(jsonPath("$.erro").exists());
    }

    @Test
    void rejectsCoordinatesWithoutLatitude() throws Exception {
        mockMvc.perform(post("/api/umidade/analisar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"poligono\":{\"coordenadas\":["
                                + "{\"longitude\":-49.25},"
                                + "{\"latitude\":-16.68,\"longitude\":-49.24},"
                                + "{\"latitude\":-16.69,\"longitude\":-49.24},"
                                + "{\"latitude\":-16.69,\"longitude\":-49.25}]}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").exists());
    }
}

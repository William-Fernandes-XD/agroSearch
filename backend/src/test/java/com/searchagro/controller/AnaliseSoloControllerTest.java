package com.searchagro.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

import com.searchagro.dto.AnaliseSoloDTO;
import com.searchagro.exception.ApiException;
import com.searchagro.service.ConsultaApi;

@WebMvcTest(controllers = {AnaliseSoloController.class, ApiExceptionHandler.class})
class AnaliseSoloControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ConsultaApi consultaApiService;

    @Test
    void retornaAnaliseSoloComSucesso() throws Exception {
        AnaliseSoloDTO dto = new AnaliseSoloDTO(
                "Goiânia, Goiás, Brasil",
                -16.6869,
                -49.2648,
                "phh2o",
                "0-5cm",
                5.2);

        when(consultaApiService.analisarSoloPorEndereco("Goiânia, Goiás, Brasil"))
                .thenReturn(dto);

        mockMvc.perform(get("/api/analise/ph/endereco")
                        .param("endereco", "Goiânia, Goiás, Brasil"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.localizacao").value("Goiânia, Goiás, Brasil"))
                .andExpect(jsonPath("$.latitude").value(-16.6869))
                .andExpect(jsonPath("$.longitude").value(-49.2648))
                .andExpect(jsonPath("$.propriedade").value("phh2o"))
                .andExpect(jsonPath("$.profundidade").value("0-5cm"))
                .andExpect(jsonPath("$.ph").value(5.2));
    }

    @Test
    void retornaAnaliseSoloComPhNulo() throws Exception {
        AnaliseSoloDTO dto = new AnaliseSoloDTO(
                "Goiânia, Goiás, Brasil",
                -16.6869,
                -49.2648,
                "phh2o",
                "0-5cm",
                null);

        when(consultaApiService.analisarSoloPorEndereco("Goiânia, Goiás, Brasil"))
                .thenReturn(dto);

        mockMvc.perform(get("/api/analise/ph/endereco")
                        .param("endereco", "Goiânia, Goiás, Brasil"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.localizacao").value("Goiânia, Goiás, Brasil"))
                .andExpect(jsonPath("$.latitude").value(-16.6869))
                .andExpect(jsonPath("$.longitude").value(-49.2648))
                .andExpect(jsonPath("$.propriedade").value("phh2o"))
                .andExpect(jsonPath("$.profundidade").value("0-5cm"))
                .andExpect(jsonPath("$.ph").doesNotExist()); // ou nulo
    }

    @Test
    void retornaNotFoundQuandoLocalizacaoNaoEncontrada() throws Exception {
        when(consultaApiService.analisarSoloPorEndereco("Lugar Inexistente"))
                .thenThrow(new ApiException("Localização não encontrada.", HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/analise/ph/endereco")
                        .param("endereco", "Lugar Inexistente"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Localização não encontrada."));
    }
}

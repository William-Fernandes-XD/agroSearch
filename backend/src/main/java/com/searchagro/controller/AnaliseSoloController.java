package com.searchagro.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.searchagro.dto.AnaliseSoloDTO;
import com.searchagro.service.ConsultaApi;

@RestController
@RequestMapping("/api/analise")
public class AnaliseSoloController {

    private final ConsultaApi consultaApiService;

    public AnaliseSoloController(ConsultaApi consultaApiService) {
        this.consultaApiService = consultaApiService;
    }

    /**
     * Endpoint para consulta de estimativa de pH do solo a partir de um endereço textual.
     * Exemplo: GET /api/analise/ph/endereco?endereco=Goiânia,Goiás,Brasil
     *
     * @param endereco Endereço informado pelo usuário.
     * @return DTO com localização, latitude, longitude e valor estimado de pH do solo.
     */
    @GetMapping("/ph/endereco")
    public ResponseEntity<AnaliseSoloDTO> consultarPhPorEndereco(@RequestParam("endereco") String endereco) {
        AnaliseSoloDTO resultado = consultaApiService.analisarSoloPorEndereco(endereco);
        return ResponseEntity.ok(resultado);
    }
}

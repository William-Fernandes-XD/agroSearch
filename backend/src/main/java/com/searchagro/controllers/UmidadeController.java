package com.searchagro.controllers;

import javax.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.searchagro.dto.AnaliseUmidadeResponseDTO;
import com.searchagro.dto.AnalisarUmidadeRequestDTO;
import com.searchagro.interfaces.UmidadeSoloService;

@RestController
@RequestMapping("/api/umidade")
public class UmidadeController {

    private final UmidadeSoloService umidadeSoloService;

    public UmidadeController(UmidadeSoloService umidadeSoloService) {
        this.umidadeSoloService = umidadeSoloService;
    }

    @PostMapping("/analisar")
    public AnaliseUmidadeResponseDTO analisar(@Valid @RequestBody AnalisarUmidadeRequestDTO request) {
        return umidadeSoloService.analisar(request.getPoligono());
    }
}

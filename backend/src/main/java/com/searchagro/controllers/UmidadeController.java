package com.searchagro.controllers;

import javax.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.searchagro.dto.AnalysisJobDTO;
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
    public ResponseEntity<AnalysisJobDTO> analisar(@Valid @RequestBody AnalisarUmidadeRequestDTO request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(umidadeSoloService.iniciarAnalise(request.getPoligono()));
    }

    @GetMapping("/analisar/{jobId}")
    public AnalysisJobDTO consultarAnalise(@PathVariable String jobId) {
        return umidadeSoloService.consultarAnalise(jobId);
    }
}

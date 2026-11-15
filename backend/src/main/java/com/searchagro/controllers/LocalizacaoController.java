package com.searchagro.controllers;

import java.util.List;

import javax.validation.constraints.NotBlank;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.searchagro.entities.Localizacao;
import com.searchagro.interfaces.LocalizacaoService;

@Validated
@RestController
@RequestMapping("/api/localizacao")
public class LocalizacaoController {

    private final LocalizacaoService localizacaoService;

    public LocalizacaoController(LocalizacaoService localizacaoService) {
        this.localizacaoService = localizacaoService;
    }

    @GetMapping("/buscar")
    public List<Localizacao> buscar(
            @RequestParam("endereco") @NotBlank(message = "Informe um endereço para pesquisar.") String endereco) {
        return localizacaoService.buscar(endereco);
    }
}

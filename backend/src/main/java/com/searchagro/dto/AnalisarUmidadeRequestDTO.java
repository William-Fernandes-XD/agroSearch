package com.searchagro.dto;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class AnalisarUmidadeRequestDTO {

    @Valid
    @NotNull(message = "O polígono é obrigatório.")
    private PoligonoDTO poligono;

    public AnalisarUmidadeRequestDTO() {
    }

    public PoligonoDTO getPoligono() {
        return poligono;
    }

    public void setPoligono(PoligonoDTO poligono) {
        this.poligono = poligono;
    }
}

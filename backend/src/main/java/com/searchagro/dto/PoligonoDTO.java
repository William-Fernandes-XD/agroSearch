package com.searchagro.dto;

import java.util.ArrayList;
import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;

import com.searchagro.entities.CoordenadaGeografica;

public class PoligonoDTO {

    @Valid
    @NotEmpty(message = "Informe as coordenadas do polígono.")
    @Size(min = 4, max = 500, message = "O polígono deve ter entre 4 e 500 coordenadas.")
    private List<CoordenadaGeografica> coordenadas = new ArrayList<>();

    public PoligonoDTO() {
    }

    public List<CoordenadaGeografica> getCoordenadas() {
        return coordenadas;
    }

    public void setCoordenadas(List<CoordenadaGeografica> coordenadas) {
        this.coordenadas = coordenadas;
    }
}

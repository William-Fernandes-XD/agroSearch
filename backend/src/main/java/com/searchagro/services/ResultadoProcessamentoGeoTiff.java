package com.searchagro.services;

import java.util.List;

import com.searchagro.entities.PontoUmidade;

public class ResultadoProcessamentoGeoTiff {

    private final List<PontoUmidade> pontos;
    private final long celulasNaArea;
    private final long celulasValidas;
    private final long celulasVespertinas;

    public ResultadoProcessamentoGeoTiff(
            List<PontoUmidade> pontos,
            long celulasNaArea,
            long celulasValidas,
            long celulasVespertinas) {
        this.pontos = pontos;
        this.celulasNaArea = celulasNaArea;
        this.celulasValidas = celulasValidas;
        this.celulasVespertinas = celulasVespertinas;
    }

    public ResultadoProcessamentoGeoTiff(List<PontoUmidade> pontos, long celulasNaArea, long celulasValidas) {
        this(pontos, celulasNaArea, celulasValidas, 0);
    }

    public List<PontoUmidade> getPontos() {
        return pontos;
    }

    public long getCelulasNaArea() {
        return celulasNaArea;
    }

    public long getCelulasValidas() {
        return celulasValidas;
    }

    public long getCelulasVespertinas() {
        return celulasVespertinas;
    }

    public double getProporcaoCelulasValidas() {
        return celulasNaArea == 0 ? 0.0 : (double) celulasValidas / celulasNaArea;
    }
}

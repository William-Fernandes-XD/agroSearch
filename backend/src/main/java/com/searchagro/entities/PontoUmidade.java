package com.searchagro.entities;

import com.searchagro.enums.ClassificacaoUmidade;
import com.searchagro.enums.NivelAtencao;

public class PontoUmidade {

    private double latitude;
    private double longitude;
    private double umidadeVolumetrica;
    private Double ndvi;
    private Double temperaturaSuperficieC;
    private Double indiceSecuraAproximado;
    private Double umidadeRefinada;
    private Double pad;
    private ClassificacaoUmidade classificacao;
    private NivelAtencao nivelAtencao;

    public PontoUmidade() {
    }

    public PontoUmidade(
            double latitude,
            double longitude,
            double umidadeVolumetrica,
            Double pad,
            ClassificacaoUmidade classificacao,
            NivelAtencao nivelAtencao) {
        this(
                latitude,
                longitude,
                umidadeVolumetrica,
                null,
                null,
                null,
                null,
                pad,
                classificacao,
                nivelAtencao);
    }

    public PontoUmidade(
            double latitude,
            double longitude,
            double umidadeVolumetrica,
            Double ndvi,
            Double temperaturaSuperficieC,
            Double indiceSecuraAproximado,
            Double umidadeRefinada,
            Double pad,
            ClassificacaoUmidade classificacao,
            NivelAtencao nivelAtencao) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.umidadeVolumetrica = umidadeVolumetrica;
        this.ndvi = ndvi;
        this.temperaturaSuperficieC = temperaturaSuperficieC;
        this.indiceSecuraAproximado = indiceSecuraAproximado;
        this.umidadeRefinada = umidadeRefinada;
        this.pad = pad;
        this.classificacao = classificacao;
        this.nivelAtencao = nivelAtencao;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public double getUmidadeVolumetrica() {
        return umidadeVolumetrica;
    }

    public void setUmidadeVolumetrica(double umidadeVolumetrica) {
        this.umidadeVolumetrica = umidadeVolumetrica;
    }

    public Double getNdvi() {
        return ndvi;
    }

    public void setNdvi(Double ndvi) {
        this.ndvi = ndvi;
    }

    public Double getTemperaturaSuperficieC() {
        return temperaturaSuperficieC;
    }

    public void setTemperaturaSuperficieC(Double temperaturaSuperficieC) {
        this.temperaturaSuperficieC = temperaturaSuperficieC;
    }

    public Double getIndiceSecuraAproximado() {
        return indiceSecuraAproximado;
    }

    public void setIndiceSecuraAproximado(Double indiceSecuraAproximado) {
        this.indiceSecuraAproximado = indiceSecuraAproximado;
    }

    public Double getUmidadeRefinada() {
        return umidadeRefinada;
    }

    public void setUmidadeRefinada(Double umidadeRefinada) {
        this.umidadeRefinada = umidadeRefinada;
    }

    public Double getPad() {
        return pad;
    }

    public void setPad(Double pad) {
        this.pad = pad;
    }

    public ClassificacaoUmidade getClassificacao() {
        return classificacao;
    }

    public void setClassificacao(ClassificacaoUmidade classificacao) {
        this.classificacao = classificacao;
    }

    public NivelAtencao getNivelAtencao() {
        return nivelAtencao;
    }

    public void setNivelAtencao(NivelAtencao nivelAtencao) {
        this.nivelAtencao = nivelAtencao;
    }
}

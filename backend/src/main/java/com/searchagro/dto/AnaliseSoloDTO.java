package com.searchagro.dto;

public class AnaliseSoloDTO {

    private String localizacao;
    private Double latitude;
    private Double longitude;
    private String propriedade;
    private String profundidade;
    private Double ph;

    public AnaliseSoloDTO() {
    }

    public AnaliseSoloDTO(String localizacao, Double latitude, Double longitude, String propriedade, String profundidade, Double ph) {
        this.localizacao = localizacao;
        this.latitude = latitude;
        this.longitude = longitude;
        this.propriedade = propriedade;
        this.profundidade = profundidade;
        this.ph = ph;
    }

    public String getLocalizacao() {
        return localizacao;
    }

    public void setLocalizacao(String localizacao) {
        this.localizacao = localizacao;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public String getPropriedade() {
        return propriedade;
    }

    public void setPropriedade(String propriedade) {
        this.propriedade = propriedade;
    }

    public String getProfundidade() {
        return profundidade;
    }

    public void setProfundidade(String profundidade) {
        this.profundidade = profundidade;
    }

    public Double getPh() {
        return ph;
    }

    public void setPh(Double ph) {
        this.ph = ph;
    }

    @Override
    public String toString() {
        return "AnaliseSoloDTO{" +
                "localizacao='" + localizacao + '\'' +
                ", latitude=" + latitude +
                ", longitude=" + longitude +
                ", propriedade='" + propriedade + '\'' +
                ", profundidade='" + profundidade + '\'' +
                ", ph=" + ph +
                '}';
    }
}

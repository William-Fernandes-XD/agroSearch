package com.searchagro.entities;

public class Localizacao {

    private String nome;
    private double latitude;
    private double longitude;
    private String caixaDelimitadora;

    public Localizacao() {
    }

    public Localizacao(String nome, double latitude, double longitude, String caixaDelimitadora) {
        this.nome = nome;
        this.latitude = latitude;
        this.longitude = longitude;
        this.caixaDelimitadora = caixaDelimitadora;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
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

    public String getCaixaDelimitadora() {
        return caixaDelimitadora;
    }

    public void setCaixaDelimitadora(String caixaDelimitadora) {
        this.caixaDelimitadora = caixaDelimitadora;
    }
}

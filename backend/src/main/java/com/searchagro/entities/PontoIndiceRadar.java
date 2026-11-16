package com.searchagro.entities;

public class PontoIndiceRadar {

    private double latitude;
    private double longitude;
    private double retornoRadarDb;
    private double indiceRetornoRelativo;

    public PontoIndiceRadar() {
    }

    public PontoIndiceRadar(
            double latitude,
            double longitude,
            double retornoRadarDb,
            double indiceRetornoRelativo) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.retornoRadarDb = retornoRadarDb;
        this.indiceRetornoRelativo = indiceRetornoRelativo;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public double getRetornoRadarDb() {
        return retornoRadarDb;
    }

    public double getIndiceRetornoRelativo() {
        return indiceRetornoRelativo;
    }
}

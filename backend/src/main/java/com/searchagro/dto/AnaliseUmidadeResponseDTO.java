package com.searchagro.dto;

import java.time.LocalDate;
import java.util.List;

import com.searchagro.entities.PontoUmidade;

public class AnaliseUmidadeResponseDTO {

    private LocalDate dataReferencia;
    private double resolucaoEspacialKm;
    private String produto;
    private String unidade;
    private boolean classificacaoDisponivel;
    private String aviso;
    private List<PontoUmidade> pontos;

    public AnaliseUmidadeResponseDTO() {
    }

    public AnaliseUmidadeResponseDTO(
            LocalDate dataReferencia,
            double resolucaoEspacialKm,
            String produto,
            String unidade,
            boolean classificacaoDisponivel,
            String aviso,
            List<PontoUmidade> pontos) {
        this.dataReferencia = dataReferencia;
        this.resolucaoEspacialKm = resolucaoEspacialKm;
        this.produto = produto;
        this.unidade = unidade;
        this.classificacaoDisponivel = classificacaoDisponivel;
        this.aviso = aviso;
        this.pontos = pontos;
    }

    public LocalDate getDataReferencia() {
        return dataReferencia;
    }

    public void setDataReferencia(LocalDate dataReferencia) {
        this.dataReferencia = dataReferencia;
    }

    public double getResolucaoEspacialKm() {
        return resolucaoEspacialKm;
    }

    public void setResolucaoEspacialKm(double resolucaoEspacialKm) {
        this.resolucaoEspacialKm = resolucaoEspacialKm;
    }

    public String getProduto() {
        return produto;
    }

    public void setProduto(String produto) {
        this.produto = produto;
    }

    public String getUnidade() {
        return unidade;
    }

    public void setUnidade(String unidade) {
        this.unidade = unidade;
    }

    public boolean isClassificacaoDisponivel() {
        return classificacaoDisponivel;
    }

    public void setClassificacaoDisponivel(boolean classificacaoDisponivel) {
        this.classificacaoDisponivel = classificacaoDisponivel;
    }

    public String getAviso() {
        return aviso;
    }

    public void setAviso(String aviso) {
        this.aviso = aviso;
    }

    public List<PontoUmidade> getPontos() {
        return pontos;
    }

    public void setPontos(List<PontoUmidade> pontos) {
        this.pontos = pontos;
    }
}

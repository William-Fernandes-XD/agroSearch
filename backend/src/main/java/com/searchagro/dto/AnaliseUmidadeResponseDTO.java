package com.searchagro.dto;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.searchagro.entities.PontoIndiceRadar;
import com.searchagro.entities.PontoUmidade;

public class AnaliseUmidadeResponseDTO {

    private LocalDate dataReferencia;
    private double resolucaoEspacialKm;
    private String produto;
    private String unidade;
    private boolean classificacaoDisponivel;
    private String aviso;
    private List<PontoUmidade> pontos;
    private List<PontoIndiceRadar> pontosIndiceRadar = Collections.emptyList();
    private double resolucaoRadarMetros = 316.227766;
    private String periodoRadar;
    private String avisoRadar;
    private Map<String, Long> temposConsultaMs = Collections.emptyMap();

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

    public List<PontoIndiceRadar> getPontosIndiceRadar() {
        return pontosIndiceRadar;
    }

    public void setPontosIndiceRadar(List<PontoIndiceRadar> pontosIndiceRadar) {
        this.pontosIndiceRadar = pontosIndiceRadar == null
                ? Collections.emptyList()
                : pontosIndiceRadar;
    }

    public double getResolucaoRadarMetros() {
        return resolucaoRadarMetros;
    }

    public void setResolucaoRadarMetros(double resolucaoRadarMetros) {
        this.resolucaoRadarMetros = resolucaoRadarMetros;
    }

    public String getPeriodoRadar() {
        return periodoRadar;
    }

    public void setPeriodoRadar(String periodoRadar) {
        this.periodoRadar = periodoRadar;
    }

    public String getAvisoRadar() {
        return avisoRadar;
    }

    public void setAvisoRadar(String avisoRadar) {
        this.avisoRadar = avisoRadar;
    }

    public Map<String, Long> getTemposConsultaMs() {
        return temposConsultaMs;
    }

    public void setTemposConsultaMs(Map<String, Long> temposConsultaMs) {
        this.temposConsultaMs = temposConsultaMs == null ? Collections.emptyMap() : temposConsultaMs;
    }
}

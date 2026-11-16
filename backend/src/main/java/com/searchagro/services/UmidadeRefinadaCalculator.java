package com.searchagro.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.searchagro.entities.PontoUmidade;
import com.searchagro.exception.IntegracaoException;

@Component
public class UmidadeRefinadaCalculator {

    private final Double lstMinC;
    private final Double coefficientA;
    private final Double coefficientB;

    public UmidadeRefinadaCalculator(
            @Value("${nasa.tvdi.lst-min-c:}") String lstMinC,
            @Value("${nasa.tvdi.coefficient-a-c:}") String coefficientA,
            @Value("${nasa.tvdi.coefficient-b-c:}") String coefficientB) {
        boolean hasLstMin = !lstMinC.trim().isEmpty();
        boolean hasCoefficientA = !coefficientA.trim().isEmpty();
        boolean hasCoefficientB = !coefficientB.trim().isEmpty();
        if (hasLstMin != hasCoefficientA || hasLstMin != hasCoefficientB) {
            throw new IllegalArgumentException(
                    "Configure NASA_TVDI_LST_MIN_C, NASA_TVDI_A_C e NASA_TVDI_B_C em conjunto.");
        }

        this.lstMinC = hasLstMin ? parseFinite("NASA_TVDI_LST_MIN_C", lstMinC) : null;
        this.coefficientA = hasCoefficientA ? parseFinite("NASA_TVDI_A_C", coefficientA) : null;
        this.coefficientB = hasCoefficientB ? parseFinite("NASA_TVDI_B_C", coefficientB) : null;
    }

    public boolean isConfigured() {
        return lstMinC != null;
    }

    public void calculate(PontoUmidade point) {
        if (!isConfigured() || point.getNdvi() == null || point.getTemperaturaSuperficieC() == null) {
            return;
        }

        double lstMaxC = coefficientA + coefficientB * point.getNdvi();
        double temperatureRange = lstMaxC - lstMinC;
        if (!Double.isFinite(temperatureRange) || temperatureRange <= 0.0) {
            throw new IntegracaoException(
                    "Os parâmetros NASA_TVDI configurados produzem LSTmax menor ou igual a LSTmin "
                            + "para um dos pontos. Revise os coeficientes.",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        double drynessIndex = (point.getTemperaturaSuperficieC() - lstMinC) / temperatureRange;
        double refinedMoisture = point.getUmidadeVolumetrica() * (1.0 - drynessIndex);
        if (!Double.isFinite(drynessIndex) || !Double.isFinite(refinedMoisture)) {
            throw new IntegracaoException(
                    "Não foi possível calcular a umidade refinada com os dados do ponto.",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        point.setIndiceSecuraAproximado(drynessIndex);
        point.setUmidadeRefinada(refinedMoisture);
    }

    private double parseFinite(String name, String value) {
        try {
            double parsed = Double.parseDouble(value.trim());
            if (!Double.isFinite(parsed)) {
                throw new NumberFormatException("not finite");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " deve conter um número finito.", exception);
        }
    }
}

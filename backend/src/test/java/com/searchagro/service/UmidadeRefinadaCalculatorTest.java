package com.searchagro.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.searchagro.entities.PontoUmidade;
import com.searchagro.exception.IntegracaoException;
import com.searchagro.services.UmidadeRefinadaCalculator;

class UmidadeRefinadaCalculatorTest {

    @Test
    void calculatesTheProposedDrynessIndexAndRefinedMoisture() {
        UmidadeRefinadaCalculator calculator = new UmidadeRefinadaCalculator("20", "30", "10");
        PontoUmidade point = point(0.2, 0.5, 27.5);

        calculator.calculate(point);

        assertEquals(0.5, point.getIndiceSecuraAproximado(), 1e-9);
        assertEquals(0.1, point.getUmidadeRefinada(), 1e-9);
    }

    @Test
    void leavesRefinedMoistureUnavailableWhenParametersAreNotConfigured() {
        UmidadeRefinadaCalculator calculator = new UmidadeRefinadaCalculator("", "", "");
        PontoUmidade point = point(0.2, 0.5, 27.5);

        calculator.calculate(point);

        assertNull(point.getIndiceSecuraAproximado());
        assertNull(point.getUmidadeRefinada());
    }

    @Test
    void rejectsCoefficientsThatProduceAnInvalidTemperatureRange() {
        UmidadeRefinadaCalculator calculator = new UmidadeRefinadaCalculator("30", "20", "10");

        IntegracaoException exception = assertThrows(
                IntegracaoException.class,
                () -> calculator.calculate(point(0.2, 0.5, 27.5)));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, exception.getHttpStatus());
    }

    private PontoUmidade point(double moisture, double ndvi, double temperatureC) {
        PontoUmidade point = new PontoUmidade();
        point.setUmidadeVolumetrica(moisture);
        point.setNdvi(ndvi);
        point.setTemperaturaSuperficieC(temperatureC);
        return point;
    }
}

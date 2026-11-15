package com.searchagro.conexao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.searchagro.conexao.HttpClientConfig.IntegracaoProperties;
import com.searchagro.exception.IntegracaoException;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;

class AppEearsClientTest {

    @Test
    void requiresEarthdataCredentialsBeforeSubmittingSmapTask() {
        IntegracaoProperties properties = new IntegracaoProperties(
                "AgroSearch-Test/1.0",
                Duration.ofSeconds(1),
                "",
                "",
                Duration.ofSeconds(1));
        AppEearsClient client = new AppEearsClient(
                HttpClient.newHttpClient(),
                new ObjectMapper(),
                properties,
                "",
                "");

        IntegracaoException exception = assertThrows(
                IntegracaoException.class,
                () -> client.downloadSurfaceMoisture(squarePolygon(), LocalDate.of(2026, 11, 14)));

        assertEquals(503, exception.getHttpStatus().value());
    }

    private Polygon squarePolygon() {
        GeometryFactory geometryFactory = new GeometryFactory();
        LinearRing ring = geometryFactory.createLinearRing(new Coordinate[] {
                new Coordinate(-49.25, -16.68),
                new Coordinate(-49.24, -16.68),
                new Coordinate(-49.24, -16.69),
                new Coordinate(-49.25, -16.69),
                new Coordinate(-49.25, -16.68)
        });
        return geometryFactory.createPolygon(ring);
    }
}

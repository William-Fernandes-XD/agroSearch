package com.searchagro.conexao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.searchagro.exception.IntegracaoException;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;

class CopernicusSentinelClientTest {

    @Test
    void buildsA316MeterSentinelGridForTheTwoWeekSmapWindow() {
        ObjectMapper mapper = new ObjectMapper();
        CopernicusSentinelClient client = new CopernicusSentinelClient(
                HttpClient.newHttpClient(),
                mapper,
                "test-client",
                "test-secret",
                Duration.ofSeconds(1));

        ObjectNode request = client.buildProcessRequest(
                squarePolygon().getEnvelopeInternal(),
                7,
                5,
                LocalDate.of(2026, 11, 14));

        assertEquals(7, request.path("output").path("width").asInt());
        assertEquals(5, request.path("output").path("height").asInt());
        assertEquals(
                "sentinel-1-grd",
                request.path("input").path("data").get(0).path("type").asText());
        assertEquals(
                "2026-10-30T00:00:00Z",
                request.path("input").path("data").get(0)
                        .path("dataFilter").path("timeRange").path("from").asText());
        assertEquals(
                "2026-11-15T23:59:59Z",
                request.path("input").path("data").get(0)
                        .path("dataFilter").path("timeRange").path("to").asText());
        assertTrue(request.path("evalscript").asText().contains("sample.VV"));
        assertTrue(request.path("evalscript").asText().contains("sample.dataMask"));
        assertTrue(request.path("evalscript").asText().contains("LINEAR_POWER"));
    }

    @Test
    void sizesEachRadarCellToNoMoreThanApproximatelyTenHectares() {
        CopernicusSentinelClient client = new CopernicusSentinelClient(
                HttpClient.newHttpClient(),
                new ObjectMapper(),
                "test-client",
                "test-secret",
                Duration.ofSeconds(1));

        int[] dimensions = client.gridDimensions(squarePolygon().getEnvelopeInternal());

        assertEquals(4, dimensions[0]);
        assertEquals(4, dimensions[1]);
    }

    @Test
    void refusesToCallCopernicusWithoutLocalOAuthCredentials() {
        CopernicusSentinelClient client = new CopernicusSentinelClient(
                HttpClient.newHttpClient(),
                new ObjectMapper(),
                "",
                "",
                Duration.ofSeconds(1));

        IntegracaoException exception = assertThrows(
                IntegracaoException.class,
                () -> client.downloadBackscatter(squarePolygon(), LocalDate.of(2026, 11, 14)));

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

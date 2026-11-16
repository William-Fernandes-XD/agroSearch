package com.searchagro.conexao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.searchagro.conexao.HttpClientConfig.IntegracaoProperties;
import com.searchagro.entities.PontoUmidade;
import com.searchagro.exception.IntegracaoException;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;

class AppEearsClientTest {

    private final IntegracaoProperties properties = new IntegracaoProperties(
            "AgroSearch-Test/1.0",
            Duration.ofSeconds(1),
            "",
            "",
            Duration.ofSeconds(1));

    @Test
    void parsesScaledNdviOnlyForTheRequestedLatestCompositeDate() {
        AppEearsClient client = createClient();
        List<PontoUmidade> points = List.of(new PontoUmidade());
        Map<Integer, Double> observations = new HashMap<>();
        Map<Integer, LocalDate> dates = new HashMap<>();
        String csv = "id,Date,Latitude,Longitude,MOD13Q1.061__250m_16_days_NDVI\n"
                + "p-0,2023-10-08,-16.68,-49.25,6000\n"
                + "p-0,2023-10-16,-16.68,-49.25,6500\n"
                + "p-0,2023-10-01,-16.68,-49.25,7000\n";

        client.parseNdviCsv(
                csv.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "ndvi.csv",
                points,
                LocalDate.of(2023, 10, 8),
                observations,
                dates);

        assertEquals(0.6, observations.get(0), 1e-9);
        assertEquals(LocalDate.of(2023, 10, 8), dates.get(0));
    }

    @Test
    void rejectsNdviRowsThatCannotBeAssociatedWithRequestedPoints() {
        AppEearsClient client = createClient();
        List<PontoUmidade> points = List.of(new PontoUmidade(), new PontoUmidade());
        String csv = "Date,MOD13Q1.061__250m_16_days_NDVI\n"
                + "2023-10-08,6500\n";

        assertThrows(
                IntegracaoException.class,
                () -> client.parseNdviCsv(
                        csv.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        "ndvi.csv",
                        points,
                        LocalDate.of(2023, 10, 8),
                        new HashMap<>(),
                        new HashMap<>()));
    }

    @Test
    void requiresEarthdataCredentialsBeforeSubmittingSmapTask() {
        AppEearsClient client = createClient();
        IntegracaoException exception = assertThrows(
                IntegracaoException.class,
                () -> client.downloadSurfaceMoisture(squarePolygon(), LocalDate.of(2026, 11, 14)));

        assertEquals(503, exception.getHttpStatus().value());
    }

    @Test
    void recognizesSmapRasterNamesWithoutDependingOnCaseOrFileType() {
        assertTrue(AppEearsClient.isGeoTiff("SPL3SMP_E.006_AM.TIFF", ""));
        assertTrue(AppEearsClient.isGeoTiff("SPL3SMP_E.006_PM.bin", "GeoTIFF"));
        assertTrue(AppEearsClient.isGeoTiff("SPL3SMP_E.006_PM.bin", "image/tiff; application=geotiff"));
        assertEquals(
                AppEearsClient.SmapLayer.MORNING,
                AppEearsClient.identifySmapLayer(
                        "SPL3SMP_E.006_Soil_Moisture_Retrieval_Data_AM_soil_moisture.tif"));
        assertEquals(
                AppEearsClient.SmapLayer.AFTERNOON,
                AppEearsClient.identifySmapLayer(
                        "SPL3SMP_E.006_Soil_Moisture_Retrieval_Data_PM_soil_moisture_pm.tif"));
        assertEquals(
                AppEearsClient.SmapLayer.UNKNOWN,
                AppEearsClient.identifySmapLayer("unrelated-raster.tif"));
    }

    private AppEearsClient createClient() {
        return new AppEearsClient(
                HttpClient.newHttpClient(),
                new ObjectMapper(),
                properties,
                "",
                "");
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

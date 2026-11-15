package com.searchagro.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Polygon;

import com.searchagro.dto.PoligonoDTO;
import com.searchagro.entities.CoordenadaGeografica;
import com.searchagro.exception.IntegracaoException;
import com.searchagro.utils.PoligonoUtils;

class PoligonoUtilsTest {

    private final PoligonoUtils poligonoUtils = new PoligonoUtils();

    @Test
    void createsValidPolygonFromLatitudeLongitudeCoordinates() {
        PoligonoDTO request = polygon(Arrays.asList(
                new CoordenadaGeografica(-16.68, -49.25),
                new CoordenadaGeografica(-16.68, -49.24),
                new CoordenadaGeografica(-16.69, -49.24),
                new CoordenadaGeografica(-16.69, -49.25),
                new CoordenadaGeografica(-16.68, -49.25)));

        Polygon polygon = poligonoUtils.criarGeometria(request);

        assertTrue(polygon.isValid());
        assertTrue(polygon.getEnvelopeInternal().contains(-49.245, -16.685));
    }

    @Test
    void rejectsPolygonThatIsNotClosed() {
        PoligonoDTO request = polygon(Arrays.asList(
                new CoordenadaGeografica(-16.68, -49.25),
                new CoordenadaGeografica(-16.68, -49.24),
                new CoordenadaGeografica(-16.69, -49.24),
                new CoordenadaGeografica(-16.69, -49.25)));

        assertThrows(IntegracaoException.class, () -> poligonoUtils.criarGeometria(request));
    }

    @Test
    void rejectsEmptyPolygon() {
        PoligonoDTO request = polygon(Collections.emptyList());

        assertThrows(IntegracaoException.class, () -> poligonoUtils.criarGeometria(request));
    }

    private PoligonoDTO polygon(java.util.List<CoordenadaGeografica> coordinates) {
        PoligonoDTO polygon = new PoligonoDTO();
        polygon.setCoordenadas(coordinates);
        return polygon;
    }
}

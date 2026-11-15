package com.searchagro.utils;

import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.searchagro.dto.PoligonoDTO;
import com.searchagro.entities.CoordenadaGeografica;
import com.searchagro.exception.IntegracaoException;

@Component
public class PoligonoUtils {

    private final GeometryFactory geometryFactory = new GeometryFactory();

    public Polygon criarGeometria(PoligonoDTO poligonoDTO) {
        if (poligonoDTO == null || poligonoDTO.getCoordenadas() == null) {
            throw new IntegracaoException("O polígono é obrigatório.", HttpStatus.BAD_REQUEST);
        }

        List<CoordenadaGeografica> vertices = poligonoDTO.getCoordenadas();
        if (vertices.size() < 4 || vertices.size() > 500) {
            throw new IntegracaoException(
                    "O polígono precisa ter entre 4 e 500 coordenadas, incluindo o fechamento.",
                    HttpStatus.BAD_REQUEST);
        }

        Coordinate[] coordinates = new Coordinate[vertices.size()];
        for (int index = 0; index < vertices.size(); index++) {
            CoordenadaGeografica vertex = vertices.get(index);
            if (vertex == null
                    || vertex.getLatitude() == null
                    || vertex.getLongitude() == null
                    || !Double.isFinite(vertex.getLatitude())
                    || !Double.isFinite(vertex.getLongitude())
                    || vertex.getLatitude() < -90
                    || vertex.getLatitude() > 90
                    || vertex.getLongitude() < -180
                    || vertex.getLongitude() > 180) {
                throw new IntegracaoException(
                        "O polígono contém coordenadas geográficas inválidas.",
                        HttpStatus.BAD_REQUEST);
            }
            coordinates[index] = new Coordinate(vertex.getLongitude(), vertex.getLatitude());
        }

        if (!coordinates[0].equals2D(coordinates[coordinates.length - 1])) {
            throw new IntegracaoException(
                    "A última coordenada deve fechar o polígono repetindo a primeira.",
                    HttpStatus.BAD_REQUEST);
        }

        LinearRing ring = geometryFactory.createLinearRing(coordinates);
        Polygon polygon = geometryFactory.createPolygon(ring);
        if (!polygon.isValid() || polygon.isEmpty() || polygon.getArea() == 0) {
            throw new IntegracaoException("O desenho não forma um polígono válido.", HttpStatus.BAD_REQUEST);
        }
        return polygon;
    }
}

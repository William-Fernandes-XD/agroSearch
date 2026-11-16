package com.searchagro.services;

import java.awt.Rectangle;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.geotools.api.geometry.Position;
import org.geotools.api.referencing.cs.AxisDirection;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridCoordinates2D;
import org.geotools.coverage.grid.GridGeometry2D;
import org.geotools.gce.geotiff.GeoTiffReader;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.searchagro.entities.PontoIndiceRadar;
import com.searchagro.exception.IntegracaoException;

@Service
public class ProcessadorSentinelRadarService {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    public List<PontoIndiceRadar> processar(byte[] geoTiff, Geometry polygon) {
        File temporaryFile = null;
        GeoTiffReader reader = null;
        GridCoverage2D coverage = null;
        try {
            temporaryFile = File.createTempFile("agrosearch-sentinel1-", ".tif");
            Files.write(temporaryFile.toPath(), geoTiff);
            reader = new GeoTiffReader(temporaryFile);
            coverage = reader.read(null);
            if (coverage == null || coverage.getCoordinateReferenceSystem() == null) {
                throw unavailable("O GeoTIFF Sentinel-1 não contém um sistema de coordenadas válido.");
            }
            RenderedImage image = coverage.getRenderedImage();
            if (image.getSampleModel().getNumBands() < 2) {
                throw unavailable("O GeoTIFF Sentinel-1 não contém retorno VV e máscara de dados.");
            }

            PreparedGeometry preparedPolygon = PreparedGeometryFactory.prepare(polygon);
            GridGeometry2D gridGeometry = coverage.getGridGeometry();
            Rectangle bounds = new Rectangle(
                    image.getMinX(),
                    image.getMinY(),
                    image.getWidth(),
                    image.getHeight());
            Raster raster = image.getData(bounds);
            boolean latitudeFirst = isLatitudeFirst(coverage);
            List<RawRadarPoint> rawPoints = new ArrayList<>();
            for (int y = raster.getMinY(); y < raster.getMinY() + raster.getHeight(); y++) {
                for (int x = raster.getMinX(); x < raster.getMinX() + raster.getWidth(); x++) {
                    double backscatterDb = raster.getSampleDouble(x, y, 0);
                    double dataMask = raster.getSampleDouble(x, y, 1);
                    if (dataMask <= 0 || !Double.isFinite(backscatterDb)
                            || backscatterDb < -50 || backscatterDb > 10) {
                        continue;
                    }
                    Position position = gridGeometry.gridToWorld(new GridCoordinates2D(x, y));
                    double firstAxis = position.getOrdinate(0);
                    double secondAxis = position.getOrdinate(1);
                    double longitude = latitudeFirst ? secondAxis : firstAxis;
                    double latitude = latitudeFirst ? firstAxis : secondAxis;
                    if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                            || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) {
                        continue;
                    }
                    Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
                    if (preparedPolygon.covers(point)) {
                        rawPoints.add(new RawRadarPoint(latitude, longitude, backscatterDb));
                    }
                }
            }
            if (rawPoints.isEmpty()) {
                throw unavailable("O Sentinel-1 não retornou células válidas dentro da área "
                        + "no período de 15 dias anterior à data SMAP.");
            }

            List<Double> sortedValues = new ArrayList<>();
            for (RawRadarPoint point : rawPoints) {
                sortedValues.add(point.backscatterDb);
            }
            Collections.sort(sortedValues);
            double low = percentile(sortedValues, 0.10);
            double high = percentile(sortedValues, 0.90);
            List<PontoIndiceRadar> points = new ArrayList<>(rawPoints.size());
            for (RawRadarPoint point : rawPoints) {
                double index = high <= low
                        ? 0.5
                        : Math.max(0.0, Math.min(1.0, (point.backscatterDb - low) / (high - low)));
                points.add(new PontoIndiceRadar(
                        point.latitude,
                        point.longitude,
                        point.backscatterDb,
                        index));
            }
            return points;
        } catch (IOException | org.geotools.api.referencing.operation.TransformException exception) {
            throw new IntegracaoException(
                    "Não foi possível ler o GeoTIFF experimental Sentinel-1: " + exception.getMessage(),
                    HttpStatus.BAD_GATEWAY);
        } finally {
            if (coverage != null) {
                coverage.dispose(true);
            }
            if (reader != null) {
                reader.dispose();
            }
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile.toPath());
                } catch (IOException exception) {
                    throw new IntegracaoException(
                            "Não foi possível remover o arquivo temporário Sentinel-1: "
                                    + exception.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR);
                }
            }
        }
    }

    private double percentile(List<Double> values, double percentile) {
        int index = (int) Math.round((values.size() - 1) * percentile);
        return values.get(index);
    }

    private boolean isLatitudeFirst(GridCoverage2D coverage) {
        AxisDirection firstAxisDirection = coverage.getCoordinateReferenceSystem()
                .getCoordinateSystem()
                .getAxis(0)
                .getDirection();
        return firstAxisDirection == AxisDirection.NORTH || firstAxisDirection == AxisDirection.SOUTH;
    }

    private IntegracaoException unavailable(String message) {
        return new IntegracaoException(message, HttpStatus.BAD_GATEWAY);
    }

    private static class RawRadarPoint {

        private final double latitude;
        private final double longitude;
        private final double backscatterDb;

        private RawRadarPoint(double latitude, double longitude, double backscatterDb) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.backscatterDb = backscatterDb;
        }
    }
}

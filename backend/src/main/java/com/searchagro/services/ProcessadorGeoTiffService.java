package com.searchagro.services;

import java.awt.Rectangle;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.geotools.api.geometry.Position;
import org.geotools.api.referencing.cs.AxisDirection;
import org.geotools.api.referencing.operation.TransformException;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridCoordinates2D;
import org.geotools.coverage.grid.GridGeometry2D;
import org.geotools.gce.geotiff.GeoTiffReader;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.searchagro.entities.PontoUmidade;
import com.searchagro.conexao.SmapRasterData;
import com.searchagro.enums.ClassificacaoUmidade;
import com.searchagro.enums.NivelAtencao;
import com.searchagro.exception.IntegracaoException;

@Service
public class ProcessadorGeoTiffService {

    private static final double MIN_VALID_MOISTURE = 0.02;
    private static final double MAX_VALID_MOISTURE = 0.5;
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    public ResultadoProcessamentoGeoTiff processar(byte[] geoTiff, Geometry polygon) {
        return processar(new SmapRasterData(geoTiff, null), polygon);
    }

    public ResultadoProcessamentoGeoTiff processar(SmapRasterData rasters, Geometry polygon) {
        File temporaryFile = null;
        GeoTiffReader reader = null;
        GridCoverage2D coverage = null;
        File afternoonTemporaryFile = null;
        GeoTiffReader afternoonReader = null;
        GridCoverage2D afternoonCoverage = null;
        try {
            byte[] primaryRaster = rasters.getMorning() != null
                    ? rasters.getMorning()
                    : rasters.getAfternoon();
            if (primaryRaster == null) {
                throw new IntegracaoException(
                        "O AppEEARS não forneceu GeoTIFF SMAP válido para o processamento.",
                        HttpStatus.BAD_GATEWAY);
            }
            temporaryFile = File.createTempFile("agrosearch-smap-am-", ".tif");
            Files.write(temporaryFile.toPath(), primaryRaster);
            reader = new GeoTiffReader(temporaryFile);
            coverage = reader.read(null);

            if (rasters.getMorning() != null && rasters.getAfternoon() != null) {
                afternoonTemporaryFile = File.createTempFile("agrosearch-smap-pm-", ".tif");
                Files.write(afternoonTemporaryFile.toPath(), rasters.getAfternoon());
                afternoonReader = new GeoTiffReader(afternoonTemporaryFile);
                afternoonCoverage = afternoonReader.read(null);
            }

            if (coverage == null || coverage.getCoordinateReferenceSystem() == null) {
                throw new IntegracaoException(
                        "O GeoTIFF SMAP não contém um sistema de coordenadas válido.",
                        HttpStatus.BAD_GATEWAY);
            }

            PreparedGeometry preparedPolygon = PreparedGeometryFactory.prepare(polygon);
            GridGeometry2D gridGeometry = coverage.getGridGeometry();
            RenderedImage image = coverage.getRenderedImage();
            RenderedImage afternoonImage = afternoonCoverage == null
                    ? null
                    : afternoonCoverage.getRenderedImage();
            if (afternoonImage != null
                    && (image.getWidth() != afternoonImage.getWidth()
                            || image.getHeight() != afternoonImage.getHeight())) {
                throw new IntegracaoException(
                        "Os rasters SMAP da manhã e da tarde não estão alinhados.",
                        HttpStatus.BAD_GATEWAY);
            }
            boolean latitudeFirst = isLatitudeFirst(coverage);
            Envelope envelope = polygon.getEnvelopeInternal();
            double minFirstAxis = latitudeFirst ? envelope.getMinY() : envelope.getMinX();
            double maxFirstAxis = latitudeFirst ? envelope.getMaxY() : envelope.getMaxX();
            double minSecondAxis = latitudeFirst ? envelope.getMinX() : envelope.getMinY();
            double maxSecondAxis = latitudeFirst ? envelope.getMaxX() : envelope.getMaxY();
            ReferencedEnvelope worldBounds = new ReferencedEnvelope(
                    minFirstAxis,
                    maxFirstAxis,
                    minSecondAxis,
                    maxSecondAxis,
                    coverage.getCoordinateReferenceSystem());
            Rectangle rasterBounds = new Rectangle(
                    image.getMinX(),
                    image.getMinY(),
                    image.getWidth(),
                    image.getHeight());
            Rectangle scanBounds = gridGeometry.worldToGrid(worldBounds).intersection(rasterBounds);
            if (scanBounds.isEmpty()) {
                return new ResultadoProcessamentoGeoTiff(new ArrayList<>(), 0, 0);
            }
            scanBounds.grow(1, 1);
            scanBounds = scanBounds.intersection(rasterBounds);
            Raster raster = image.getData(scanBounds);
            Raster afternoonRaster = afternoonImage == null ? null : afternoonImage.getData(scanBounds);
            List<PontoUmidade> points = new ArrayList<>();
            long cellsInPolygon = 0;
            long validCells = 0;
            long afternoonCells = 0;

            for (int y = raster.getMinY(); y < raster.getMinY() + raster.getHeight(); y++) {
                for (int x = raster.getMinX(); x < raster.getMinX() + raster.getWidth(); x++) {
                    double soilMoisture = raster.getSampleDouble(x, y, 0);
                    double afternoonMoisture = afternoonRaster == null
                            ? Double.NaN
                            : afternoonRaster.getSampleDouble(x, y, 0);
                    Position worldPosition = gridGeometry.gridToWorld(new GridCoordinates2D(x, y));
                    double firstAxis = worldPosition.getOrdinate(0);
                    double secondAxis = worldPosition.getOrdinate(1);
                    double longitude = latitudeFirst ? secondAxis : firstAxis;
                    double latitude = latitudeFirst ? firstAxis : secondAxis;
                    if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                            || Math.abs(latitude) > 90 || Math.abs(longitude) > 180
                            || !envelope.contains(longitude, latitude)) {
                        continue;
                    }

                    Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
                    if (preparedPolygon.covers(point)) {
                        cellsInPolygon++;
                        boolean morningValid = isValidMoisture(soilMoisture);
                        boolean afternoonValid = isValidMoisture(afternoonMoisture);
                        if (morningValid || afternoonValid) {
                            validCells++;
                            double selectedMoisture = soilMoisture;
                            if (!morningValid) {
                                selectedMoisture = afternoonMoisture;
                                afternoonCells++;
                            }
                            points.add(new PontoUmidade(
                                    latitude,
                                    longitude,
                                    selectedMoisture,
                                    null,
                                    ClassificacaoUmidade.SEM_CLASSIFICACAO,
                                    NivelAtencao.NAO_CLASSIFICADO));
                        }
                    }
                }
            }
            return new ResultadoProcessamentoGeoTiff(points, cellsInPolygon, validCells, afternoonCells);
        } catch (IOException | TransformException exception) {
            throw new IntegracaoException(
                    "Não foi possível interpretar o GeoTIFF de umidade do SMAP.",
                    HttpStatus.BAD_GATEWAY,
                    exception);
        } finally {
            if (coverage != null) {
                coverage.dispose(true);
            }
            if (afternoonCoverage != null) {
                afternoonCoverage.dispose(true);
            }
            if (reader != null) {
                reader.dispose();
            }
            if (afternoonReader != null) {
                afternoonReader.dispose();
            }
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile.toPath());
                } catch (IOException exception) {
                    temporaryFile.deleteOnExit();
                }
            }
            if (afternoonTemporaryFile != null) {
                try {
                    Files.deleteIfExists(afternoonTemporaryFile.toPath());
                } catch (IOException exception) {
                    afternoonTemporaryFile.deleteOnExit();
                }
            }
        }
    }

    private boolean isValidMoisture(double moisture) {
        return Double.isFinite(moisture)
                && moisture >= MIN_VALID_MOISTURE
                && moisture <= MAX_VALID_MOISTURE;
    }

    private boolean isLatitudeFirst(GridCoverage2D coverage) {
        AxisDirection firstAxis = coverage.getCoordinateReferenceSystem()
                .getCoordinateSystem()
                .getAxis(0)
                .getDirection();
        return AxisDirection.NORTH.equals(firstAxis) || AxisDirection.SOUTH.equals(firstAxis);
    }
}

package com.searchagro.conexao;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Polygon;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.searchagro.exception.IntegracaoException;

@Component
public class CopernicusSentinelClient {

    private static final String TOKEN_URL =
            "https://identity.dataspace.copernicus.eu/auth/realms/CDSE/protocol/openid-connect/token";
    private static final String PROCESS_URL = "https://sh.dataspace.copernicus.eu/api/v1/process";
    private static final double TARGET_CELL_METERS = 316.227766;
    private static final int MAX_GRID_CELLS = 100_000;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String clientId;
    private final String clientSecret;
    private final Duration responseTimeout;
    private String accessToken;
    private Instant tokenExpiresAt = Instant.EPOCH;

    public CopernicusSentinelClient(
            HttpClient geospatialHttpClient,
            ObjectMapper objectMapper,
            @Value("${copernicus.client-id:}") String clientId,
            @Value("${copernicus.client-secret:}") String clientSecret,
            @Value("${integracao.response-timeout:30s}") Duration responseTimeout) {
        this.httpClient = geospatialHttpClient;
        this.objectMapper = objectMapper;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.responseTimeout = responseTimeout;
    }

    public boolean isConfigured() {
        return !clientId.isBlank() && !clientSecret.isBlank();
    }

    public byte[] downloadBackscatter(Polygon polygon, LocalDate referenceDate) {
        if (!isConfigured()) {
            throw unavailable(
                    "Sentinel-1 desativado: configure COPERNICUS_CLIENT_ID e "
                            + "COPERNICUS_CLIENT_SECRET no .env local.");
        }

        Envelope bounds = polygon.getEnvelopeInternal();
        int[] dimensions = gridDimensions(bounds);
        int width = dimensions[0];
        int height = dimensions[1];
        if (width > 2500 || height > 2500 || (long) width * height > MAX_GRID_CELLS) {
            throw new IntegracaoException(
                    "A área é grande demais para a grade experimental Sentinel-1 de aproximadamente "
                            + "10 hectares por célula. Reduza o polígono e tente novamente.",
                    HttpStatus.PAYLOAD_TOO_LARGE);
        }

        try {
            ObjectNode requestBody = buildProcessRequest(bounds, width, height, referenceDate);
            HttpRequest request = HttpRequest.newBuilder(URI.create(PROCESS_URL))
                    .timeout(responseTimeout.multipliedBy(3))
                    .header("Authorization", "Bearer " + getAccessToken())
                    .header("Content-Type", "application/json")
                    .header("Accept", "image/tiff")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(
                            objectMapper.writeValueAsBytes(requestBody)))
                    .build();
            HttpResponse<byte[]> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw unavailable("O Copernicus Sentinel Hub respondeu HTTP "
                        + response.statusCode() + " ao buscar o radar Sentinel-1.");
            }
            if (response.body().length < 4
                    || !(response.body()[0] == 'I' && response.body()[1] == 'I')
                            && !(response.body()[0] == 'M' && response.body()[1] == 'M')) {
                throw unavailable(
                        "O Copernicus Sentinel Hub não retornou um GeoTIFF Sentinel-1 válido para a área.");
            }
            return response.body();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable("A consulta Sentinel-1 foi interrompida.");
        } catch (IOException exception) {
            throw unavailable("Falha de comunicação com o Copernicus Sentinel Hub: "
                    + exception.getMessage());
        }
    }

    int[] gridDimensions(Envelope bounds) {
        double centerLatitude = (bounds.getMinY() + bounds.getMaxY()) / 2.0;
        double cellLatitudeDegrees = TARGET_CELL_METERS / 110_574.0;
        double cellLongitudeDegrees = TARGET_CELL_METERS
                / (111_320.0 * Math.max(0.01, Math.cos(Math.toRadians(centerLatitude))));
        int width = Math.max(1, (int) Math.ceil(bounds.getWidth() / cellLongitudeDegrees));
        int height = Math.max(1, (int) Math.ceil(bounds.getHeight() / cellLatitudeDegrees));
        return new int[] {width, height};
    }

    private synchronized String getAccessToken() throws IOException, InterruptedException {
        if (accessToken != null && Instant.now().isBefore(tokenExpiresAt.minusSeconds(30))) {
            return accessToken;
        }
        String form = "grant_type=client_credentials"
                + "&client_id=" + encode(clientId)
                + "&client_secret=" + encode(clientSecret);
        HttpRequest request = HttpRequest.newBuilder(URI.create(TOKEN_URL))
                .timeout(responseTimeout)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw unavailable("Não foi possível autenticar no Copernicus Data Space "
                    + "(HTTP " + response.statusCode() + "). Verifique as credenciais locais.");
        }

        JsonNode tokenResponse = objectMapper.readTree(response.body());
        accessToken = tokenResponse.path("access_token").asText("");
        long expiresIn = tokenResponse.path("expires_in").asLong(0);
        if (accessToken.isBlank() || expiresIn <= 0) {
            throw unavailable("O Copernicus Data Space retornou uma resposta de autenticação inválida.");
        }
        tokenExpiresAt = Instant.now().plusSeconds(expiresIn);
        return accessToken;
    }

    ObjectNode buildProcessRequest(
            Envelope bounds,
            int width,
            int height,
            LocalDate referenceDate) {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode input = root.putObject("input");
        ObjectNode inputBounds = input.putObject("bounds");
        ArrayNode bbox = inputBounds.putArray("bbox");
        bbox.add(bounds.getMinX()).add(bounds.getMinY()).add(bounds.getMaxX()).add(bounds.getMaxY());
        inputBounds.putObject("properties")
                .put("crs", "http://www.opengis.net/def/crs/EPSG/0/4326");

        ArrayNode data = input.putArray("data");
        ObjectNode sentinel = data.addObject();
        sentinel.put("type", "sentinel-1-grd");
        ObjectNode filter = sentinel.putObject("dataFilter");
        ObjectNode timeRange = filter.putObject("timeRange");
        timeRange.put("from", referenceDate.minusDays(15) + "T00:00:00Z");
        timeRange.put("to", referenceDate.plusDays(1) + "T23:59:59Z");
        filter.put("acquisitionMode", "IW");
        filter.put("polarization", "DV");
        filter.put("resolution", "HIGH");
        filter.put("mosaickingOrder", "mostRecent");
        sentinel.putObject("processing")
                .put("orthorectify", true)
                .put("backCoeff", "GAMMA0_TERRAIN");

        root.put("evalscript",
                "//VERSION=3\n"
                        + "function setup() {\n"
                        + "  return {\n"
                        + "    input: [{ bands: [\"VV\", \"dataMask\"], "
                        + "units: [\"LINEAR_POWER\", \"DN\"] }],\n"
                        + "    output: { bands: 2, sampleType: \"FLOAT32\" }\n"
                        + "  };\n"
                        + "}\n"
                        + "function evaluatePixel(sample) {\n"
                        + "  const db = sample.VV > 0 ? 10 * Math.log(sample.VV) / Math.LN10 : -9999;\n"
                        + "  return [db, sample.dataMask];\n"
                        + "}\n");
        ObjectNode output = root.putObject("output");
        output.put("width", width);
        output.put("height", height);
        ArrayNode responses = output.putArray("responses");
        responses.addObject()
                .put("identifier", "default")
                .putObject("format")
                .put("type", "image/tiff");
        return root;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private IntegracaoException unavailable(String message) {
        return new IntegracaoException(message, HttpStatus.SERVICE_UNAVAILABLE);
    }
}

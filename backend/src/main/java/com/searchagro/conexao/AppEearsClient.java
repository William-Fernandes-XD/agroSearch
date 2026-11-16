package com.searchagro.conexao;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Polygon;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.searchagro.conexao.HttpClientConfig.IntegracaoProperties;
import com.searchagro.conexao.SmapRasterData;
import com.searchagro.constantes.Constantes;
import com.searchagro.entities.PontoUmidade;
import com.searchagro.exception.IntegracaoException;

@Component
public class AppEearsClient {

    private static final DateTimeFormatter APPEEARS_DATE = DateTimeFormatter.ofPattern("MM-dd-yyyy");
    private static final String PRODUCT = Constantes.SMAP_PRODUCT;
    private static final String LAYER = Constantes.SMAP_SOIL_MOISTURE_LAYER;
    private static final String NDVI_PRODUCT = "MOD13Q1.061";
    private static final String NDVI_LAYER = "_250m_16_days_NDVI";
    private static final String API_BASE = Constantes.APPEEARS_API_URL;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final IntegracaoProperties properties;
    private final String username;
    private final String password;
    private final String userAgent;
    private final Duration responseTimeout;
    private final Duration taskTimeout;

    private String accessToken;

    public AppEearsClient(
            HttpClient geospatialHttpClient,
            ObjectMapper objectMapper,
            IntegracaoProperties properties,
            @Value("${nasa.earthdata.username:}") String username,
            @Value("${nasa.earthdata.password:}") String password) {
        this.httpClient = geospatialHttpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.username = username;
        this.password = password;
        this.userAgent = properties.getUserAgent();
        this.responseTimeout = properties.getResponseTimeout();
        this.taskTimeout = properties.getAppEearsTaskTimeout();
    }

    public SmapRasterData downloadSurfaceMoisture(Polygon polygon, LocalDate dataReferencia) {
        String taskId = submitAreaTask(polygon, dataReferencia);
        awaitTask(taskId, "SMAP");
        JsonNode bundle = getJson("/bundle/" + taskId);
        JsonNode files = bundle.path("files");
        if (!files.isArray()) {
            throw new IntegracaoException(
                    "O AppEEARS não retornou os arquivos do processamento SMAP.",
                    HttpStatus.BAD_GATEWAY);
        }

        byte[] morningRaster = null;
        byte[] afternoonRaster = null;
        List<JsonNode> geotiffFiles = new ArrayList<>();
        for (JsonNode file : files) {
            String filename = file.path("file_name").asText("");
            String fileId = file.path("file_id").asText("");
            if (!isGeoTiff(filename, file.path("file_type").asText("")) || fileId.isEmpty()) {
                continue;
            }
            geotiffFiles.add(file);
            SmapLayer layer = identifySmapLayer(filename);
            if (layer == SmapLayer.MORNING) {
                morningRaster = getBytes("/bundle/" + taskId + "/" + fileId);
            } else if (layer == SmapLayer.AFTERNOON) {
                afternoonRaster = getBytes("/bundle/" + taskId + "/" + fileId);
            }
        }

        if (morningRaster == null
                && afternoonRaster == null
                && geotiffFiles.size() == 1
                && geotiffFiles.get(0).path("file_name").asText("")
                        .toLowerCase(Locale.ROOT).contains(PRODUCT.toLowerCase(Locale.ROOT))) {
            JsonNode onlyRaster = geotiffFiles.get(0);
            byte[] raster = getBytes("/bundle/" + taskId + "/"
                    + onlyRaster.path("file_id").asText());
            String filename = onlyRaster.path("file_name").asText("");
            SmapLayer layer = identifySmapLayer(filename);
            if (layer == SmapLayer.AFTERNOON) {
                afternoonRaster = raster;
            } else {
                morningRaster = raster;
            }
        }

        if (morningRaster != null || afternoonRaster != null) {
            return new SmapRasterData(morningRaster, afternoonRaster);
        }

        throw new IntegracaoException(
                describeMissingSmapRasters(taskId, dataReferencia, files),
                HttpStatus.SERVICE_UNAVAILABLE);
    }

    static boolean isGeoTiff(String filename, String fileType) {
        String normalizedFilename = filename.toLowerCase(Locale.ROOT);
        String normalizedType = fileType.toLowerCase(Locale.ROOT);
        return normalizedFilename.endsWith(".tif")
                || normalizedFilename.endsWith(".tiff")
                || "tif".equals(normalizedType)
                || "tiff".equals(normalizedType)
                || "geotiff".equals(normalizedType)
                || normalizedType.startsWith("image/tiff");
    }

    static SmapLayer identifySmapLayer(String filename) {
        String normalizedFilename = filename.toLowerCase(Locale.ROOT);
        if (normalizedFilename.contains(Constantes.SMAP_PM_SOIL_MOISTURE_LAYER.toLowerCase(Locale.ROOT))) {
            return SmapLayer.AFTERNOON;
        }
        if (normalizedFilename.contains(LAYER.toLowerCase(Locale.ROOT))) {
            return SmapLayer.MORNING;
        }
        return SmapLayer.UNKNOWN;
    }

    private String describeMissingSmapRasters(
            String taskId,
            LocalDate dataReferencia,
            JsonNode files) {
        List<String> fileDescriptions = new ArrayList<>();
        if (files.isArray()) {
            for (JsonNode file : files) {
                String filename = file.path("file_name").asText("(sem nome)");
                String fileType = file.path("file_type").asText("tipo desconhecido");
                fileDescriptions.add(filename + " [" + fileType + "]");
                if (fileDescriptions.size() == 8) {
                    break;
                }
            }
        }
        String returnedFiles = fileDescriptions.isEmpty()
                ? "nenhum arquivo"
                : String.join(", ", fileDescriptions);
        return "O AppEEARS concluiu a tarefa SMAP " + taskId
                + " para " + dataReferencia
                + ", mas o bundle não contém um GeoTIFF reconhecível das camadas AM/PM solicitadas. "
                + "Arquivos listados: " + returnedFiles
                + ". Verifique se há dados SMAP para essa data e se as camadas de umidade foram "
                + "incluídas no resultado.";
    }

    enum SmapLayer {
        MORNING,
        AFTERNOON,
        UNKNOWN
    }

    public Map<Integer, Double> downloadNdvi(List<PontoUmidade> points, LocalDate dataNdvi) {
        if (points.isEmpty()) {
            return java.util.Collections.emptyMap();
        }

        String taskId = submitPointTask(points, dataNdvi);
        awaitTask(taskId, "NDVI");
        JsonNode bundle = getJson("/bundle/" + taskId);
        JsonNode files = bundle.path("files");
        if (!files.isArray()) {
            throw new IntegracaoException(
                    "O AppEEARS não retornou os arquivos da extração de NDVI.",
                    HttpStatus.BAD_GATEWAY);
        }

        Map<Integer, Double> observations = new HashMap<>();
        Map<Integer, LocalDate> observationDates = new HashMap<>();
        boolean foundCsv = false;
        for (JsonNode file : files) {
            String filename = file.path("file_name").asText("");
            String fileId = file.path("file_id").asText("");
            if (filename.toLowerCase(Locale.ROOT).endsWith(".csv") && !fileId.isEmpty()) {
                foundCsv = true;
                parseNdviCsv(
                        getBytes("/bundle/" + taskId + "/" + fileId),
                        filename,
                        points,
                        dataNdvi,
                        observations,
                        observationDates);
            }
        }
        if (!foundCsv) {
            throw new IntegracaoException(
                    "O AppEEARS concluiu a tarefa sem disponibilizar o CSV de NDVI.",
                    HttpStatus.BAD_GATEWAY);
        }
        return observations;
    }

    private String submitPointTask(List<PontoUmidade> points, LocalDate dataNdvi) {
        ObjectNode task = objectMapper.createObjectNode();
        task.put("task_name", "AgroSearch-NDVI-" + java.util.UUID.randomUUID());
        task.put("task_type", "point");

        ObjectNode params = task.putObject("params");
        ArrayNode dates = params.putArray("dates");
        ObjectNode date = dates.addObject();
        String observationDate = dataNdvi.format(APPEEARS_DATE);
        date.put("startDate", observationDate);
        date.put("endDate", observationDate);
        date.put("recurring", false);

        ArrayNode layers = params.putArray("layers");
        ObjectNode layer = layers.addObject();
        layer.put("product", NDVI_PRODUCT);
        layer.put("layer", NDVI_LAYER);

        ArrayNode coordinates = params.putArray("coordinates");
        for (int index = 0; index < points.size(); index++) {
            PontoUmidade point = points.get(index);
            ObjectNode coordinate = coordinates.addObject();
            coordinate.put("latitude", point.getLatitude());
            coordinate.put("longitude", point.getLongitude());
            coordinate.put("id", pointId(index));
        }

        JsonNode response = sendJson("POST", "/task", task, true);
        String taskId = response.path("task_id").asText("");
        if (taskId.isEmpty()) {
            throw new IntegracaoException(
                    "O AppEEARS não retornou o identificador da extração de NDVI.",
                    HttpStatus.BAD_GATEWAY);
        }
        return taskId;
    }

    void parseNdviCsv(
            byte[] csvBytes,
            String filename,
            List<PontoUmidade> points,
            LocalDate dataReferencia,
            Map<Integer, Double> observations,
            Map<Integer, LocalDate> observationDates) {
        String csv = new String(csvBytes, StandardCharsets.UTF_8);
        String[] lines = csv.split("\\R");
        if (lines.length < 2) {
            return;
        }

        List<String> headers = parseCsvLine(lines[0]);
        int dateColumn = findColumn(headers, "date");
        int ndviColumn = findColumn(headers, "ndvi");
        int idColumn = findColumn(headers, "id", "point", "site");
        int latitudeColumn = findColumn(headers, "latitude", "lat");
        int longitudeColumn = findColumn(headers, "longitude", "lon", "long");
        if (dateColumn < 0 || ndviColumn < 0) {
            throw new IntegracaoException(
                    "O AppEEARS retornou um CSV de NDVI em formato inesperado.",
                    HttpStatus.BAD_GATEWAY);
        }

        for (int lineIndex = 1; lineIndex < lines.length; lineIndex++) {
            List<String> row = parseCsvLine(lines[lineIndex]);
            if (row.size() <= Math.max(dateColumn, ndviColumn)) {
                continue;
            }

            LocalDate observationDate = parseObservationDate(row.get(dateColumn));
            if (observationDate == null || !observationDate.equals(dataReferencia)) {
                continue;
            }
            int pointIndex = findPointIndex(
                    row, idColumn, latitudeColumn, longitudeColumn, points, filename);
            if (pointIndex < 0) {
                throw new IntegracaoException(
                        "Não foi possível associar as linhas de NDVI do AppEEARS aos pontos solicitados.",
                        HttpStatus.BAD_GATEWAY);
            }

            Double ndvi = parseNdvi(row.get(ndviColumn));
            if (ndvi == null) {
                continue;
            }
            LocalDate previousDate = observationDates.get(pointIndex);
            if (previousDate == null || observationDate.isAfter(previousDate)) {
                observations.put(pointIndex, ndvi);
                observationDates.put(pointIndex, observationDate);
            }
        }
    }

    private int findPointIndex(
            List<String> row,
            int idColumn,
            int latitudeColumn,
            int longitudeColumn,
            List<PontoUmidade> points,
            String filename) {
        if (idColumn >= 0 && idColumn < row.size()) {
            String id = row.get(idColumn).trim();
            if (id.startsWith("p-")) {
                try {
                    int index = Integer.parseInt(id.substring(2));
                    if (index >= 0 && index < points.size()) {
                        return index;
                    }
                } catch (NumberFormatException ignored) {
                    // Fall through to coordinate matching.
                }
            }
        }

        if (latitudeColumn >= 0 && longitudeColumn >= 0
                && latitudeColumn < row.size() && longitudeColumn < row.size()) {
            try {
                double latitude = Double.parseDouble(row.get(latitudeColumn).trim());
                double longitude = Double.parseDouble(row.get(longitudeColumn).trim());
                for (int index = 0; index < points.size(); index++) {
                    PontoUmidade point = points.get(index);
                    if (Math.abs(point.getLatitude() - latitude) < 0.0001
                            && Math.abs(point.getLongitude() - longitude) < 0.0001) {
                        return index;
                    }
                }
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        for (int index = 0; index < points.size(); index++) {
            if (filename.contains(pointId(index))) {
                return index;
            }
        }
        return points.size() == 1 ? 0 : -1;
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char current = line.charAt(index);
            if (current == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    value.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (current == ',' && !quoted) {
                values.add(value.toString());
                value.setLength(0);
            } else {
                value.append(current);
            }
        }
        values.add(value.toString());
        return values;
    }

    private int findColumn(List<String> headers, String... names) {
        for (int index = 0; index < headers.size(); index++) {
            String header = headers.get(index).replace("\uFEFF", "").toLowerCase(Locale.ROOT);
            for (String name : names) {
                if (header.equals(name) || header.endsWith("_" + name) || header.contains(name)) {
                    return index;
                }
            }
        }
        return -1;
    }

    private LocalDate parseObservationDate(String value) {
        String date = value.trim();
        try {
            return date.length() >= 10
                    ? LocalDate.parse(date.substring(0, 10))
                    : LocalDate.parse(date, DateTimeFormatter.BASIC_ISO_DATE);
        } catch (java.time.DateTimeException exception) {
            return null;
        }
    }

    private Double parseNdvi(String value) {
        try {
            String trimmedValue = value.trim();
            double rawValue = Double.parseDouble(trimmedValue);
            boolean integerRawValue = trimmedValue.matches("-?\\d+");
            double ndvi = Math.abs(rawValue) > 1.0 || integerRawValue
                    ? rawValue * 0.0001
                    : rawValue;
            return Double.isFinite(ndvi) && ndvi >= -0.2 && ndvi <= 1.0 ? ndvi : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String pointId(int index) {
        return "p-" + index;
    }

    private String submitAreaTask(Polygon polygon, LocalDate dataReferencia) {
        ObjectNode task = objectMapper.createObjectNode();
        task.put("task_name", "AgroSearch-" + java.util.UUID.randomUUID());
        task.put("task_type", "area");

        ObjectNode params = task.putObject("params");
        params.set("geo", createFeatureCollection(polygon));
        ArrayNode dates = params.putArray("dates");
        ObjectNode date = dates.addObject();
        String formattedDate = dataReferencia.format(APPEEARS_DATE);
        date.put("startDate", formattedDate);
        date.put("endDate", formattedDate);
        date.put("recurring", false);
        date.putArray("yearRange").add(2000).add(2050);

        ArrayNode layers = params.putArray("layers");
        ObjectNode morningLayer = layers.addObject();
        morningLayer.put("product", PRODUCT);
        morningLayer.put("layer", LAYER);
        ObjectNode afternoonLayer = layers.addObject();
        afternoonLayer.put("product", PRODUCT);
        afternoonLayer.put("layer", Constantes.SMAP_PM_SOIL_MOISTURE_LAYER);

        ObjectNode output = params.putObject("output");
        output.putObject("format").put("type", "geotiff");
        output.put("projection", "geographic");

        JsonNode response = sendJson("POST", "/task", task, true);
        String taskId = response.path("task_id").asText("");
        if (taskId.isEmpty()) {
            throw new IntegracaoException(
                    "O AppEEARS não retornou o identificador do processamento.",
                    HttpStatus.BAD_GATEWAY);
        }
        return taskId;
    }

    private ObjectNode createFeatureCollection(Polygon polygon) {
        ObjectNode collection = objectMapper.createObjectNode();
        collection.put("type", "FeatureCollection");
        ArrayNode features = collection.putArray("features");
        ObjectNode feature = features.addObject();
        feature.put("type", "Feature");
        ObjectNode geometry = feature.putObject("geometry");
        geometry.put("type", "Polygon");
        ArrayNode rings = geometry.putArray("coordinates");
        ArrayNode ring = rings.addArray();
        for (Coordinate coordinate : polygon.getExteriorRing().getCoordinates()) {
            ArrayNode position = ring.addArray();
            position.add(coordinate.x);
            position.add(coordinate.y);
        }
        feature.putObject("properties");
        return collection;
    }

    private void awaitTask(String taskId, String product) {
        long deadline = System.nanoTime() + taskTimeout.toNanos();
        while (System.nanoTime() < deadline) {
            JsonNode status = getJson("/status/" + taskId);
            if (status.isArray() && !status.isEmpty()) {
                status = status.get(0);
            }

            String state = status.path("status").asText("");
            if ("done".equalsIgnoreCase(state)) {
                return;
            }
            if ("error".equalsIgnoreCase(state) || "failed".equalsIgnoreCase(state)) {
                throw new IntegracaoException(
                        "O processamento " + product + " pelo AppEEARS falhou.",
                        HttpStatus.BAD_GATEWAY);
            }
            waitBeforePolling();
        }
        throw new IntegracaoException(
                "A NASA AppEEARS ainda não concluiu o processamento " + product
                        + " dentro do limite configurado de "
                        + taskTimeout.toMinutes()
                        + " minutos. Áreas extensas podem levar mais tempo; aumente "
                        + "NASA_APPEEARS_TASK_TIMEOUT se necessário.",
                HttpStatus.GATEWAY_TIMEOUT);
    }

    private void waitBeforePolling() {
        try {
            TimeUnit.SECONDS.sleep(3);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IntegracaoException(
                    "A espera pelo processamento SMAP foi interrompida.",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    exception);
        }
    }

    private JsonNode getJson(String path) {
        try {
            HttpResponse<String> response = sendAuthorized("GET", API_BASE + path, null);
            ensureSuccess(response.statusCode(), response.body());
            return objectMapper.readTree(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IntegracaoException(
                    "A comunicação com AppEEARS foi interrompida.",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    exception);
        } catch (IOException exception) {
            throw new IntegracaoException(
                    "Não foi possível consultar o AppEEARS.",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    exception);
        }
    }

    private JsonNode sendJson(String method, String path, JsonNode body, boolean authenticated) {
        try {
            String jsonBody = body == null ? null : objectMapper.writeValueAsString(body);
            HttpResponse<String> response = authenticated
                    ? sendAuthorized(method, API_BASE + path, jsonBody)
                    : sendRequest(method, API_BASE + path, jsonBody, null);
            ensureSuccess(response.statusCode(), response.body());
            return objectMapper.readTree(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IntegracaoException(
                    "A comunicação com AppEEARS foi interrompida.",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    exception);
        } catch (IOException exception) {
            throw new IntegracaoException(
                    "Não foi possível interpretar a resposta do AppEEARS.",
                    HttpStatus.BAD_GATEWAY,
                    exception);
        }
    }

    private byte[] getBytes(String path) {
        try {
            HttpResponse<byte[]> response = sendAuthorizedBytes(API_BASE + path);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw upstreamError(response.statusCode(), new String(response.body(), StandardCharsets.UTF_8));
            }
            return response.body();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IntegracaoException(
                    "O download dos dados SMAP foi interrompido.",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    exception);
        } catch (IOException exception) {
            throw new IntegracaoException(
                    "Não foi possível baixar os dados SMAP.",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    exception);
        }
    }

    private HttpResponse<String> sendAuthorized(String method, String url, String body)
            throws IOException, InterruptedException {
        HttpResponse<String> response = sendRequest(method, url, body, getAccessToken());
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            clearAccessToken();
            response = sendRequest(method, url, body, getAccessToken());
        }
        return response;
    }

    private HttpResponse<byte[]> sendAuthorizedBytes(String url) throws IOException, InterruptedException {
        HttpResponse<byte[]> response = sendBytes(url, getAccessToken());
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            clearAccessToken();
            response = sendBytes(url, getAccessToken());
        }
        return response;
    }

    private HttpResponse<String> sendRequest(String method, String url, String body, String token)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .timeout(responseTimeout)
                .header("Accept", "application/json")
                .header("User-Agent", userAgent);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if ("POST".equals(method)) {
            request.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        } else {
            request.GET();
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<byte[]> sendBytes(String url, String token) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(responseTimeout)
                .header("Accept", "application/octet-stream")
                .header("User-Agent", userAgent)
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    private synchronized String getAccessToken() {
        if (accessToken != null) {
            return accessToken;
        }
        if (username.trim().isEmpty() || password.trim().isEmpty()) {
            throw new IntegracaoException(
                    "Configure NASA_EARTHDATA_USERNAME e NASA_EARTHDATA_PASSWORD para consultar o SMAP.",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }

        JsonNode response = login();
        accessToken = response.path("token").asText("");
        if (accessToken.isEmpty()) {
            throw new IntegracaoException(
                    "O login Earthdata não retornou um token do AppEEARS.",
                    HttpStatus.BAD_GATEWAY);
        }
        return accessToken;
    }

    private JsonNode login() {
        String basicCredentials = username + ":" + password;
        String authorization = "Basic "
                + Base64.getEncoder().encodeToString(basicCredentials.getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(API_BASE + "/login"))
                .timeout(responseTimeout)
                .header("Accept", "application/json")
                .header("User-Agent", userAgent)
                .header("Authorization", authorization)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            ensureSuccess(response.statusCode(), response.body());
            return objectMapper.readTree(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IntegracaoException(
                    "A autenticação no AppEEARS foi interrompida.",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    exception);
        } catch (IOException exception) {
            throw new IntegracaoException(
                    "Não foi possível autenticar no AppEEARS.",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    exception);
        }
    }

    private synchronized void clearAccessToken() {
        accessToken = null;
    }

    private void ensureSuccess(int statusCode, String body) {
        if (statusCode < 200 || statusCode >= 300) {
            throw upstreamError(statusCode, body);
        }
    }

    private IntegracaoException upstreamError(int statusCode, String responseBody) {
        HttpStatus responseStatus = statusCode == 401 || statusCode == 403
                ? HttpStatus.SERVICE_UNAVAILABLE
                : HttpStatus.BAD_GATEWAY;
        String details = responseBody == null ? "" : responseBody;
        if (details.length() > 300) {
            details = details.substring(0, 300);
        }
        return new IntegracaoException(
                "O AppEEARS retornou HTTP " + statusCode + (details.isEmpty() ? "." : ": " + details),
                responseStatus);
    }
}

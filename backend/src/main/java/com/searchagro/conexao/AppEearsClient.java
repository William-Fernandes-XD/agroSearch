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
import com.searchagro.constantes.Constantes;
import com.searchagro.exception.IntegracaoException;

@Component
public class AppEearsClient {

    private static final DateTimeFormatter APPEEARS_DATE = DateTimeFormatter.ofPattern("MM-dd-yyyy");
    private static final String PRODUCT = Constantes.SMAP_PRODUCT;
    private static final String LAYER = Constantes.SMAP_SOIL_MOISTURE_LAYER;
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

    public byte[] downloadSurfaceMoisture(Polygon polygon, LocalDate dataReferencia) {
        String taskId = submitAreaTask(polygon, dataReferencia);
        awaitTask(taskId);
        JsonNode bundle = getJson("/bundle/" + taskId);
        JsonNode files = bundle.path("files");
        if (!files.isArray()) {
            throw new IntegracaoException(
                    "O AppEEARS não retornou os arquivos do processamento SMAP.",
                    HttpStatus.BAD_GATEWAY);
        }

        for (JsonNode file : files) {
            String filename = file.path("file_name").asText("");
            String fileId = file.path("file_id").asText("");
            if (filename.toLowerCase(java.util.Locale.ROOT).endsWith(".tif")
                    && filename.contains(LAYER)
                    && !fileId.isEmpty()) {
                return getBytes("/bundle/" + taskId + "/" + fileId);
            }
        }

        throw new IntegracaoException(
                "O AppEEARS concluiu a consulta, mas não gerou o GeoTIFF de umidade do solo.",
                HttpStatus.SERVICE_UNAVAILABLE);
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
        ObjectNode layer = layers.addObject();
        layer.put("product", PRODUCT);
        layer.put("layer", LAYER);

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

    private void awaitTask(String taskId) {
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
                        "O processamento da área pelo AppEEARS falhou.",
                        HttpStatus.BAD_GATEWAY);
            }
            waitBeforePolling();
        }
        throw new IntegracaoException(
                "O processamento SMAP excedeu o tempo limite. Tente novamente mais tarde.",
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

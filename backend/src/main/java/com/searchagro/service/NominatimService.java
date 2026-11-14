package com.searchagro.service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.searchagro.constantes.Constantes;
import com.searchagro.config.HttpClientConfig.NominatimSettings;
import com.searchagro.exception.NominatimException;
import com.searchagro.model.GeographicLocation;

@Service
public class NominatimService {

    private static final long DEFAULT_REQUEST_INTERVAL_NANOS = Duration.ofSeconds(1).toNanos();

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String searchUrl;
    private final String userAgent;
    private final Duration responseTimeout;
    private final long requestIntervalNanos;
    private final long cacheTtlNanos;

    private long lastRequestStartedAt = System.nanoTime() - DEFAULT_REQUEST_INTERVAL_NANOS;
    private String cachedQuery;
    private List<GeographicLocation> cachedResults;
    private long cachedAt;

    public NominatimService(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            @Value("${nominatim.search-url:" + Constantes.NOMINATIM_LOCALIZACAO + "}") String searchUrl,
            NominatimSettings nominatimSettings,
            @Value("${nominatim.request-interval:1s}") Duration requestInterval,
            @Value("${nominatim.cache-ttl:5m}") Duration cacheTtl) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.searchUrl = searchUrl;
        this.userAgent = nominatimSettings.getUserAgent();
        this.responseTimeout = nominatimSettings.getResponseTimeout();
        this.requestIntervalNanos = Math.max(0, requestInterval.toNanos());
        this.cacheTtlNanos = Math.max(0, cacheTtl.toNanos());
    }

    public synchronized List<GeographicLocation> search(String query) {
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("A consulta de localização não pode estar vazia.");
        }

        long now = System.nanoTime();
        if (isCachedQuery(query, now)) {
            return new ArrayList<>(cachedResults);
        }

        waitForRequestInterval();
        lastRequestStartedAt = System.nanoTime();

        HttpRequest request = HttpRequest.newBuilder(buildSearchUri(query))
                .timeout(responseTimeout)
                .header("Accept", "application/json")
                .header("User-Agent", userAgent)
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new NominatimException(
                        "O serviço Nominatim retornou um erro HTTP.",
                        response.statusCode());
            }

            List<GeographicLocation> results = parseResults(response.body());
            cachedQuery = query;
            cachedResults = Collections.unmodifiableList(new ArrayList<>(results));
            cachedAt = System.nanoTime();
            return new ArrayList<>(results);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new NominatimException(
                    "A consulta ao serviço Nominatim foi interrompida.",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    exception);
        } catch (HttpTimeoutException exception) {
            throw new NominatimException(
                    "A consulta ao serviço Nominatim excedeu o tempo limite.",
                    HttpStatus.GATEWAY_TIMEOUT,
                    exception);
        } catch (IOException exception) {
            throw new NominatimException(
                    "Não foi possível consultar o serviço Nominatim.",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    exception);
        }
    }

    private boolean isCachedQuery(String query, long now) {
        return cachedResults != null
                && query.equals(cachedQuery)
                && cacheTtlNanos > 0
                && now - cachedAt < cacheTtlNanos;
    }

    private URI buildSearchUri(String query) {
        String separator = searchUrl.contains("?") ? "&" : "?";
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8).replace("+", "%20");
        String uri = searchUrl + separator
                + "q=" + encodedQuery
                + "&format=jsonv2"
                + "&addressdetails=1"
                + "&extratags=1"
                + "&namedetails=1"
                + "&limit=40"
                + "&accept-language=pt-BR";
        try {
            return URI.create(uri);
        } catch (IllegalArgumentException exception) {
            throw new NominatimException("A URL de consulta do Nominatim é inválida.", exception);
        }
    }

    private List<GeographicLocation> parseResults(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (root == null || !root.isArray()) {
                throw new NominatimException("A resposta do Nominatim não contém uma lista JSON.");
            }

            List<GeographicLocation> results = new ArrayList<>();
            for (JsonNode item : root) {
                if (!item.isObject()) {
                    throw new NominatimException("O Nominatim retornou um resultado em formato inválido.");
                }
                GeographicLocation location = objectMapper.treeToValue(item, GeographicLocation.class);
                validateLocation(location);
                location.setOriginalJson(item.deepCopy());
                results.add(location);
            }
            return results;
        } catch (IOException exception) {
            throw new NominatimException("Não foi possível interpretar o JSON do Nominatim.", exception);
        }
    }

    private void validateLocation(GeographicLocation location) {
        double latitude = parseCoordinate(location.getLat(), "latitude");
        double longitude = parseCoordinate(location.getLon(), "longitude");
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new NominatimException("O Nominatim retornou coordenadas fora dos limites geográficos.");
        }
        if (location.getPlaceId() <= 0 || location.getOsmId() <= 0
                || location.getDisplayName() == null || location.getDisplayName().trim().isEmpty()) {
            throw new NominatimException("O Nominatim retornou um resultado geográfico incompleto.");
        }

        JsonNode boundingBox = location.getBoundingBox();
        if (boundingBox != null && !boundingBox.isNull()) {
            if (!boundingBox.isArray() || boundingBox.size() != 4) {
                throw new NominatimException("O Nominatim retornou uma bounding box inválida.");
            }
            double south = parseCoordinate(boundingBox.get(0).asText(), "bounding box");
            double north = parseCoordinate(boundingBox.get(1).asText(), "bounding box");
            double west = parseCoordinate(boundingBox.get(2).asText(), "bounding box");
            double east = parseCoordinate(boundingBox.get(3).asText(), "bounding box");
            if (south < -90 || south > 90 || north < -90 || north > 90
                    || west < -180 || west > 180 || east < -180 || east > 180
                    || south > north) {
                throw new NominatimException("O Nominatim retornou uma bounding box fora dos limites.");
            }
        }
    }

    private double parseCoordinate(String value, String coordinateName) {
        if (value == null || value.trim().isEmpty()) {
            throw new NominatimException("O Nominatim retornou " + coordinateName + " ausente.");
        }
        try {
            double coordinate = Double.parseDouble(value);
            if (Double.isNaN(coordinate) || Double.isInfinite(coordinate)) {
                throw new NumberFormatException("Coordenada não finita.");
            }
            return coordinate;
        } catch (NumberFormatException exception) {
            throw new NominatimException("O Nominatim retornou " + coordinateName + " inválida.", exception);
        }
    }

    private void waitForRequestInterval() {
        long elapsed = System.nanoTime() - lastRequestStartedAt;
        long waitNanos = requestIntervalNanos - elapsed;
        if (waitNanos <= 0) {
            return;
        }

        try {
            TimeUnit.NANOSECONDS.sleep(waitNanos);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new NominatimException("A espera para consultar o Nominatim foi interrompida.", exception);
        }
    }
}

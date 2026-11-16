package com.searchagro.conexao;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Polygon;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.searchagro.constantes.Constantes;
import com.searchagro.exception.IntegracaoException;

@Component
public class CmrClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Duration responseTimeout;
    private LocalDate cachedDate;
    private Instant cachedAt;
    private final Map<String, CachedDate> cachedNdviDates = new HashMap<>();

    public CmrClient(
            HttpClient geospatialHttpClient,
            ObjectMapper objectMapper,
            HttpClientConfig.IntegracaoProperties properties) {
        this.httpClient = geospatialHttpClient;
        this.objectMapper = objectMapper;
        this.responseTimeout = properties.getResponseTimeout();
    }

    public synchronized LocalDate latestGranuleDate() {
        if (cachedDate != null && cachedAt.plus(Duration.ofMinutes(15)).isAfter(Instant.now())) {
            return cachedDate;
        }

        URI uri = URI.create(Constantes.CMR_GRANULE_SEARCH_URL
                + "?short_name=SPL3SMP_E&version=006&page_size=1&sort_key=-start_date");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(responseTimeout)
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IntegracaoException(
                        "O catálogo NASA CMR retornou HTTP " + response.statusCode() + ".",
                        HttpStatus.BAD_GATEWAY);
            }

            JsonNode root = objectMapper.readTree(response.body());
            if (root == null || !root.isObject()) {
                throw new IntegracaoException(
                        "O catálogo NASA CMR retornou uma resposta inválida.",
                        HttpStatus.BAD_GATEWAY);
            }
            JsonNode items = root.path("items");
            if (!items.isArray() || items.isEmpty()) {
                throw new IntegracaoException(
                        "O catálogo NASA CMR não encontrou dados SMAP disponíveis.",
                        HttpStatus.SERVICE_UNAVAILABLE);
            }

            String startDate = items.get(0)
                    .path("umm")
                    .path("TemporalExtent")
                    .path("RangeDateTime")
                    .path("BeginningDateTime")
                    .asText(null);
            if (startDate == null) {
                throw new IntegracaoException(
                        "O catálogo NASA CMR não informou a data dos dados SMAP.",
                        HttpStatus.BAD_GATEWAY);
            }

            cachedDate = Instant.parse(startDate).atZone(java.time.ZoneOffset.UTC).toLocalDate();
            cachedAt = Instant.now();
            return cachedDate;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IntegracaoException(
                    "A consulta ao catálogo NASA foi interrompida.",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    exception);
        } catch (IOException | java.time.DateTimeException exception) {
            throw new IntegracaoException(
                    "Não foi possível consultar os metadados do produto SMAP.",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    exception);
        }
    }

    public synchronized LocalDate latestNdviDate(Polygon polygon) {
        Envelope envelope = polygon.getEnvelopeInternal();
        String boundingBox = String.format(
                java.util.Locale.ROOT,
                "%.5f,%.5f,%.5f,%.5f",
                envelope.getMinX(),
                envelope.getMinY(),
                envelope.getMaxX(),
                envelope.getMaxY());
        Instant now = Instant.now();
        CachedDate cached = cachedNdviDates.get(boundingBox);
        if (cached != null && cached.cachedAt.plus(Duration.ofMinutes(15)).isAfter(now)) {
            return cached.date;
        }

        cachedNdviDates.entrySet().removeIf(entry -> entry.getValue().cachedAt.plus(Duration.ofMinutes(15))
                .isBefore(now));
        URI uri = URI.create("https://cmr.earthdata.nasa.gov/search/granules.umm_json"
                + "?short_name=MOD13Q1&version=061&page_size=1&sort_key=-start_date"
                + "&bounding_box=" + boundingBox);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(responseTimeout)
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IntegracaoException(
                        "O catálogo NASA CMR retornou HTTP " + response.statusCode()
                                + " ao consultar a data mais recente do NDVI.",
                        HttpStatus.BAD_GATEWAY);
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode items = root.path("items");
            if (!items.isArray() || items.isEmpty()) {
                throw new IntegracaoException(
                        "O catálogo NASA CMR não encontrou uma observação NDVI para a área.",
                        HttpStatus.SERVICE_UNAVAILABLE);
            }
            String startDate = items.get(0)
                    .path("umm")
                    .path("TemporalExtent")
                    .path("RangeDateTime")
                    .path("BeginningDateTime")
                    .asText(null);
            if (startDate == null) {
                throw new IntegracaoException(
                        "O catálogo NASA CMR não informou a data da observação NDVI.",
                        HttpStatus.BAD_GATEWAY);
            }

            LocalDate date = Instant.parse(startDate).atZone(java.time.ZoneOffset.UTC).toLocalDate();
            cachedNdviDates.put(boundingBox, new CachedDate(date, now));
            return date;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IntegracaoException(
                    "A consulta ao catálogo NASA para NDVI foi interrompida.",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    exception);
        } catch (IOException | java.time.DateTimeException exception) {
            throw new IntegracaoException(
                    "Não foi possível consultar a data mais recente do NDVI no catálogo NASA.",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    exception);
        }
    }

    private static class CachedDate {

        private final LocalDate date;
        private final Instant cachedAt;

        private CachedDate(LocalDate date, Instant cachedAt) {
            this.date = date;
            this.cachedAt = cachedAt;
        }
    }
}

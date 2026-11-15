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
}

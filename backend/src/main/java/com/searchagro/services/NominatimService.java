package com.searchagro.services;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
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
import com.searchagro.conexao.HttpClientConfig.IntegracaoProperties;
import com.searchagro.constantes.Constantes;
import com.searchagro.entities.Localizacao;
import com.searchagro.exception.IntegracaoException;
import com.searchagro.interfaces.LocalizacaoService;

@Service
public class NominatimService implements LocalizacaoService {

    private static final long MIN_INTERVAL_NANOS = Duration.ofSeconds(1).toNanos();

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String searchUrl;
    private final IntegracaoProperties properties;
    private long lastRequestStartedAt = System.nanoTime() - MIN_INTERVAL_NANOS;
    private String cachedQuery;
    private List<Localizacao> cachedLocations;

    public NominatimService(
            HttpClient geospatialHttpClient,
            ObjectMapper objectMapper,
            IntegracaoProperties properties,
            @Value("${nominatim.search-url:" + Constantes.NOMINATIM_SEARCH_URL + "}") String searchUrl) {
        this.httpClient = geospatialHttpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.searchUrl = searchUrl;
    }

    @Override
    public synchronized List<Localizacao> buscar(String endereco) {
        if (endereco == null || endereco.trim().isEmpty()) {
            throw new IntegracaoException("Informe um endereço para pesquisar.", HttpStatus.BAD_REQUEST);
        }
        if (endereco.equals(cachedQuery) && cachedLocations != null) {
            return new ArrayList<>(cachedLocations);
        }

        waitForRateLimit();
        lastRequestStartedAt = System.nanoTime();
        String encodedAddress = URLEncoder.encode(endereco.trim(), StandardCharsets.UTF_8);
        URI uri = URI.create(searchUrl
                + "?q=" + encodedAddress
                + "&format=jsonv2&addressdetails=1&limit=5&accept-language=pt-BR");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(properties.getResponseTimeout())
                .header("Accept", "application/json")
                .header("User-Agent", properties.getUserAgent())
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IntegracaoException(
                        "O serviço de geocodificação retornou HTTP " + response.statusCode() + ".",
                        HttpStatus.BAD_GATEWAY);
            }

            JsonNode root = objectMapper.readTree(response.body());
            if (root == null || !root.isArray()) {
                throw new IntegracaoException(
                        "O serviço de geocodificação retornou uma resposta inválida.",
                        HttpStatus.BAD_GATEWAY);
            }
            List<Localizacao> locations = new ArrayList<>();
            for (JsonNode result : root) {
                double latitude = parseCoordinate(result.path("lat").asText(null));
                double longitude = parseCoordinate(result.path("lon").asText(null));
                String name = result.path("display_name").asText("");
                if (Double.isFinite(latitude) && Math.abs(latitude) <= 90
                        && Double.isFinite(longitude) && Math.abs(longitude) <= 180
                        && !name.trim().isEmpty()) {
                    locations.add(new Localizacao(
                            name,
                            latitude,
                            longitude,
                            result.path("boundingbox").toString()));
                }
            }
            cachedQuery = endereco;
            cachedLocations = Collections.unmodifiableList(new ArrayList<>(locations));
            return locations;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IntegracaoException(
                    "A consulta de localização foi interrompida.",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    exception);
        } catch (IOException | IllegalArgumentException exception) {
            if (exception instanceof IntegracaoException) {
                throw (IntegracaoException) exception;
            }
            throw new IntegracaoException(
                    "Não foi possível consultar o serviço de geocodificação.",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    exception);
        }
    }

    private double parseCoordinate(String value) {
        if (value == null) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            return Double.NaN;
        }
    }

    private void waitForRateLimit() {
        long remaining = MIN_INTERVAL_NANOS - (System.nanoTime() - lastRequestStartedAt);
        if (remaining <= 0) {
            return;
        }
        try {
            TimeUnit.NANOSECONDS.sleep(remaining);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IntegracaoException(
                    "A espera para consultar o geocodificador foi interrompida.",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    exception);
        }
    }
}

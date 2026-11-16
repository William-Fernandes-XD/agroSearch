package com.searchagro.conexao;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.searchagro.entities.PontoUmidade;
import com.searchagro.exception.IntegracaoException;

@Component
public class NasaPowerClient {

    private static final String API_URL = "https://power.larc.nasa.gov/api/temporal/daily/point";
    private static final DateTimeFormatter POWER_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Duration responseTimeout;

    public NasaPowerClient(
            HttpClient geospatialHttpClient,
            ObjectMapper objectMapper,
            HttpClientConfig.IntegracaoProperties properties) {
        this.httpClient = geospatialHttpClient;
        this.objectMapper = objectMapper;
        this.responseTimeout = properties.getResponseTimeout();
    }

    public Double getSurfaceTemperature(PontoUmidade point, LocalDate date) {
        String query = "parameters=TS"
                + "&community=AG"
                + "&longitude=" + point.getLongitude()
                + "&latitude=" + point.getLatitude()
                + "&start=" + POWER_DATE.format(date)
                + "&end=" + POWER_DATE.format(date)
                + "&format=JSON";
        HttpRequest request = HttpRequest.newBuilder(URI.create(API_URL + "?" + query))
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
                        "A NASA POWER retornou HTTP " + response.statusCode() + " ao consultar TS.",
                        HttpStatus.BAD_GATEWAY);
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode value = root.path("properties")
                    .path("parameter")
                    .path("TS")
                    .path(POWER_DATE.format(date));
            if (!value.isNumber()) {
                return null;
            }
            double temperature = value.asDouble();
            return Double.isFinite(temperature) && temperature != -999.0 ? temperature : null;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IntegracaoException(
                    "A consulta à temperatura NASA POWER foi interrompida.",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    exception);
        } catch (IOException exception) {
            throw new IntegracaoException(
                    "Não foi possível consultar a temperatura NASA POWER.",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    exception);
        }
    }

}

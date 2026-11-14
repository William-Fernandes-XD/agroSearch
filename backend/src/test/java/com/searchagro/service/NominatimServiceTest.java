package com.searchagro.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.searchagro.config.HttpClientConfig.NominatimSettings;
import com.searchagro.exception.NominatimException;
import com.searchagro.model.GeographicLocation;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NominatimServiceTest {

    private static final String VALID_RESULT = "{"
            + "\"place_id\":11415832,"
            + "\"licence\":\"Data © OpenStreetMap contributors\","
            + "\"osm_type\":\"relation\","
            + "\"osm_id\":334547,"
            + "\"lat\":\"-16.6808820\","
            + "\"lon\":\"-49.2532691\","
            + "\"category\":\"boundary\","
            + "\"type\":\"administrative\","
            + "\"place_rank\":16,"
            + "\"importance\":0.610489156737459,"
            + "\"addresstype\":\"municipality\","
            + "\"name\":\"Goiânia\","
            + "\"display_name\":\"Goiânia, Goiás, Brasil\","
            + "\"address\":{\"municipality\":\"Goiânia\",\"state\":\"Goiás\","
            + "\"country\":\"Brasil\",\"country_code\":\"br\"},"
            + "\"boundingbox\":[\"-16.8318764\",\"-16.4542142\","
            + "\"-49.4469524\",\"-49.0780000\"],"
            + "\"extratags\":{\"wikidata\":\"Q Goiânia\"},"
            + "\"namedetails\":{\"name:en\":\"Goiania\"},"
            + "\"future_field\":{\"available\":true}"
            + "}";

    private HttpServer server;
    private AtomicReference<String> responseBody;
    private AtomicReference<Integer> responseStatus;
    private AtomicReference<String> requestUri;
    private AtomicReference<String> userAgent;
    private AtomicInteger requestCount;
    private NominatimService service;

    @BeforeEach
    void startServer() throws IOException {
        responseBody = new AtomicReference<>("[" + VALID_RESULT + "]");
        responseStatus = new AtomicReference<>(200);
        requestUri = new AtomicReference<>();
        userAgent = new AtomicReference<>();
        requestCount = new AtomicInteger();

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/search", exchange -> {
            requestCount.incrementAndGet();
            requestUri.set(exchange.getRequestURI().toString());
            userAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));

            byte[] body = responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(responseStatus.get(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/search";
        service = new NominatimService(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
                new ObjectMapper(),
                baseUrl,
                new NominatimSettings("SearchAgro-Test/1.0", Duration.ofSeconds(2)),
                Duration.ZERO,
                Duration.ofMinutes(5));
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsEncodedQueryAndReturnsTypedResultsWithAdditionalJson() throws Exception {
        List<GeographicLocation> results = service.search("Goiânia, Goiás, Brasil");

        assertEquals(1, results.size());
        GeographicLocation location = results.get(0);
        assertEquals("Goiânia", location.getName());
        assertEquals("-16.6808820", location.getLat());
        assertEquals("-49.2532691", location.getLon());
        assertEquals("wikidata", location.getExtratags().fieldNames().next());
        assertEquals("Goiania", location.getNamedetails().get("name:en").asText());
        assertTrue(location.getOriginalJson().has("future_field"));
        assertTrue(location.getAdditionalFields().containsKey("future_field"));
        String serializedLocation = new ObjectMapper().writeValueAsString(location);
        assertTrue(serializedLocation.contains("\"future_field\":{\"available\":true}"));
        assertFalse(serializedLocation.contains("additionalFields"));

        String decodedQuery = URLDecoder.decode(requestUri.get(), StandardCharsets.UTF_8.name());
        assertTrue(decodedQuery.contains("q=Goiânia, Goiás, Brasil"));
        assertTrue(decodedQuery.contains("format=jsonv2"));
        assertTrue(decodedQuery.contains("addressdetails=1"));
        assertTrue(decodedQuery.contains("extratags=1"));
        assertTrue(decodedQuery.contains("namedetails=1"));
        assertTrue(decodedQuery.contains("limit=40"));
        assertTrue(decodedQuery.contains("accept-language=pt-BR"));
        assertEquals("SearchAgro-Test/1.0", userAgent.get());
    }

    @Test
    void reusesRecentIdenticalSearchWithoutAnotherRequest() {
        service.search("Goiânia");
        service.search("Goiânia");

        assertEquals(1, requestCount.get());
    }

    @Test
    void rejectsResultsWithoutValidCoordinates() {
        responseBody.set("[" + VALID_RESULT.replace("\"lat\":\"-16.6808820\"", "\"lat\":\"999\"") + "]");

        assertThrows(NominatimException.class, () -> service.search("local inválido"));
    }

    @Test
    void reportsUpstreamHttpError() {
        responseStatus.set(429);

        NominatimException exception = assertThrows(
                NominatimException.class,
                () -> service.search("Goiânia"));

        assertEquals(429, exception.getUpstreamStatus());
        assertEquals(502, exception.getResponseStatus().value());
    }

    @Test
    void rejectsMalformedJsonResponse() {
        responseBody.set("{not-json");

        assertThrows(NominatimException.class, () -> service.search("Goiânia"));
        assertEquals(1, requestCount.get());
    }
}

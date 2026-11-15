package com.searchagro.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.searchagro.conexao.HttpClientConfig.IntegracaoProperties;
import com.searchagro.entities.Localizacao;
import com.searchagro.services.NominatimService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LocalizacaoServiceTest {

    private HttpServer server;
    private AtomicReference<String> requestPath;
    private NominatimService service;

    @BeforeEach
    void startServer() throws IOException {
        requestPath = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/search", exchange -> {
            requestPath.set(exchange.getRequestURI().toString());
            byte[] body = ("[{\"lat\":\"-16.6808820\",\"lon\":\"-49.2532691\","
                    + "\"display_name\":\"Goiânia, Goiás, Brasil\","
                    + "\"boundingbox\":[\"-16.8\",\"-16.4\",\"-49.4\",\"-49.0\"]}]")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        service = new NominatimService(
                HttpClient.newHttpClient(),
                new ObjectMapper(),
                new IntegracaoProperties("AgroSearch-Test/1.0", Duration.ofSeconds(2), "", "", Duration.ZERO),
                "http://127.0.0.1:" + server.getAddress().getPort() + "/search");
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void encodesAddressAndReturnsOnlyValidLocations() {
        List<Localizacao> results = service.buscar("Goiânia, Goiás, Brasil");

        assertEquals(1, results.size());
        assertEquals(-16.680882, results.get(0).getLatitude(), 0.000001);
        assertEquals(-49.253269, results.get(0).getLongitude(), 0.000001);
        assertEquals("Goiânia, Goiás, Brasil", results.get(0).getNome());
        org.junit.jupiter.api.Assertions.assertTrue(requestPath.get().contains("format=jsonv2"));
        org.junit.jupiter.api.Assertions.assertTrue(requestPath.get().contains("accept-language=pt-BR"));
    }
}

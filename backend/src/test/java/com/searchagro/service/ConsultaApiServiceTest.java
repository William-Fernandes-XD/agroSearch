package com.searchagro.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.google.gson.Gson;
import com.searchagro.dto.AnaliseSoloDTO;
import com.searchagro.exception.ApiException;
import com.searchagro.model.ResultadoSoilGrids;
import com.sun.net.httpserver.HttpServer;

class ConsultaApiServiceTest {

    private HttpServer server;
    private AtomicReference<String> nominatimResponseBody;
    private AtomicReference<Integer> nominatimResponseStatus;
    private AtomicReference<String> soilgridsResponseBody;
    private AtomicReference<Integer> soilgridsResponseStatus;
    private AtomicInteger nominatimCallCount;
    private AtomicInteger soilgridsCallCount;
    private ConsultaApiService service;

    private static final String NOMINATIM_SUCCESS_JSON = "["
            + "{"
            + "\"place_id\":11415832,"
            + "\"lat\":\"-16.6869\","
            + "\"lon\":\"-49.2648\","
            + "\"display_name\":\"Goiânia, Goiás, Brasil\","
            + "\"type\":\"administrative\""
            + "}"
            + "]";

    private static final String SOILGRIDS_PH_52_JSON = "{"
            + "\"type\":\"Feature\","
            + "\"geometry\":{\"type\":\"Point\",\"coordinates\":[-49.2648,-16.6869]},"
            + "\"properties\":{"
            + "  \"layers\":["
            + "    {"
            + "      \"name\":\"phh2o\","
            + "      \"unit_measure\":{\"d_factor\":10,\"mapped_units\":\"pH*10\",\"target_units\":\"-\"},"
            + "      \"depths\":["
            + "        {\"label\":\"0-5cm\",\"values\":{\"mean\":52}}"
            + "      ]"
            + "    }"
            + "  ]"
            + "}"
            + "}";

    private static final String SOILGRIDS_PH_NULL_JSON = "{"
            + "\"type\":\"Feature\","
            + "\"geometry\":{\"type\":\"Point\",\"coordinates\":[-49.2648,-16.6869]},"
            + "\"properties\":{"
            + "  \"layers\":["
            + "    {"
            + "      \"name\":\"phh2o\","
            + "      \"unit_measure\":{\"d_factor\":10,\"mapped_units\":\"pH*10\",\"target_units\":\"-\"},"
            + "      \"depths\":["
            + "        {\"label\":\"0-5cm\",\"values\":{\"mean\":null}}"
            + "      ]"
            + "    }"
            + "  ]"
            + "}"
            + "}";

    @BeforeEach
    void setUp() throws IOException {
        nominatimResponseBody = new AtomicReference<>(NOMINATIM_SUCCESS_JSON);
        nominatimResponseStatus = new AtomicReference<>(200);
        soilgridsResponseBody = new AtomicReference<>(SOILGRIDS_PH_52_JSON);
        soilgridsResponseStatus = new AtomicReference<>(200);
        nominatimCallCount = new AtomicInteger();
        soilgridsCallCount = new AtomicInteger();

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        server.createContext("/nominatim/search", exchange -> {
            nominatimCallCount.incrementAndGet();
            byte[] body = nominatimResponseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(nominatimResponseStatus.get(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        server.createContext("/soilgrids/query", exchange -> {
            soilgridsCallCount.incrementAndGet();
            byte[] body = soilgridsResponseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(soilgridsResponseStatus.get(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        server.start();

        int port = server.getAddress().getPort();
        String nominatimUrl = "http://127.0.0.1:" + port + "/nominatim/search";
        String soilgridsUrl = "http://127.0.0.1:" + port + "/soilgrids/query";

        service = new ConsultaApiService(
                new Gson(),
                nominatimUrl,
                "SearchAgro-Test/1.0",
                3000,
                3000,
                0,
                soilgridsUrl,
                3000,
                3000);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void fluxoCompletoComSucessoEPhCalculado() {
        AnaliseSoloDTO dto = service.analisarSoloPorEndereco("Goiânia, Goiás, Brasil");

        assertNotNull(dto);
        assertEquals("Goiânia, Goiás, Brasil", dto.getLocalizacao());
        assertEquals(-16.6869, dto.getLatitude(), 0.0001);
        assertEquals(-49.2648, dto.getLongitude(), 0.0001);
        assertEquals("phh2o", dto.getPropriedade());
        assertEquals("0-5cm", dto.getProfundidade());
        assertEquals(5.2, dto.getPh());
        assertEquals(1, nominatimCallCount.get());
        assertEquals(1, soilgridsCallCount.get());
    }

    @Test
    void fluxoCompletoComPhNuloPreservaNuloENaoSubstituiPorZero() {
        soilgridsResponseBody.set(SOILGRIDS_PH_NULL_JSON);

        AnaliseSoloDTO dto = service.analisarSoloPorEndereco("Goiânia, Goiás, Brasil");

        assertNotNull(dto);
        assertEquals("Goiânia, Goiás, Brasil", dto.getLocalizacao());
        assertEquals(-16.6869, dto.getLatitude(), 0.0001);
        assertEquals(-49.2648, dto.getLongitude(), 0.0001);
        assertNull(dto.getPh(), "O pH nulo retornado pela API deve ser estritamente preservado como null.");
    }

    @Test
    void lancaExcecaoQuandoLocalizacaoNaoEncontrada() {
        nominatimResponseBody.set("[]");

        ApiException ex = assertThrows(ApiException.class, () ->
                service.analisarSoloPorEndereco("Endereço Inexistente XYZ 123"));

        assertEquals("Localização não encontrada.", ex.getMessage());
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals(1, nominatimCallCount.get());
        assertEquals(0, soilgridsCallCount.get(), "SoilGrids não deve ser chamado quando a localização não for encontrada.");
    }

    @Test
    void lancaExcecaoParaEnderecoVazio() {
        assertThrows(ApiException.class, () -> service.analisarSoloPorEndereco("   "));
        assertThrows(ApiException.class, () -> service.analisarSoloPorEndereco(null));
        assertEquals(0, nominatimCallCount.get());
    }

    @Test
    void rejeitaCoordenadasForaDosLimites() {
        assertThrows(ApiException.class, () -> service.consultarPh(95.0, -49.0));
        assertThrows(ApiException.class, () -> service.consultarPh(-16.0, 195.0));
    }

    @Test
    void trataErroHttpNominatim() {
        nominatimResponseStatus.set(500);

        ApiException ex = assertThrows(ApiException.class, () ->
                service.analisarSoloPorEndereco("Goiânia"));

        assertTrue(ex.getMessage().contains("status HTTP de erro: 500") || ex.getMessage().contains("500"));
        assertEquals(500, ex.getUpstreamStatus());
    }

    @Test
    void trataErroHttpSoilGrids() {
        soilgridsResponseStatus.set(503);

        ApiException ex = assertThrows(ApiException.class, () ->
                service.analisarSoloPorEndereco("Goiânia"));

        assertTrue(ex.getMessage().contains("status HTTP de erro: 503") || ex.getMessage().contains("503"));
        assertEquals(503, ex.getUpstreamStatus());
    }

    @Test
    void calculoDePhRespeitaFatorDeEscalaDFactor() {
        ResultadoSoilGrids resultado = new Gson().fromJson(SOILGRIDS_PH_52_JSON, ResultadoSoilGrids.class);
        Double ph = resultado.calcularPh("phh2o", "0-5cm");
        assertEquals(5.2, ph);

        // Teste com outro valor (ex: mean 61 -> pH 6.1)
        String json61 = SOILGRIDS_PH_52_JSON.replace("52", "61");
        ResultadoSoilGrids resultado61 = new Gson().fromJson(json61, ResultadoSoilGrids.class);
        assertEquals(6.1, resultado61.calcularPh("phh2o", "0-5cm"));

        // Teste com outro valor (ex: mean 70 -> pH 7.0)
        String json70 = SOILGRIDS_PH_52_JSON.replace("52", "70");
        ResultadoSoilGrids resultado70 = new Gson().fromJson(json70, ResultadoSoilGrids.class);
        assertEquals(7.0, resultado70.calcularPh("phh2o", "0-5cm"));
    }
}

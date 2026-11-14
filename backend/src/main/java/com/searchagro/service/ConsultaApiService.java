package com.searchagro.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.searchagro.constantes.Constantes;
import com.searchagro.dto.AnaliseSoloDTO;
import com.searchagro.exception.ApiException;
import com.searchagro.model.Localizacao;
import com.searchagro.model.ResultadoSoilGrids;

@Service
public class ConsultaApiService implements ConsultaApi {

    private final Gson gson;
    private final String nominatimSearchUrl;
    private final String nominatimUserAgent;
    private final int nominatimConnectTimeoutMs;
    private final int nominatimReadTimeoutMs;
    private final String soilgridsQueryUrl;
    private final int soilgridsConnectTimeoutMs;
    private final int soilgridsReadTimeoutMs;
    private final long requestIntervalNanos;

    private long lastNominatimRequestNanos = System.nanoTime() - Duration.ofSeconds(1).toNanos();

    @org.springframework.beans.factory.annotation.Autowired
    public ConsultaApiService(
            @Value("${nominatim.search-url:" + Constantes.NOMINATIM_LOCALIZACAO + "}") String nominatimSearchUrl,
            @Value("${nominatim.user-agent:SearchAgro/0.0.1}") String nominatimUserAgent,
            @Value("${nominatim.connect-timeout:5s}") Duration nominatimConnectTimeout,
            @Value("${nominatim.response-timeout:10s}") Duration nominatimResponseTimeout,
            @Value("${nominatim.request-interval:1s}") Duration requestInterval,
            @Value("${soilgrids.query-url:" + Constantes.SOILGRIDS_PROPERTIES_QUERY + "}") String soilgridsQueryUrl,
            @Value("${soilgrids.connect-timeout:5s}") Duration soilgridsConnectTimeout,
            @Value("${soilgrids.response-timeout:10s}") Duration soilgridsResponseTimeout) {
        this.gson = new Gson();
        this.nominatimSearchUrl = nominatimSearchUrl;
        this.nominatimUserAgent = nominatimUserAgent;
        this.nominatimConnectTimeoutMs = (int) nominatimConnectTimeout.toMillis();
        this.nominatimReadTimeoutMs = (int) nominatimResponseTimeout.toMillis();
        this.requestIntervalNanos = Math.max(0, requestInterval.toNanos());
        this.soilgridsQueryUrl = soilgridsQueryUrl;
        this.soilgridsConnectTimeoutMs = (int) soilgridsConnectTimeout.toMillis();
        this.soilgridsReadTimeoutMs = (int) soilgridsResponseTimeout.toMillis();
    }

    /**
     * Construtor flexível para injeção personalizada ou testes unitários.
     */
    public ConsultaApiService(
            Gson gson,
            String nominatimSearchUrl,
            String nominatimUserAgent,
            int nominatimConnectTimeoutMs,
            int nominatimReadTimeoutMs,
            long requestIntervalNanos,
            String soilgridsQueryUrl,
            int soilgridsConnectTimeoutMs,
            int soilgridsReadTimeoutMs) {
        this.gson = (gson != null) ? gson : new Gson();
        this.nominatimSearchUrl = nominatimSearchUrl;
        this.nominatimUserAgent = nominatimUserAgent;
        this.nominatimConnectTimeoutMs = nominatimConnectTimeoutMs;
        this.nominatimReadTimeoutMs = nominatimReadTimeoutMs;
        this.requestIntervalNanos = requestIntervalNanos;
        this.soilgridsQueryUrl = soilgridsQueryUrl;
        this.soilgridsConnectTimeoutMs = soilgridsConnectTimeoutMs;
        this.soilgridsReadTimeoutMs = soilgridsReadTimeoutMs;
    }

    /**
     * Método central e reutilizável para executar requisições HTTP GET utilizando HttpURLConnection,
     * URL e BufferedReader, padronizando timeouts, cabeçalhos, tratamento de erros e fechamento de conexões.
     */
    public String executarRequisicaoHttp(String urlStr, String userAgent, int connectTimeoutMs, int readTimeoutMs) {
        HttpURLConnection connection = null;
        BufferedReader reader = null;
        try {
            URL url = new URL(urlStr);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(connectTimeoutMs);
            connection.setReadTimeout(readTimeoutMs);
            connection.setRequestProperty("Accept", "application/json");

            if (userAgent != null && !userAgent.trim().isEmpty()) {
                connection.setRequestProperty("User-Agent", userAgent);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                throw new ApiException(
                        "A API externa retornou status HTTP de erro: " + responseCode,
                        responseCode);
            }

            reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder responseBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                responseBuilder.append(line);
            }

            return responseBuilder.toString();
        } catch (SocketTimeoutException e) {
            throw new ApiException(
                    "A requisição para a API externa excedeu o tempo limite (timeout).",
                    HttpStatus.GATEWAY_TIMEOUT,
                    e);
        } catch (IOException e) {
            throw new ApiException(
                    "Erro de conexão com a API externa: " + e.getMessage(),
                    HttpStatus.SERVICE_UNAVAILABLE,
                    e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    @Override
    public Localizacao[] buscarLocalizacao(String endereco) {
        if (endereco == null || endereco.trim().isEmpty()) {
            throw new ApiException("O endereço informado para consulta não pode estar vazio.", HttpStatus.BAD_REQUEST);
        }

        aguardarIntervaloNominatim();

        String encodedEndereco = URLEncoder.encode(endereco.trim(), StandardCharsets.UTF_8).replace("+", "%20");
        String separador = nominatimSearchUrl.contains("?") ? "&" : "?";
        String urlFinal = nominatimSearchUrl + separador
                + "q=" + encodedEndereco
                + "&format=jsonv2"
                + "&addressdetails=1"
                + "&limit=5";

        String jsonResponse = executarRequisicaoHttp(
                urlFinal,
                nominatimUserAgent,
                nominatimConnectTimeoutMs,
                nominatimReadTimeoutMs);

        try {
            Localizacao[] localizacoes = gson.fromJson(jsonResponse, Localizacao[].class);
            return (localizacoes != null) ? localizacoes : new Localizacao[0];
        } catch (JsonSyntaxException e) {
            throw new ApiException("Formato de resposta inválido retornado pelo serviço de geocodificação.", e);
        }
    }

    @Override
    public ResultadoSoilGrids consultarPh(double latitude, double longitude) {
        validarCoordenadas(latitude, longitude);

        String separador = soilgridsQueryUrl.contains("?") ? "&" : "?";
        String urlFinal = String.format(
                Locale.US,
                "%s%slat=%.4f&lon=%.4f&property=%s&depth=%s&value=%s",
                soilgridsQueryUrl,
                separador,
                latitude,
                longitude,
                Constantes.PROPRIEDADE_PH,
                Constantes.PROFUNDIDADE_PADRAO,
                Constantes.VALOR_PADRAO);

        String jsonResponse = executarRequisicaoHttp(
                urlFinal,
                nominatimUserAgent,
                soilgridsConnectTimeoutMs,
                soilgridsReadTimeoutMs);

        try {
            return gson.fromJson(jsonResponse, ResultadoSoilGrids.class);
        } catch (JsonSyntaxException e) {
            throw new ApiException("Formato de resposta JSON inválido retornado pelo SoilGrids.", e);
        }
    }

    @Override
    public AnaliseSoloDTO analisarSoloPorEndereco(String endereco) {
        if (endereco == null || endereco.trim().isEmpty()) {
            throw new ApiException("O endereço informado não pode ser vazio.", HttpStatus.BAD_REQUEST);
        }

        // 1. Consultar Nominatim
        Localizacao[] localizacoes = buscarLocalizacao(endereco);

        // 2. Validar se encontrou alguma localização
        if (localizacoes == null || localizacoes.length == 0) {
            throw new ApiException("Localização não encontrada.", HttpStatus.NOT_FOUND);
        }

        // 3. Obter coordenadas da primeira localização
        Localizacao primeiraLocalizacao = localizacoes[0];
        if (primeiraLocalizacao.getLat() == null || primeiraLocalizacao.getLon() == null) {
            throw new ApiException("A localização retornada não contém coordenadas geográficas válidas.");
        }

        double latitude;
        double longitude;
        try {
            latitude = Double.parseDouble(primeiraLocalizacao.getLat());
            longitude = Double.parseDouble(primeiraLocalizacao.getLon());
        } catch (NumberFormatException e) {
            throw new ApiException("Coordenadas geográficas retornadas são inválidas.", e);
        }

        validarCoordenadas(latitude, longitude);

        // 4. Consultar SoilGrids utilizando as coordenadas
        ResultadoSoilGrids resultado = consultarPh(latitude, longitude);

        // 5 e 6. Extrair e converter o valor de pH respeitando o fator de escala da API
        Double ph = (resultado != null)
                ? resultado.calcularPh(Constantes.PROPRIEDADE_PH, Constantes.PROFUNDIDADE_PADRAO)
                : null;

        // 7. Montar e retornar o DTO consolidado
        String nomeLocalizacao = (primeiraLocalizacao.getDisplayName() != null && !primeiraLocalizacao.getDisplayName().trim().isEmpty())
                ? primeiraLocalizacao.getDisplayName()
                : endereco;

        return new AnaliseSoloDTO(
                nomeLocalizacao,
                latitude,
                longitude,
                Constantes.PROPRIEDADE_PH,
                Constantes.PROFUNDIDADE_PADRAO,
                ph);
    }

    private void validarCoordenadas(double latitude, double longitude) {
        if (Double.isNaN(latitude) || Double.isInfinite(latitude)
                || Double.isNaN(longitude) || Double.isInfinite(longitude)) {
            throw new ApiException("As coordenadas informadas não são números finitos.");
        }
        if (latitude < -90.0 || latitude > 90.0 || longitude < -180.0 || longitude > 180.0) {
            throw new ApiException(
                    String.format(Locale.US, "Coordenadas fora dos limites válidos (lat: %.4f, lon: %.4f).", latitude, longitude),
                    HttpStatus.BAD_REQUEST);
        }
    }

    private synchronized void aguardarIntervaloNominatim() {
        if (requestIntervalNanos <= 0) {
            return;
        }

        long decorrido = System.nanoTime() - lastNominatimRequestNanos;
        long tempoEspera = requestIntervalNanos - decorrido;
        if (tempoEspera > 0) {
            try {
                TimeUnit.NANOSECONDS.sleep(tempoEspera);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ApiException("Aguardando intervalo entre requisições do Nominatim foi interrompido.", HttpStatus.SERVICE_UNAVAILABLE, e);
            }
        }
        lastNominatimRequestNanos = System.nanoTime();
    }
}

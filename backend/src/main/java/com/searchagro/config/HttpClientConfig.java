package com.searchagro.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HttpClientConfig {

    @Bean
    public HttpClient nominatimHttpClient(
            @Value("${nominatim.connect-timeout:5s}") Duration connectTimeout) {
        return HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Bean
    public NominatimSettings nominatimSettings(
            @Value("${nominatim.user-agent}") String userAgent,
            @Value("${nominatim.response-timeout:10s}") Duration responseTimeout) {
        return new NominatimSettings(userAgent, responseTimeout);
    }

    public static class NominatimSettings {

        private final String userAgent;
        private final Duration responseTimeout;

        public NominatimSettings(String userAgent, Duration responseTimeout) {
            this.userAgent = userAgent;
            this.responseTimeout = responseTimeout;
        }

        public String getUserAgent() {
            return userAgent;
        }

        public Duration getResponseTimeout() {
            return responseTimeout;
        }
    }
}

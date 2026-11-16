package com.searchagro.conexao;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HttpClientConfig {

    @Bean
    public HttpClient geospatialHttpClient(
            @Value("${integracao.connect-timeout:10s}") Duration connectTimeout) {
        return HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Bean
    public IntegracaoProperties integracaoProperties(
            @Value("${nominatim.user-agent:AgroSearch/1.0}") String userAgent,
            @Value("${integracao.response-timeout:30s}") Duration responseTimeout,
            @Value("${nasa.earthdata.username:}") String earthdataUsername,
            @Value("${nasa.earthdata.password:}") String earthdataPassword,
            @Value("${nasa.appeears.task-timeout:900s}") Duration appEearsTaskTimeout) {
        return new IntegracaoProperties(
                userAgent,
                responseTimeout,
                earthdataUsername,
                earthdataPassword,
                appEearsTaskTimeout);
    }

    public static class IntegracaoProperties {

        private final String userAgent;
        private final Duration responseTimeout;
        private final String earthdataUsername;
        private final String earthdataPassword;
        private final Duration appEearsTaskTimeout;

        public IntegracaoProperties(
                String userAgent,
                Duration responseTimeout,
                String earthdataUsername,
                String earthdataPassword,
                Duration appEearsTaskTimeout) {
            this.userAgent = userAgent;
            this.responseTimeout = responseTimeout;
            this.earthdataUsername = earthdataUsername;
            this.earthdataPassword = earthdataPassword;
            this.appEearsTaskTimeout = appEearsTaskTimeout;
        }

        public String getUserAgent() {
            return userAgent;
        }

        public Duration getResponseTimeout() {
            return responseTimeout;
        }

        public String getEarthdataUsername() {
            return earthdataUsername;
        }

        public String getEarthdataPassword() {
            return earthdataPassword;
        }

        public Duration getAppEearsTaskTimeout() {
            return appEearsTaskTimeout;
        }
    }
}

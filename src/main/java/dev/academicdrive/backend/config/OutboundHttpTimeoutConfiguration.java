package dev.academicdrive.backend.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;

@Configuration(proxyBeanMethods = false)
public class OutboundHttpTimeoutConfiguration {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration RESPONSE_TIMEOUT = Duration.ofMinutes(3);

    @Bean
    RestClientCustomizer outboundHttpTimeouts() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(RESPONSE_TIMEOUT);
        return builder -> builder.requestFactory(requestFactory);
    }
}

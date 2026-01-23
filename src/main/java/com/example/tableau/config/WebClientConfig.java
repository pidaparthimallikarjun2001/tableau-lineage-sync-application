package com.example.tableau.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Configuration for WebClient used to communicate with Tableau APIs.
 */
@Configuration
public class WebClientConfig {

    /** Maximum in-memory buffer size for responses (16 MB) */
    private static final int MAX_IN_MEMORY_SIZE_BYTES = 16 * 1024 * 1024;
    
    /** Connection timeout in milliseconds */
    private static final int CONNECT_TIMEOUT_MS = 30000;
    
    /** Read/Write timeout in seconds */
    private static final int IO_TIMEOUT_SECONDS = 60;

    /**
     * Configure WebClient with proper timeouts and buffer sizes.
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MS)
                .responseTimeout(Duration.ofSeconds(IO_TIMEOUT_SECONDS))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(IO_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(IO_TIMEOUT_SECONDS, TimeUnit.SECONDS)));

        // Increase buffer size for large GraphQL responses
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_SIZE_BYTES))
                .build();

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies);
    }
}

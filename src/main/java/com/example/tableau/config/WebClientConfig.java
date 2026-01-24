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
import reactor.netty.resources.ConnectionProvider;

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
    
    /** Read/Write timeout in seconds - increased for large paginated responses */
    private static final int IO_TIMEOUT_SECONDS = 180;
    
    /** Response timeout in seconds - increased to handle large GraphQL responses */
    private static final int RESPONSE_TIMEOUT_SECONDS = 180;
    
    /** Max connection pool size */
    private static final int MAX_CONNECTIONS = 50;
    
    /** Pending acquire timeout in seconds */
    private static final int PENDING_ACQUIRE_TIMEOUT_SECONDS = 60;
    
    /** Max idle time for connections in seconds */
    private static final int MAX_IDLE_TIME_SECONDS = 30;
    
    /** Max life time for connections in seconds */
    private static final int MAX_LIFE_TIME_SECONDS = 300;

    /**
     * Configure WebClient.Builder with proper timeouts, connection pooling, and keep-alive settings
     * to prevent premature connection closures.
     * 
     * Configuration details:
     * - Connection timeout: 30 seconds
     * - Read/Write/Response timeout: 180 seconds (for large GraphQL responses)
     * - Connection pool: 50 max connections with lifecycle management
     * - TCP keep-alive: Enabled to maintain persistent connections
     * 
     * @return WebClient.Builder configured with custom HTTP client settings
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        // Configure connection pool with generous limits and keep-alive settings
        ConnectionProvider connectionProvider = ConnectionProvider.builder("tableau-connection-pool")
                .maxConnections(MAX_CONNECTIONS)
                .pendingAcquireTimeout(Duration.ofSeconds(PENDING_ACQUIRE_TIMEOUT_SECONDS))
                .maxIdleTime(Duration.ofSeconds(MAX_IDLE_TIME_SECONDS))
                .maxLifeTime(Duration.ofSeconds(MAX_LIFE_TIME_SECONDS))
                .evictInBackground(Duration.ofSeconds(120))
                .build();

        HttpClient httpClient = HttpClient.create(connectionProvider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MS)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .responseTimeout(Duration.ofSeconds(RESPONSE_TIMEOUT_SECONDS))
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

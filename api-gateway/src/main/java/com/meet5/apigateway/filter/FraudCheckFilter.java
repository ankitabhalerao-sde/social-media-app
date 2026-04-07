package com.meet5.apigateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * FraudCheckGatewayFilter
 * <p>
 * Intercepts every POST request before routing to downstream services.
 * Reads X-User-Id header, calls fraud-service to check status.
 * If user is FRAUD — returns 403 immediately, request never reaches downstream.
 * If user is CLEAN — request passes through normally.
 * <p>
 * Why at the gateway level?
 * Centralised enforcement — no service needs to implement its own fraud check.
 * If a new service is added, it gets fraud protection automatically
 * just by adding the FraudCheck filter to its route.
 */
@Component
public class FraudCheckFilter implements GlobalFilter, Ordered {

    private static final Logger LOGGER = LoggerFactory.getLogger(FraudCheckFilter.class);

    @Value("${fraud.service.url}")
    private String fraudServiceUrl;

    @Value("${fraud.header.name}")
    private String userIdHeader;

    private final WebClient webClient;

    public FraudCheckFilter(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (HttpMethod.POST.name().equals(exchange.getRequest().getMethod())) {
            return chain.filter(exchange);
        }

        String userId = exchange.getRequest().getHeaders().getFirst(userIdHeader);
        if (userId == null || userId.isBlank()) {
            LOGGER.info("No {} header found - Skipping fraud check", userIdHeader);
            return chain.filter(exchange);
        }

        return webClient.get()
                .uri(fraudServiceUrl + "/api/v1/frauds/{userId}/status", userId)
                .retrieve()
                .bodyToMono(Map.class)
                .flatMap(response -> {
                    Boolean blocked = (Boolean) response.get("blocked");

                    if (Boolean.TRUE.equals(blocked)) {
                        LOGGER.warn("BLOCKED request from fraud user={}", userId);
                        return blockRequest(exchange);
                    }
                    LOGGER.debug("Fraud check PASSED for userId={}", userId);
                    return chain.filter(exchange);
                })
                .onErrorResume(ex -> {
                    LOGGER.error("Fraud service unreachable — failing open: {}", ex.getMessage());
                    return chain.filter(exchange);
                });
    }

    private Mono<Void> blockRequest(ServerWebExchange exchange) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = """
            {
                "status": 403,
                "error": "FRAUD_BLOCKED",
                "message": "User is blocked due to fraudulent activity"
            }
            """;

        var buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
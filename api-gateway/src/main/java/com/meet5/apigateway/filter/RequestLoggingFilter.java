package com.meet5.apigateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        LOGGER.info("→ {} {} [userId={}]", request.getMethod(), request.getPath(), request.getHeaders().getFirst("X-User-Id"));
        long startTime = System.currentTimeMillis();
        return chain.filter(exchange)
                .then(Mono.fromRunnable(() -> LOGGER.info("← {} {} {}ms", exchange.getResponse().getStatusCode(),
                        request.getPath(), System.currentTimeMillis() - startTime)));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}

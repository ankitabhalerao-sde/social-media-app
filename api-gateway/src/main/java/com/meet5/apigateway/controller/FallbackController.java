package com.meet5.apigateway.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/fallback")
public class FallbackController {
    private static final Logger LOGGER = LoggerFactory.getLogger(FallbackController.class);

    @RequestMapping("/user-service")
    public ResponseEntity<Map<String, Object>> userFallback() {
        LOGGER.warn("Circuit breaker OPEN — user-service unavailable");
        return handleFallback("User-Service");
    }

    @RequestMapping("/interaction-service")
    public ResponseEntity<Map<String, Object>> interactionFallback() {
        LOGGER.warn("Circuit breaker OPEN — interation-service unavailable");
        return handleFallback("Interaction-Service");
    }

    @RequestMapping("/fraud-service")
    public ResponseEntity<Map<String, Object>> fraudFallback() {
        LOGGER.warn("Circuit breaker OPEN — fraud-service unavailable");
        return handleFallback("Fraud-Service");
    }

    public ResponseEntity<Map<String, Object>> handleFallback(String serviceName) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(
                        Map.of("status", HttpStatus.SERVICE_UNAVAILABLE.value(),
                                "error", "SERVICE_UNAVAILABLE",
                                "message", serviceName + " is currently unavailable. Please try again later.",
                                "timestamp", Instant.now().toString())
                );
    }
}

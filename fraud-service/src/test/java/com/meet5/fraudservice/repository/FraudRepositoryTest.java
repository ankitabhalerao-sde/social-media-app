package com.meet5.fraudservice.repository;

import com.meet5.fraudservice.domain.FraudEvent;
import com.meet5.fraudservice.domain.FraudStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;


import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;


@Testcontainers
@DisplayName("FraudRepository")
@Import(FraudRepository.class)
@SpringBootTest
public class FraudRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("fraud_test")
            .withUsername("postgres")
            .withPassword("postgres");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    FraudRepository fraudRepository;

    @Test
    @DisplayName("findStatus — returns empty for unknown user")
    void findStatus_returnsEmptyForUnknownUser() {
        Optional<FraudStatus> status = fraudRepository.findStatusByUserId(UUID.randomUUID());
        assertThat(status).isEmpty();
    }

    @Test
    @DisplayName("findStatus — returns status after upsert")
    void shouldUpsertAndReturnStatusAfterUpsert() {
        UUID userId = UUID.randomUUID();
        fraudRepository.upsertStatus(userId, FraudStatus.FRAUD);

        Optional<FraudStatus> status = fraudRepository.findStatusByUserId(userId);

        assertThat(status).isPresent();
        assertThat(status.get()).isEqualTo(FraudStatus.FRAUD);
    }

    @Test
    @DisplayName("upsertStatus — inserts new status")
    void shouldInsertNewStatus() {
        UUID userId = UUID.randomUUID();

        fraudRepository.upsertStatus(userId, FraudStatus.CLEAN);
        System.out.println(postgres.getJdbcUrl());
        assertThat(fraudRepository.findStatusByUserId(userId))
                .isPresent()
                .hasValue(FraudStatus.CLEAN);
    }

    @Test
    @DisplayName("upsertStatus — updates existing status")
    void shouldUpdateExistingStatus() {
        UUID userId = UUID.randomUUID();
        fraudRepository.upsertStatus(userId, FraudStatus.CLEAN);
        fraudRepository.upsertStatus(userId, FraudStatus.FRAUD);

        assertThat(fraudRepository.findStatusByUserId(userId))
                .isPresent()
                .hasValue(FraudStatus.FRAUD);
    }

    @Test
    @DisplayName("upsertStatus_Should not create duplicate record")
    public void shouldNotCreateDuplicateRecord() {
        UUID userId = UUID.randomUUID();
        fraudRepository.upsertStatus(userId, FraudStatus.FRAUD);
        fraudRepository.upsertStatus(userId, FraudStatus.CLEAN);

        Optional<FraudStatus> status = fraudRepository.findStatusByUserId(userId);
        assertThat(status).isPresent();
    }

    @Test
    @DisplayName("logFraudEvent_add fraud event to DB")
    public void shouldAddLogFraudEvent() {
        UUID userId = UUID.randomUUID();
        FraudEvent event = FraudEvent.builder()
                .userId(userId)
                .reason("Exceeded 100 visit/likes within 10 minutes")
                .actionCount(100)
                .detectedAt(Instant.now())
                .build();
        fraudRepository.logFraudEvent(event); // should not throw exception
    }

    @Test
    @DisplayName("logFraudEvent — multiple events for same user are allowed")
    void shouldAllowMultipleEventsPerUser() {
        UUID userId = UUID.randomUUID();

        FraudEvent event1 = FraudEvent.builder()
                .userId(userId)
                .reason("First detection")
                .actionCount(100)
                .detectedAt(Instant.now())
                .build();

        FraudEvent event2 = FraudEvent.builder()
                .userId(userId)
                .reason("Second detection")
                .actionCount(150)
                .detectedAt(Instant.now())
                .build();

        // Both should persist without conflict
        fraudRepository.logFraudEvent(event1);
        fraudRepository.logFraudEvent(event2);
    }
}

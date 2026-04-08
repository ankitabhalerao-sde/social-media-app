package com.meet5.fraudservice.service;

import com.meet5.fraudservice.domain.FraudEvent;
import com.meet5.fraudservice.domain.FraudStatus;
import com.meet5.fraudservice.dto.FraudMarkedEvent;
import com.meet5.fraudservice.repository.FraudRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FraudDetectionServiceTest {

    @Mock
    private FraudRepository fraudRepository;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    ValueOperations<String, String> valueOperations;

    @InjectMocks
    FraudDetectionService fraudDetectionService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(fraudDetectionService, "actionLimit", 100);
        ReflectionTestUtils.setField(fraudDetectionService, "windowMinutes", 10);
    }

    @Test
    @DisplayName("evaluateUser")
    public void doNothingWhenUserFraudTest() {
        UUID userId = UUID.randomUUID();

        when(fraudRepository.findStatusByUserId(userId)).thenReturn(Optional.of(FraudStatus.FRAUD));

        // nothing should happen
        fraudDetectionService.evaluateUser(userId, "VISIT");
        verifyNoInteractions(stringRedisTemplate);
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    @DisplayName("evaluateUser_Edit counter on each interaction")
    public void incrementRedisCounterTest() {
        UUID userId = UUID.randomUUID();
        when(fraudRepository.findStatusByUserId(userId)).thenReturn(Optional.of(FraudStatus.CLEAN));
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);
        fraudDetectionService.evaluateUser(userId, "VISIT");


        verify(valueOperations).increment("fraud:action:" +userId);
    }

    @Test
    @DisplayName("should set Redis TTL on first action")
    void setTtlOnFirstActionTest() {
        UUID userId = UUID.randomUUID();
        when(fraudRepository.findStatusByUserId(userId)).thenReturn(Optional.of(FraudStatus.CLEAN));
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L); // first action

        fraudDetectionService.evaluateUser(userId, "VISIT");

        verify(stringRedisTemplate).expire(eq("fraud:action:" +userId), eq(Duration.ofMinutes(10)));
    }

    @Test
    @DisplayName("should not reset Redis TTL on first action")
    void doNotResetTtlAfterFirstActionTest() {
        UUID userId = UUID.randomUUID();
        when(fraudRepository.findStatusByUserId(userId)).thenReturn(Optional.of(FraudStatus.CLEAN));
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(13L); // first action
        fraudDetectionService.evaluateUser(userId, "VISIT");

        verify(stringRedisTemplate, never()).expire(any(), any());
    }

    @Test
    @DisplayName("should NOT mark as fraud below threshold")
    void doNotMarkFraudBelowThresholdTest() {
        UUID userId = UUID.randomUUID();
        when(fraudRepository.findStatusByUserId(userId)).thenReturn(Optional.of(FraudStatus.CLEAN));
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(99L); // one below threshold

        fraudDetectionService.evaluateUser(userId, "VISIT");

        verify(fraudRepository, never()).upsertStatus(any(), any());
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("should mark as fraud above threshold")
    void markFraudAboveThresholdTest() {
        UUID userId = UUID.randomUUID();
        when(fraudRepository.findStatusByUserId(userId)).thenReturn(Optional.of(FraudStatus.CLEAN));
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(99L); // one below threshold

        fraudDetectionService.evaluateUser(userId, "VISIT");

        verify(fraudRepository, never()).upsertStatus(userId, FraudStatus.FRAUD);
    }

    @Test
    @DisplayName("should treat new user as CLEAN")
    void shouldTreatNewUserAsClean() {
        UUID userId = UUID.randomUUID();
        when(fraudRepository.findStatusByUserId(userId)).thenReturn(Optional.empty()); // no record yet
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);
        fraudDetectionService.evaluateUser(userId, "VISIT");
        verify(valueOperations).increment(anyString());
    }

    @Test
    @DisplayName("should update DB status to FRAUD")
    void updateDbStatusTest() {
        UUID userId = UUID.randomUUID();

        fraudDetectionService.markAsFraud(userId, 100);

        verify(fraudRepository).upsertStatus(userId, FraudStatus.FRAUD);
    }

    @Test
    @DisplayName("should log fraud event to DB")
    void shouldLogFraudEvent() {
        UUID userId = UUID.randomUUID();

        fraudDetectionService.markAsFraud(userId, 100);

        ArgumentCaptor<FraudEvent> captor = ArgumentCaptor.forClass(FraudEvent.class);
        verify(fraudRepository).logFraudEvent(captor.capture());

        FraudEvent logged = captor.getValue();
        assertThat(logged.getUserId()).isEqualTo(userId);
        assertThat(logged.getActionCount()).isEqualTo(100);
        assertThat(logged.getReason()).contains("100");
        assertThat(logged.getReason()).contains("10 minutes");
    }

    @Test
    @DisplayName("should publish fraud.user.marked Kafka event")
    void publishKafkaEventTest() {
        UUID userId = UUID.randomUUID();

        fraudDetectionService.markAsFraud(userId, 100);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq(FraudMarkedEvent.TOPIC),eq(userId.toString()),eventCaptor.capture());

        FraudMarkedEvent event = (FraudMarkedEvent) eventCaptor.getValue();
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.actionCount()).isEqualTo(100);
        assertThat(event.markedAt()).isNotNull();
    }

    @Test
    @DisplayName("should update DB, log event, AND publish Kafka — all three")
    void doAllOperationsInMarkAsFraudTest() {
        UUID userId = UUID.randomUUID();

        fraudDetectionService.markAsFraud(userId, 100);

        // All three must happen — this is a transaction
        verify(fraudRepository).upsertStatus(userId, FraudStatus.FRAUD);
        verify(fraudRepository).logFraudEvent(any(FraudEvent.class));
        verify(kafkaTemplate).send(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("should return CLEAN for unknown user")
    void returnCleanForUnknownUserTest() {
        UUID userId = UUID.randomUUID();
        when(fraudRepository.findStatusByUserId(userId)).thenReturn(Optional.empty());

        FraudStatus status = fraudDetectionService.getStatus(userId);

        assertThat(status).isEqualTo(FraudStatus.CLEAN);
    }

    @Test
    @DisplayName("should return FRAUD for known fraud user")
    void returnFraudForFraudUserTest() {
        UUID userId = UUID.randomUUID();
        when(fraudRepository.findStatusByUserId(userId)).thenReturn(Optional.of(FraudStatus.FRAUD));

        FraudStatus status = fraudDetectionService.getStatus(userId);

        assertThat(status).isEqualTo(FraudStatus.FRAUD);
    }


}

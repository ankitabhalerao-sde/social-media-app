package com.meet5.fraudservice.service;

import com.meet5.fraudservice.domain.FraudEvent;
import com.meet5.fraudservice.domain.FraudStatus;
import com.meet5.fraudservice.dto.FraudMarkedEvent;
import com.meet5.fraudservice.repository.FraudRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class FraudDetectionService {
    private final Logger LOGGER = LoggerFactory.getLogger(this.getClass());
    private static final String REDIS_PREFIX = "fraud:action:";

    @Value("${fraud.detection.action-limit}")
    private int actionLimit;

    @Value("${fraud.detection.window-minutes}")
    private int windowMinutes;

    private final FraudRepository fraudRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public FraudDetectionService(FraudRepository fraudRepository, StringRedisTemplate stringRedisTemplate, KafkaTemplate<String, Object> kafkaTemplate) {
        this.fraudRepository = fraudRepository;
        this.stringRedisTemplate = stringRedisTemplate;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Transactional
    public void evaluateUser(UUID userId, String actionType) {
        FraudStatus status = getStatus(userId);

        if(status == FraudStatus.FRAUD) {
            LOGGER.debug("User {} already marked FRAUD, skipping validation", userId);
            return;
        }

        String redisKey = REDIS_PREFIX + userId;
        Long actionCount = stringRedisTemplate.opsForValue().increment(redisKey);
        if(actionCount != null && actionCount == 1) {
            stringRedisTemplate.expire(redisKey, Duration.ofMinutes(windowMinutes));
        }
        if(actionCount != null && actionCount >= actionLimit) {
            markAsFraud(userId, actionCount.intValue());
        }
    }

    @Transactional
    public void markAsFraud(UUID userId, int actionCount) {
        LOGGER.warn("FRAUD DETECTED — user={} actionCount={}", userId, actionCount);
        String reason = "Exceeded " + actionLimit + " actions within " + windowMinutes + " minutes";

        fraudRepository.upsertStatus(userId, FraudStatus.FRAUD);

        fraudRepository.logFraudEvent(FraudEvent.builder()
                .userId(userId)
                .reason(reason)
                .actionCount(actionCount)
                .detectedAt(Instant.now())
                .build());

        FraudMarkedEvent event = new FraudMarkedEvent(userId, reason, actionCount, Instant.now());

        kafkaTemplate.send(FraudMarkedEvent.TOPIC, userId.toString(), event);
        LOGGER.info("Published fraud.user.marked for userId={}", userId);
    }

    public FraudStatus getStatus(UUID userId) {
        return fraudRepository.findStatusByUserId(userId).orElse(FraudStatus.CLEAN);
    }
}

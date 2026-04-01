package com.meet5.userservice.repository;

import com.meet5.userservice.domain.User;
import com.meet5.userservice.domain.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(UserRepository.class)
public class UserRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("user_test")
            .withUsername("postgres")
            .withPassword("postgres");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private UserRepository userRepository;

    private User buildProfile(String username) {
        return User.builder()
                .name("Test User")
                .username(username)
                .age(25)
                .extraFields(Map.of("city", "Berlin"))
                .status(UserStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("should insert and retrieve user by ID")
    void shouldInsertAndRetrieveById() {
        User saved = userRepository.insert(buildProfile("alice92"));

        Optional<User> found = userRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("alice92");
        assertThat(found.get().getName()).isEqualTo("Test User");
        assertThat(found.get().getAge()).isEqualTo(25);
    }

    @Test
    @DisplayName("should return empty when user not found by ID")
    void shouldReturnEmptyForUnknownId() {
        Optional<User> found = userRepository.findById(UUID.randomUUID());
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("should persist extraFields as JSONB")
    void shouldPersistExtraFields() {
        User saved = userRepository.insert(buildProfile("bob99"));

        Optional<User> found = userRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getExtraFields()).containsKey("city");
        assertThat(found.get().getExtraFields().get("city")).isEqualTo("Berlin");
    }

    @Test
    @DisplayName("should set createdAt and updatedAt on insert")
    void shouldSetTimestamps() {
        User saved = userRepository.insert(buildProfile("carol55"));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("should set status to ACTIVE on insert")
    void shouldSetStatusActive() {
        User saved = userRepository.insert(buildProfile("dave77"));

        assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("should find user by username")
    void shouldFindByUsername() {
        userRepository.insert(buildProfile("eve123"));

        Optional<User> found = userRepository.findByUsername("eve123");

        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("eve123");
    }

    @Test
    @DisplayName("should return empty for unknown username")
    void shouldReturnEmptyForUnknownUsername() {
        Optional<User> found = userRepository.findByUsername("nobody");
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("should update user status")
    void shouldUpdateStatus() {
        User saved = userRepository.insert(buildProfile("frank44"));

        userRepository.updateStatus(saved.getId(), UserStatus.FRAUD);

        Optional<User> found = userRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(UserStatus.FRAUD);
    }

    @Test
    @DisplayName("should bulk insert by jdbc batch")
    void shouldBulkInsert() {
        int savedCount = userRepository.bulkInsert(List.of(buildProfile("alice92"), buildProfile("bob")));
        assertThat(savedCount).isEqualTo(2);
    }

    @Test
    @DisplayName(("should skip duplicate user insert in batch"))
    void shouldSkipDuplicateUserInsert() {
        int savedCount = userRepository.bulkInsert(List.of(buildProfile("alice92"), buildProfile("alice92")));
        assertThat(savedCount).isEqualTo(1);
    }

    @Test
    @DisplayName("should return zero for empty list")
    void shouldReturnZeroForEmptyList() {
        int inserted = userRepository.bulkInsert(Collections.emptyList());
        assertThat(inserted).isEqualTo(0);
    }

    @Test
    @DisplayName("should handle batch according to cop threshold")
    void shouldHandleLargeBatchUsingCopy() {
        List<User> users = new java.util.ArrayList<>();
        for (int i = 0; i < 505; i++) {
            users.add(buildProfile("load_test_user_" + i));
        }

        int inserted = userRepository.bulkInsert(users);
        assertThat(inserted).isEqualTo(users.size());
    }

    @Test
    @DisplayName("should handle batch according to copy threshold")
    void shouldInsertBatchAndSkipDuplicateUsingCopy() {
        List<User> users = new java.util.ArrayList<>();
        for (int i = 0; i < 505; i++) {
            users.add(buildProfile("load_test_user_" + i));
        }
        users.add(buildProfile("load_test_user_" + 300));
        int inserted = userRepository.bulkInsert(users);
        assertThat(inserted).isEqualTo(users.size() - 1);
    }
}

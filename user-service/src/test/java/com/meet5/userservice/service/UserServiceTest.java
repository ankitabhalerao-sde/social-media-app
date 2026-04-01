package com.meet5.userservice.service;

import com.meet5.userservice.domain.User;
import com.meet5.userservice.domain.UserStatus;
import com.meet5.userservice.dto.BulkInsertResponse;
import com.meet5.userservice.dto.UserRequest;
import com.meet5.userservice.dto.UserResponse;
import com.meet5.userservice.exception.DuplicateUsernameException;
import com.meet5.userservice.exception.UserNotFoundException;
import com.meet5.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private User sampleUser;
    private UserRequest sampleRequest;

    @BeforeEach
    void setUp() {
        sampleRequest = new UserRequest("Alice Bob", "alice92", 28, Map.of("city", "Berlin"));
        sampleUser = User.builder()
                .id(UUID.randomUUID())
                .name("Alice Bob")
                .username("alice92")
                .age(28)
                .extraFields(Map.of("city", "Berlin"))
                .status(UserStatus.ACTIVE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("should create user successfully when username is unique")
    void shouldCreateUserSuccessfully() {
        when(userRepository.findByUsername("alice92")).thenReturn(Optional.empty());
        when(userRepository.insert(any(User.class))).thenReturn(sampleUser);

        UserResponse response = userService.createUser(sampleRequest);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Alice Bob");
        assertThat(response.username()).isEqualTo("alice92");
        assertThat(response.age()).isEqualTo(28);
        assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("should throw DuplicateUsernameException when username already taken")
    void shouldThrowWhenUsernameDuplicate() {
        when(userRepository.findByUsername("alice92")).thenReturn(Optional.of(sampleUser));

        assertThatThrownBy(() -> userService.createUser(sampleRequest))
                .isInstanceOf(DuplicateUsernameException.class)
                .hasMessageContaining("alice92");
    }

    @Test
    @DisplayName("should pass extraFields through to repository")
    void shouldPassExtraFieldsToRepository() {
        when(userRepository.findByUsername(any())).thenReturn(Optional.empty());
        when(userRepository.insert(any())).thenReturn(sampleUser);

        userService.createUser(sampleRequest);

        verify(userRepository).insert(argThat(profile ->
                profile.getExtraFields().containsKey("city") &&
                        profile.getExtraFields().get("city").equals("Berlin")
        ));
    }

    @Test
    @DisplayName("should use empty map when extraFields is null")
    void shouldUseEmptyMapWhenExtraFieldsNull() {
        UserRequest requestWithNullFields = new UserRequest("Bob","bob99", 25, null);

        when(userRepository.findByUsername(any())).thenReturn(Optional.empty());
        when(userRepository.insert(any())).thenReturn(sampleUser);

        userService.createUser(requestWithNullFields);

        verify(userRepository).insert(argThat(profile ->
                profile.getExtraFields() != null &&
                        profile.getExtraFields().isEmpty()
        ));
    }

    @Test
    @DisplayName("should call repository insert exactly once")
    void shouldCallRepositoryInsertOnce() {
        when(userRepository.findByUsername(any())).thenReturn(Optional.empty());
        when(userRepository.insert(any())).thenReturn(sampleUser);

        userService.createUser(sampleRequest);

        verify(userRepository, times(1)).insert(any());
    }

    @Test
    @DisplayName("should return user when found")
    void shouldReturnUserWhenFound() {
        UUID userId = sampleUser.getId();
        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));

        UserResponse response = userService.getUserById(userId);

        assertThat(response.id()).isEqualTo(userId);
        assertThat(response.username()).isEqualTo("alice92");
    }

    @Test
    @DisplayName("should throw UserNotFoundException when user not found")
    void shouldThrowWhenUserNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(userRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(unknownId))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining(unknownId.toString());
    }

    @Test
    @DisplayName("should return bulk insert response with response count")
    public void shouldReturnBulkInsertCount() {
        List<UserRequest> request = List.of(
                new UserRequest("Alica", "alica1new", 19, null),
                new UserRequest("Bob", "booby", 24, null)
        );

        when(userRepository.bulkInsert(any())).thenReturn(2);

        BulkInsertResponse response = userService.bulkCreateUsers(request);
        assertThat(response).isNotNull();
        assertThat(response.inserted()).isEqualTo(2);
        assertThat(response.skipped()).isEqualTo(0);
    }

    @Test
    @DisplayName("should return bulk insert and skipped count response with response count")
    public void shouldReturnBulkInsertAndSkippedCount() {
        List<UserRequest> request = List.of(
                new UserRequest("Alica", "alica1new", 19, null),
                new UserRequest("Bob", "booby", 24, null),
                new UserRequest("Alica", "alica1new", 19, null)
                );

        when(userRepository.bulkInsert(any())).thenReturn(2);

        BulkInsertResponse response = userService.bulkCreateUsers(request);
        assertThat(response).isNotNull();
        assertThat(response.inserted()).isEqualTo(2);
        assertThat(response.skipped()).isEqualTo(1);
    }

    @Test
    @DisplayName("should record duration in response")
    void shouldRecordDurationInResponse() {
        when(userRepository.bulkInsert(any())).thenReturn(1);
        List<UserRequest> request = List.of(
                new UserRequest("Alica", "alica1new", 19, null));
        BulkInsertResponse response = userService.bulkCreateUsers(request);
        assertThat(response).isNotNull();
        assertThat(response.durationMs()).isGreaterThanOrEqualTo(0);
    }


}

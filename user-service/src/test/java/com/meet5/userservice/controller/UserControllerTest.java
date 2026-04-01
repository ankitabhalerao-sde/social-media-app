package com.meet5.userservice.controller;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meet5.userservice.domain.UserStatus;
import com.meet5.userservice.dto.BulkInsertResponse;
import com.meet5.userservice.dto.UserRequest;
import com.meet5.userservice.dto.UserResponse;
import com.meet5.userservice.exception.DuplicateUsernameException;
import com.meet5.userservice.exception.UserNotFoundException;
import com.meet5.userservice.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@WebMvcTest
public class UserControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    private final UUID userId = UUID.randomUUID();

    private UserResponse sampleResponse() {
        return new UserResponse(
                userId,
                "Alice Bob",
                "alice92",
                28,
                UserStatus.ACTIVE,
                Map.of("city", "Berlin"),
                Instant.now(),
                Instant.now()
        );
    }
    @Test
    @DisplayName("should return 201 with valid request")
    void shouldReturn201WithValidRequest() throws Exception {
        when(userService.createUser(any())).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UserRequest("Alice Bob", "alice92", 28, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("alice92"))
                .andExpect(jsonPath("$.name").value("Alice Bob"))
                .andExpect(jsonPath("$.age").value(28))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("should return 400 when name is blank")
    void shouldReturn400WhenNameBlank() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UserRequest("", "alice92", 28, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("should return 400 when username is blank")
    void shouldReturn400WhenUsernameBlank() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UserRequest("Alice", "", 28, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("should return 400 when age is below minimum")
    void shouldReturn400WhenAgeBelowMinimum() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UserRequest("Alice", "alice92", 10, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("should return 400 when age exceeds maximum")
    void shouldReturn400WhenAgeExceedsMaximum() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UserRequest("Alice", "alice92", 200, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("should return 400 when age is null")
    void shouldReturn400WhenAgeNull() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"name":"Alice",
                        "username":"alice92",
                        "age":null }
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("should return 409 when username already taken")
    void shouldReturn409WhenUsernameDuplicate() throws Exception {
        when(userService.createUser(any())).thenThrow(new DuplicateUsernameException("alice92"));

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UserRequest("Alice", "alice92", 28, null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("USERNAME_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("should return 400 when request body is missing")
    void shouldReturn400WhenBodyMissing() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("should return 200 with user when found")
    void shouldReturn200WhenFound() throws Exception {
        when(userService.getUserById(userId)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/users/{id}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.username").value("alice92"));
    }

    @Test
    @DisplayName("should return 404 when user not found")
    void shouldReturn404WhenNotFound() throws Exception {
        when(userService.getUserById(any())).thenThrow(new UserNotFoundException(userId));

        mockMvc.perform(get("/api/v1/users/{id}", userId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("should return 400 for invalid UUID format")
    void shouldReturn400ForInvalidUuid() throws Exception {
        mockMvc.perform(get("/api/v1/users/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("should return 201 for valid batch")
    void shouldReturn201ForValidBatch() throws Exception {
        when(userService.bulkCreateUsers(any())).thenReturn(new BulkInsertResponse(3,0,3,50L));
        List<UserRequest> request = List.of(
                new UserRequest("Alica", "alica1new", 19, null),
                new UserRequest("Bob", "booby", 24, null),
                new UserRequest("cathi", "cathina", 49, null)
        );

        mockMvc.perform(post("/api/v1/users/bulk")
                .content(objectMapper.writeValueAsString(request))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.inserted").value(3))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.skipped").value(0));
    }

    @Test
    @DisplayName("should return 400 when batch is empty")
    public void shouldReturn400WhenBatchIsEmpty() throws Exception {
       mockMvc.perform(get("/api/v1/users/bulk")
               .content(objectMapper.writeValueAsString(List.of()))
                       .contentType(MediaType.APPLICATION_JSON))
               .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return inserted and skipped count")
    public void shouldReturnInsertedAndSkippedCount() throws Exception {
        when(userService.bulkCreateUsers(any())).thenReturn(new BulkInsertResponse(2,1,3,50L));
        List<UserRequest> request = List.of(
                new UserRequest("Alica", "alica1new", 19, null),
                new UserRequest("Bob", "booby", 24, null),
                new UserRequest("Alica", "alica1new", 19, null)
        );

        mockMvc.perform(post("/api/v1/users/bulk")
                        .content(objectMapper.writeValueAsString(request))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.inserted").value(2))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.skipped").value(1));
    }
}

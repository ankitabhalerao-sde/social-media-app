package com.meet5.interactionservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meet5.interactionservice.dto.LikeRequest;
import com.meet5.interactionservice.dto.LikeResponse;
import com.meet5.interactionservice.dto.VisitRequest;
import com.meet5.interactionservice.dto.VisitResponse;
import com.meet5.interactionservice.dto.VisitorSummary;
import com.meet5.interactionservice.exception.UserBlockedException;
import com.meet5.interactionservice.service.InteractionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InteractionController.class)
@DisplayName("InteractionController")
class InteractionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private InteractionService interactionService;

    private final UUID visitorId = UUID.randomUUID();
    private final UUID visitedId = UUID.randomUUID();
    private final UUID likerId = UUID.randomUUID();
    private final UUID likedId = UUID.randomUUID();

    // ── POST /api/v1/interactions/visit ───────────────────────────


    @Test
    @DisplayName("should return 201 for valid visit request")
    void shouldReturn201ForValidRequest() throws Exception {
        when(interactionService.recordVisit(any()))
                .thenReturn(new VisitResponse(visitorId, visitedId, 1, Instant.now()));

        mockMvc.perform(post("/api/v1/interactions/visit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new VisitRequest(visitorId, visitedId)
                        )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.visitorId").value(visitorId.toString()))
                .andExpect(jsonPath("$.visitedId").value(visitedId.toString()))
                .andExpect(jsonPath("$.visitCount").value(1));
    }

    @Test
    @DisplayName("should return 400 when visitorId is missing")
    void shouldReturn400WhenVisitorIdMissing() throws Exception {
        mockMvc.perform(post("/api/v1/interactions/visit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"visitedId": "%s"}
                                """.formatted(visitedId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("should return 400 when visitedId is missing")
    void shouldReturn400WhenVisitedIdMissing() throws Exception {
        mockMvc.perform(post("/api/v1/interactions/visit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"visitorId": "%s"}
                                """.formatted(visitorId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("should return 403 when visitor is blocked")
    void shouldReturn403WhenVisitorBlocked() throws Exception {
        when(interactionService.recordVisit(any()))
                .thenThrow(new UserBlockedException(visitorId));

        mockMvc.perform(post("/api/v1/interactions/visit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new VisitRequest(visitorId, visitedId)
                        )))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("User is blocked and detected as fraud"));
    }

    @Test
    @DisplayName("should return 400 when request body is empty")
    void shouldReturn400WhenBodyEmpty() throws Exception {
        mockMvc.perform(post("/api/v1/interactions/visit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }


    // ── POST /api/v1/interactions/like ────────────────────────────

    @Test
    @DisplayName("should return 201 for new like")
    void shouldReturn201ForNewLike() throws Exception {
        when(interactionService.recordLike(any()))
                .thenReturn(new LikeResponse(likerId, likedId, true, Instant.now()));

        mockMvc.perform(post("/api/v1/interactions/like")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LikeRequest(likerId, likedId)
                        )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isNew").value(true))
                .andExpect(jsonPath("$.likerId").value(likerId.toString()));
    }

    @Test
    @DisplayName("should return 201 with isNew false for duplicate like")
    void shouldReturn201WithIsNewFalseForDuplicate() throws Exception {
        when(interactionService.recordLike(any()))
                .thenReturn(new LikeResponse(likerId, likedId, false, Instant.now()));

        mockMvc.perform(post("/api/v1/interactions/like")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LikeRequest(likerId, likedId)
                        )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isNew").value(false));
    }

    @Test
    @DisplayName("should return 403 when liker is blocked")
    void shouldReturn403WhenLikerBlocked() throws Exception {
        when(interactionService.recordLike(any()))
                .thenThrow(new UserBlockedException(likerId));

        mockMvc.perform(post("/api/v1/interactions/like")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LikeRequest(likerId, likedId)
                        )))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("User is blocked and detected as fraud"));
    }

    @Test
    @DisplayName("should return 400 when likerId is missing")
    void shouldReturn400WhenLikerIdMissing() throws Exception {
        mockMvc.perform(post("/api/v1/interactions/like")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"likedId": "%s"}
                                """.formatted(likedId)))
                .andExpect(status().isBadRequest());
    }


    // ── GET /api/v1/interactions/{userId}/visitors ────────────────

    @Test
    @DisplayName("should return 200 with visitors list")
    void shouldReturn200WithVisitors() throws Exception {
        UUID userId = UUID.randomUUID();
        List<VisitorSummary> visitors = List.of(
                new VisitorSummary(UUID.randomUUID(), 5, Instant.now(), Instant.now()),
                new VisitorSummary(UUID.randomUUID(), 2, Instant.now(), Instant.now())
        );

        when(interactionService.getVisitors(any(), any(Integer.class), any(Integer.class)))
                .thenReturn(visitors);

        mockMvc.perform(get("/api/v1/interactions/{userId}/visitors", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("should return 200 with empty list when no visitors")
    void shouldReturn200WithEmptyList() throws Exception {
        UUID userId = UUID.randomUUID();
        when(interactionService.getVisitors(any(), any(Integer.class), any(Integer.class)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/interactions/{userId}/visitors", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("should return 400 for invalid UUID")
    void shouldReturn400ForInvalidUuid() throws Exception {
        mockMvc.perform(get("/api/v1/interactions/{userId}/visitors", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("should accept custom page and size parameters")
    void shouldAcceptPageAndSizeParams() throws Exception {
        UUID userId = UUID.randomUUID();
        when(interactionService.getVisitors(any(), any(Integer.class), any(Integer.class)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/interactions/{userId}/visitors", userId)
                        .param("page", "2")
                        .param("size", "10"))
                .andExpect(status().isOk());
    }

}
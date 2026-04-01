package com.meet5.userservice.controller;

import com.meet5.userservice.dto.BulkInsertResponse;
import com.meet5.userservice.dto.UserRequest;
import com.meet5.userservice.dto.UserResponse;
import com.meet5.userservice.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "User", description = "User Profile Management")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }
    @PostMapping
    @Operation(
            summary = "Create a user",
            description = "Creates a new user. Username must be unique."
    )
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody UserRequest userRequest) {
        UserResponse userResponse = userService.createUser(userRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(userResponse);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "get a user",
            description = "Get a user by Id"
    )
    public ResponseEntity<UserResponse> getUserById(@PathVariable UUID id) {
        UserResponse userResponse = userService.getUserById(id);
        return ResponseEntity.ok(userResponse);
    }

    @PostMapping("/bulk")
    @Operation(
            summary = "Bulk insert users",
            description = "Inserts user profiles in one request. Duplicate usernames are skipped."
    )
    public ResponseEntity<BulkInsertResponse> bulkInsertUsers( @RequestBody List<@Valid UserRequest> userRequests) {
        BulkInsertResponse response = userService.bulkCreateUsers(userRequests);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}

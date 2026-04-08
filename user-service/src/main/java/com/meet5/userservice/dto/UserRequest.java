package com.meet5.userservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.util.Map;

public record UserRequest(

        @Schema(example = "test_user_123")
        @NotBlank(message = "name is required")
        @Size(min = 2, max = 100)
        String name,

        @Schema(example = "test_user_123")
        @NotBlank(message = "username is required")
        @Size(min = 3, max = 50)
        @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "username can only contain letters, numbers and underscores")
        String username,

        @NotNull(message = "age is required")
        @Min(value = 16, message = "age must be at least 16")
        @Max(value = 120, message = "age must not exceed 120")
        Integer age,

        Map<String, Object> extraFields
) {
}


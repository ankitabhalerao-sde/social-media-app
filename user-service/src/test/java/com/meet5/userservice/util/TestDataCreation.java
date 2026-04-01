package com.meet5.userservice.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meet5.userservice.domain.User;
import com.meet5.userservice.domain.UserStatus;
import com.meet5.userservice.dto.UserRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Delete later
public class TestDataCreation {
    public static void main(String[] args) {
        ObjectMapper objectMapper = new ObjectMapper();
        TestDataCreation testDataCreation = new TestDataCreation();
        List<User> request = new ArrayList<>();
        for (int i = 0; i < 5005; i++) {
            request.add(testDataCreation.buildProfile("load_test_user_" + i));
        }
        try {
            System.out.println(objectMapper.writeValueAsString(request));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private User buildProfile(String username) {
        return User.builder()
                .name("Test User")
                .username(username)
                .age(25)
                .extraFields(Map.of("city", "Berlin"))
                .status(UserStatus.ACTIVE)
                .build();
    }
}

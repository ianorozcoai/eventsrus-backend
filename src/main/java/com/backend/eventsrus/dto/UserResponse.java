package com.backend.eventsrus.dto;

import com.backend.eventsrus.enums.Role;
import com.backend.eventsrus.model.User;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class UserResponse {

    private Long id;
    private String email;
    private Role role;
    private String firstName;
    private String lastName;
    private String mobileNumber;
    private Instant createdAt;

    public static UserResponse from(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .role(user.getRole())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .mobileNumber(user.getMobileNumber())
                .createdAt(user.getCreatedAt())
                .build();
    }
}

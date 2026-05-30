package com.iliaspiotopoulos.bibliocore.dto.response;

public record AuthResponse(
        String accessToken,
        String tokenType,
        Long expiresIn,
        UserResponse user
) {
    public AuthResponse(String accessToken, Long expiresIn, UserResponse user) {
        this(accessToken, "Bearer", expiresIn, user);
    }
}
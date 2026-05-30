package com.iliaspiotopoulos.bibliocore.dto.response;

import com.iliaspiotopoulos.bibliocore.model.enums.Role;

public record UserResponse(
        Long id,
        String email,
        Role role
) {}
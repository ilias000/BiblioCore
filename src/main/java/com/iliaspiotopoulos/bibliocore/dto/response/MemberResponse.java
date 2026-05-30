package com.iliaspiotopoulos.bibliocore.dto.response;

import com.iliaspiotopoulos.bibliocore.model.enums.MembershipStatus;

import java.time.Instant;

public record MemberResponse(
        Long id,
        String name,
        String email,
        MembershipStatus membershipStatus,
        Integer loanLimit,
        Integer activeLoans,
        Instant createdAt
) {}
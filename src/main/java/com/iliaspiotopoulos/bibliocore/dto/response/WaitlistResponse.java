package com.iliaspiotopoulos.bibliocore.dto.response;

import com.iliaspiotopoulos.bibliocore.model.enums.WaitlistStatus;

import java.time.Instant;

public record WaitlistResponse(
        Long id,
        Long memberId,
        String memberName,
        Long bookId,
        String bookTitle,
        WaitlistStatus status,
        Integer position,
        Instant createdAt,
        Instant notifiedAt
) {}
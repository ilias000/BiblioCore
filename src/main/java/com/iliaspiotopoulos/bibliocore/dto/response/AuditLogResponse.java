package com.iliaspiotopoulos.bibliocore.dto.response;

import java.time.Instant;

public record AuditLogResponse(
        Long id,
        String entityType,
        Long entityId,
        String action,
        String fieldName,
        String oldValue,
        String newValue,
        Long performedById,
        String performedByEmail,
        Instant performedAt
) {}
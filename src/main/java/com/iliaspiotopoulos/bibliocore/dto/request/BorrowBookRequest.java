package com.iliaspiotopoulos.bibliocore.dto.request;

import jakarta.validation.constraints.NotNull;

public record BorrowBookRequest(
        @NotNull(message = "Book ID is required")
        Long bookId
) {}
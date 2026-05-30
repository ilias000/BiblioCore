package com.iliaspiotopoulos.bibliocore.dto.response;

import com.iliaspiotopoulos.bibliocore.model.enums.LoanStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LoanResponse(
        Long id,
        Long memberId,
        String memberName,
        Long bookId,
        String bookTitle,
        String bookIsbn,
        LoanStatus status,
        LocalDate borrowDate,
        LocalDate dueDate,
        LocalDate returnDate,
        BigDecimal fineAmount,
        Boolean overdue
) {}
package com.iliaspiotopoulos.bibliocore.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReturnLoanResponse(
        Long loanId,
        String bookTitle,
        LocalDate borrowDate,
        LocalDate dueDate,
        LocalDate returnDate,
        Integer daysOverdue,
        BigDecimal fineAmount,
        String message
) {}
package com.iliaspiotopoulos.bibliocore.dto.response;

import java.time.LocalDate;
import java.util.List;

public record BookResponse(
        Long id,
        String isbn,
        String title,
        List<String> authors,
        String genre,
        LocalDate publicationDate,
        Integer totalCopies,
        Integer availableCopies,
        Boolean available,
        Integer waitlistCount
) {}
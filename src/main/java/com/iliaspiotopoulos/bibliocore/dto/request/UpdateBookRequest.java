package com.iliaspiotopoulos.bibliocore.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record UpdateBookRequest(
        @Size(max = 500, message = "Title must not exceed 500 characters")
        String title,

        List<String> authors,

        @Size(max = 100, message = "Genre must not exceed 100 characters")
        String genre,

        LocalDate publicationDate,

        @Min(value = 0, message = "Total copies cannot be negative")
        Integer totalCopies
) {}
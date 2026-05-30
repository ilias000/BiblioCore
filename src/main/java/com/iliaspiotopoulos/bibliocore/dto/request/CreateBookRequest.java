package com.iliaspiotopoulos.bibliocore.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record CreateBookRequest(
        @NotBlank(message = "ISBN is required")
        @Size(max = 20, message = "ISBN must not exceed 20 characters")
        String isbn,

        @NotBlank(message = "Title is required")
        @Size(max = 500, message = "Title must not exceed 500 characters")
        String title,

        @NotEmpty(message = "At least one author is required")
        List<@NotBlank(message = "Author name cannot be blank") String> authors,

        @Size(max = 100, message = "Genre must not exceed 100 characters")
        String genre,

        LocalDate publicationDate,

        @Min(value = 1, message = "Total copies must be at least 1")
        Integer totalCopies
) {
    public CreateBookRequest {
        if (totalCopies == null) {
            totalCopies = 1;
        }
    }
}
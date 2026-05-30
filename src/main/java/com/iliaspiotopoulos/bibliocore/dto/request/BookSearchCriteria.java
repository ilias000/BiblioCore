package com.iliaspiotopoulos.bibliocore.dto.request;

public record BookSearchCriteria(
        String title,
        String author,
        String genre,
        Boolean available
) {}
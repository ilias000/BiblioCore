package com.iliaspiotopoulos.bibliocore.mapper;

import com.iliaspiotopoulos.bibliocore.dto.response.BookResponse;
import com.iliaspiotopoulos.bibliocore.model.entity.Author;
import com.iliaspiotopoulos.bibliocore.model.entity.Book;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface BookMapper {

    @Mapping(target = "authors", source = "authors", qualifiedByName = "authorsToStrings")
    @Mapping(target = "available", expression = "java(book.isAvailable())")
    @Mapping(target = "waitlistCount", ignore = true)
    BookResponse toResponse(Book book);

    default BookResponse toResponse(Book book, int waitlistCount) {
        BookResponse base = toResponse(book);
        return new BookResponse(
                base.id(),
                base.isbn(),
                base.title(),
                base.authors(),
                base.genre(),
                base.publicationDate(),
                base.totalCopies(),
                base.availableCopies(),
                base.available(),
                waitlistCount
        );
    }

    @Named("authorsToStrings")
    default List<String> authorsToStrings(Set<Author> authors) {
        if (authors == null) {
            return List.of();
        }
        return authors.stream()
                .map(Author::getName)
                .sorted()
                .collect(Collectors.toList());
    }
}
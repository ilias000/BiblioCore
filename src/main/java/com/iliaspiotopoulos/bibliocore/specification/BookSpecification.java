package com.iliaspiotopoulos.bibliocore.specification;

import com.iliaspiotopoulos.bibliocore.dto.request.BookSearchCriteria;
import com.iliaspiotopoulos.bibliocore.model.entity.Author;
import com.iliaspiotopoulos.bibliocore.model.entity.Book;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

public final class BookSpecification {

    private BookSpecification() {
    }

    public static Specification<Book> buildSpecification(BookSearchCriteria criteria) {
        return Specification
                .where(notDeleted())
                .and(titleContains(criteria.title()))
                .and(authorContains(criteria.author()))
                .and(genreEquals(criteria.genre()))
                .and(isAvailable(criteria.available()));
    }

    private static Specification<Book> noOp() {
        return (root, query, cb) -> cb.conjunction();
    }

    private static Specification<Book> notDeleted() {
        return (root, query, cb) -> cb.equal(root.get("deleted"), false);
    }

    private static Specification<Book> titleContains(String title) {
        if (title == null || title.isBlank()) {
            return noOp();
        }
        return (root, query, cb) ->
                cb.like(cb.lower(root.get("title")), "%" + title.toLowerCase() + "%");
    }

    private static Specification<Book> authorContains(String authorName) {
        if (authorName == null || authorName.isBlank()) {
            return noOp();
        }
        return (root, query, cb) -> {
            query.distinct(true);
            Join<Book, Author> authorsJoin = root.join("authors", JoinType.LEFT);
            return cb.like(cb.lower(authorsJoin.get("name")), "%" + authorName.toLowerCase() + "%");
        };
    }

    private static Specification<Book> genreEquals(String genre) {
        if (genre == null || genre.isBlank()) {
            return noOp();
        }
        return (root, query, cb) ->
                cb.equal(cb.lower(root.get("genre")), genre.toLowerCase());
    }

    private static Specification<Book> isAvailable(Boolean available) {
        if (available == null) {
            return noOp();
        }
        return (root, query, cb) -> {
            if (available) {
                return cb.greaterThan(root.get("availableCopies"), 0);
            } else {
                return cb.equal(root.get("availableCopies"), 0);
            }
        };
    }
}
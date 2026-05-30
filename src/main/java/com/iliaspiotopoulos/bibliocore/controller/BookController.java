package com.iliaspiotopoulos.bibliocore.controller;

import com.iliaspiotopoulos.bibliocore.dto.request.BookSearchCriteria;
import com.iliaspiotopoulos.bibliocore.dto.request.CreateBookRequest;
import com.iliaspiotopoulos.bibliocore.dto.request.UpdateBookRequest;
import com.iliaspiotopoulos.bibliocore.dto.response.BookResponse;
import com.iliaspiotopoulos.bibliocore.service.BookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/books")
@Tag(name = "Books", description = "Book catalog management")
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    @GetMapping
    @Operation(summary = "Search books", description = "Search and filter books with pagination. Public access.")
    public ResponseEntity<Page<BookResponse>> searchBooks(
            @Parameter(description = "Filter by title (partial match)")
            @RequestParam(required = false) String title,
            @Parameter(description = "Filter by author name (partial match)")
            @RequestParam(required = false) String author,
            @Parameter(description = "Filter by genre (exact match)")
            @RequestParam(required = false) String genre,
            @Parameter(description = "Filter by availability")
            @RequestParam(required = false) Boolean available,
            @PageableDefault(size = 20, sort = "title", direction = Sort.Direction.ASC) Pageable pageable) {

        BookSearchCriteria criteria = new BookSearchCriteria(title, author, genre, available);
        Page<BookResponse> books = bookService.searchBooks(criteria, pageable);
        return ResponseEntity.ok(books);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get book by ID", description = "Retrieve a single book by its ID. Public access.")
    public ResponseEntity<BookResponse> getBookById(@PathVariable Long id) {
        BookResponse book = bookService.getBookById(id);
        return ResponseEntity.ok(book);
    }

    @GetMapping("/isbn/{isbn}")
    @Operation(summary = "Get book by ISBN", description = "Retrieve a single book by its ISBN. Public access.")
    public ResponseEntity<BookResponse> getBookByIsbn(@PathVariable String isbn) {
        BookResponse book = bookService.getBookByIsbn(isbn);
        return ResponseEntity.ok(book);
    }

    @PostMapping
    @Operation(summary = "Create book", description = "Add a new book to the catalog. Admin only.",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<BookResponse> createBook(@Valid @RequestBody CreateBookRequest request) {
        BookResponse book = bookService.createBook(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(book);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update book", description = "Update an existing book. Admin only.",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<BookResponse> updateBook(
            @PathVariable Long id,
            @Valid @RequestBody UpdateBookRequest request) {
        BookResponse book = bookService.updateBook(id, request);
        return ResponseEntity.ok(book);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete book", description = "Soft-delete a book. Admin only. Fails if active loans exist.",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Void> deleteBook(@PathVariable Long id) {
        bookService.deleteBook(id);
        return ResponseEntity.noContent().build();
    }
}
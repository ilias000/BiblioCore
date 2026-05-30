package com.iliaspiotopoulos.bibliocore.service;

import com.iliaspiotopoulos.bibliocore.dto.request.BookSearchCriteria;
import com.iliaspiotopoulos.bibliocore.dto.request.CreateBookRequest;
import com.iliaspiotopoulos.bibliocore.dto.request.UpdateBookRequest;
import com.iliaspiotopoulos.bibliocore.dto.response.BookResponse;
import com.iliaspiotopoulos.bibliocore.exception.BusinessRuleException;
import com.iliaspiotopoulos.bibliocore.exception.DuplicateResourceException;
import com.iliaspiotopoulos.bibliocore.exception.ResourceNotFoundException;
import com.iliaspiotopoulos.bibliocore.mapper.BookMapper;
import com.iliaspiotopoulos.bibliocore.model.entity.Author;
import com.iliaspiotopoulos.bibliocore.model.entity.Book;
import com.iliaspiotopoulos.bibliocore.repository.AuthorRepository;
import com.iliaspiotopoulos.bibliocore.repository.BookRepository;
import com.iliaspiotopoulos.bibliocore.repository.WaitlistRepository;
import com.iliaspiotopoulos.bibliocore.specification.BookSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class BookService {

    private static final Logger log = LoggerFactory.getLogger(BookService.class);

    private final BookRepository bookRepository;
    private final AuthorRepository authorRepository;
    private final WaitlistRepository waitlistRepository;
    private final BookMapper bookMapper;
    private final AuditService auditService;

    public BookService(BookRepository bookRepository,
                       AuthorRepository authorRepository,
                       WaitlistRepository waitlistRepository,
                       BookMapper bookMapper,
                       AuditService auditService) {
        this.bookRepository = bookRepository;
        this.authorRepository = authorRepository;
        this.waitlistRepository = waitlistRepository;
        this.bookMapper = bookMapper;
        this.auditService = auditService;
    }

    @Transactional
    public BookResponse createBook(CreateBookRequest request) {
        log.info("Creating book with ISBN: {}", request.isbn());

        if (bookRepository.existsByIsbn(request.isbn())) {
            throw new DuplicateResourceException("Book", "ISBN", request.isbn());
        }

        Set<Author> authors = resolveAuthors(request.authors());

        Book book = Book.builder()
                .isbn(request.isbn())
                .title(request.title())
                .genre(request.genre())
                .publicationDate(request.publicationDate())
                .totalCopies(request.totalCopies())
                .availableCopies(request.totalCopies())
                .authors(authors)
                .build();

        book = bookRepository.save(book);
        auditService.logAction("BOOK", book.getId(), "CREATED");

        log.info("Book created with ID: {}", book.getId());
        return toResponseWithWaitlist(book);
    }

    @Transactional(readOnly = true)
    public Page<BookResponse> searchBooks(BookSearchCriteria criteria, Pageable pageable) {
        Specification<Book> spec = BookSpecification.buildSpecification(criteria);
        return bookRepository.findAll(spec, pageable)
                .map(this::toResponseWithWaitlist);
    }

    @Transactional(readOnly = true)
    public BookResponse getBookById(Long id) {
        Book book = findBookById(id);
        return toResponseWithWaitlist(book);
    }

    @Transactional(readOnly = true)
    public BookResponse getBookByIsbn(String isbn) {
        Book book = bookRepository.findByIsbnAndDeletedFalse(isbn)
                .orElseThrow(() -> new ResourceNotFoundException("Book", "ISBN", isbn));
        return toResponseWithWaitlist(book);
    }

    @Transactional
    public BookResponse updateBook(Long id, UpdateBookRequest request) {
        Book book = findBookById(id);
        log.info("Updating book: {}", id);

        if (request.title() != null && !request.title().isBlank()) {
            book.setTitle(request.title());
        }

        if (request.genre() != null) {
            book.setGenre(request.genre());
        }

        if (request.publicationDate() != null) {
            book.setPublicationDate(request.publicationDate());
        }

        if (request.authors() != null && !request.authors().isEmpty()) {
            Set<Author> authors = resolveAuthors(request.authors());
            book.getAuthors().clear();
            book.getAuthors().addAll(authors);
        }

        if (request.totalCopies() != null) {
            int currentLoans = bookRepository.countActiveLoans(id);
            if (request.totalCopies() < currentLoans) {
                throw new BusinessRuleException("INSUFFICIENT_COPIES",
                        "Cannot reduce total copies below the number of active loans (" + currentLoans + ")");
            }
            int diff = request.totalCopies() - book.getTotalCopies();
            book.setTotalCopies(request.totalCopies());
            book.setAvailableCopies(book.getAvailableCopies() + diff);
        }

        book = bookRepository.save(book);
        auditService.logAction("BOOK", id, "UPDATED");

        log.info("Book {} updated successfully", id);
        return toResponseWithWaitlist(book);
    }

    @Transactional
    public void deleteBook(Long id) {
        Book book = findBookById(id);

        int activeLoans = bookRepository.countActiveLoans(id);
        if (activeLoans > 0) {
            throw new BusinessRuleException("ACTIVE_LOANS_EXIST",
                    "Cannot delete book with " + activeLoans + " active loan(s)");
        }

        book.setDeleted(true);
        bookRepository.save(book);
        auditService.logAction("BOOK", id, "DELETED");
        log.info("Book {} soft-deleted", id);
    }

    private Book findBookById(Long id) {
        return bookRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Book", "id", id));
    }

    private Set<Author> resolveAuthors(List<String> authorNames) {
        Set<Author> authors = new HashSet<>();
        for (String name : authorNames) {
            Author author = authorRepository.findByName(name)
                    .orElseGet(() -> authorRepository.save(Author.builder().name(name).build()));
            authors.add(author);
        }
        return authors;
    }

    private BookResponse toResponseWithWaitlist(Book book) {
        int waitlistCount = waitlistRepository.countWaitingByBookId(book.getId());
        return bookMapper.toResponse(book, waitlistCount);
    }
}
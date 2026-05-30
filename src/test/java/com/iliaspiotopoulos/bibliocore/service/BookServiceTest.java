package com.iliaspiotopoulos.bibliocore.service;

import com.iliaspiotopoulos.bibliocore.dto.request.CreateBookRequest;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BookService Unit Tests")
class BookServiceTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private AuthorRepository authorRepository;

    @Mock
    private WaitlistRepository waitlistRepository;

    @Mock
    private BookMapper bookMapper;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private BookService bookService;

    private Book testBook;
    private Author testAuthor;

    @BeforeEach
    void setUp() {
        testAuthor = Author.builder()
                .id(1L)
                .name("Test Author")
                .build();

        testBook = Book.builder()
                .id(1L)
                .isbn("978-0-123456-78-9")
                .title("Test Book")
                .genre("Fiction")
                .publicationDate(LocalDate.of(2024, 1, 15))
                .totalCopies(5)
                .availableCopies(5)
                .deleted(false)
                .build();
    }

    @Nested
    @DisplayName("Create Book Tests")
    class CreateBookTests {

        @Test
        @DisplayName("Should successfully create a new book")
        void createBook_Success() {
            // Given
            CreateBookRequest request = new CreateBookRequest(
                    "978-0-123456-78-9",
                    "Test Book",
                    List.of("Test Author"),
                    "Fiction",
                    LocalDate.of(2024, 1, 15),
                    5
            );

            when(bookRepository.existsByIsbn(anyString())).thenReturn(false);
            when(authorRepository.findByName("Test Author")).thenReturn(Optional.empty());
            when(authorRepository.save(any(Author.class))).thenReturn(testAuthor);
            when(bookRepository.save(any(Book.class))).thenReturn(testBook);
            when(waitlistRepository.countWaitingByBookId(anyLong())).thenReturn(0);
            when(bookMapper.toResponse(any(Book.class), anyInt())).thenReturn(createBookResponse());

            // When
            BookResponse response = bookService.createBook(request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.isbn()).isEqualTo("978-0-123456-78-9");
            verify(bookRepository).save(any(Book.class));
            verify(auditService).logAction("BOOK", 1L, "CREATED");
        }

        @Test
        @DisplayName("Should reject duplicate ISBN")
        void createBook_DuplicateIsbn_ThrowsException() {
            // Given
            CreateBookRequest request = new CreateBookRequest(
                    "978-0-123456-78-9",
                    "Test Book",
                    List.of("Test Author"),
                    "Fiction",
                    LocalDate.of(2024, 1, 15),
                    5
            );

            when(bookRepository.existsByIsbn("978-0-123456-78-9")).thenReturn(true);

            // When & Then
            assertThatThrownBy(() -> bookService.createBook(request))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("ISBN");

            verify(bookRepository, never()).save(any(Book.class));
        }

        @Test
        @DisplayName("Should reuse existing author")
        void createBook_ExistingAuthor_ReusesAuthor() {
            // Given
            CreateBookRequest request = new CreateBookRequest(
                    "978-0-123456-78-9",
                    "Test Book",
                    List.of("Test Author"),
                    "Fiction",
                    null,
                    3
            );

            when(bookRepository.existsByIsbn(anyString())).thenReturn(false);
            when(authorRepository.findByName("Test Author")).thenReturn(Optional.of(testAuthor));
            when(bookRepository.save(any(Book.class))).thenReturn(testBook);
            when(waitlistRepository.countWaitingByBookId(anyLong())).thenReturn(0);
            when(bookMapper.toResponse(any(Book.class), anyInt())).thenReturn(createBookResponse());

            // When
            bookService.createBook(request);

            // Then
            verify(authorRepository, never()).save(any(Author.class));
        }
    }

    @Nested
    @DisplayName("Delete Book Tests")
    class DeleteBookTests {

        @Test
        @DisplayName("Should successfully soft-delete a book with no active loans")
        void deleteBook_NoActiveLoans_Success() {
            // Given
            when(bookRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testBook));
            when(bookRepository.countActiveLoans(1L)).thenReturn(0);
            when(bookRepository.save(any(Book.class))).thenReturn(testBook);

            // When
            bookService.deleteBook(1L);

            // Then
            assertThat(testBook.getDeleted()).isTrue();
            verify(bookRepository).save(testBook);
            verify(auditService).logAction("BOOK", 1L, "DELETED");
        }

        @Test
        @DisplayName("Should reject deletion when active loans exist")
        void deleteBook_WithActiveLoans_ThrowsException() {
            // Given
            when(bookRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testBook));
            when(bookRepository.countActiveLoans(1L)).thenReturn(2);

            // When & Then
            assertThatThrownBy(() -> bookService.deleteBook(1L))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("active loan");

            verify(bookRepository, never()).save(any(Book.class));
        }

        @Test
        @DisplayName("Should throw exception when book not found")
        void deleteBook_BookNotFound_ThrowsException() {
            // Given
            when(bookRepository.findByIdAndDeletedFalse(999L)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> bookService.deleteBook(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Book");
        }
    }

    @Nested
    @DisplayName("Get Book Tests")
    class GetBookTests {

        @Test
        @DisplayName("Should return book by ID")
        void getBookById_Success() {
            // Given
            when(bookRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(testBook));
            when(waitlistRepository.countWaitingByBookId(1L)).thenReturn(0);
            when(bookMapper.toResponse(testBook, 0)).thenReturn(createBookResponse());

            // When
            BookResponse response = bookService.getBookById(1L);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(1L);
        }

        @Test
        @DisplayName("Should return book by ISBN")
        void getBookByIsbn_Success() {
            // Given
            when(bookRepository.findByIsbnAndDeletedFalse("978-0-123456-78-9"))
                    .thenReturn(Optional.of(testBook));
            when(waitlistRepository.countWaitingByBookId(1L)).thenReturn(0);
            when(bookMapper.toResponse(testBook, 0)).thenReturn(createBookResponse());

            // When
            BookResponse response = bookService.getBookByIsbn("978-0-123456-78-9");

            // Then
            assertThat(response).isNotNull();
            assertThat(response.isbn()).isEqualTo("978-0-123456-78-9");
        }
    }

    private BookResponse createBookResponse() {
        return new BookResponse(
                1L,
                "978-0-123456-78-9",
                "Test Book",
                List.of("Test Author"),
                "Fiction",
                LocalDate.of(2024, 1, 15),
                5,
                5,
                true,
                0
        );
    }
}
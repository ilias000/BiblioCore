package com.iliaspiotopoulos.bibliocore.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iliaspiotopoulos.bibliocore.dto.request.BorrowBookRequest;
import com.iliaspiotopoulos.bibliocore.dto.request.CreateBookRequest;
import com.iliaspiotopoulos.bibliocore.dto.request.LoginRequest;
import com.iliaspiotopoulos.bibliocore.dto.request.RegisterMemberRequest;
import com.iliaspiotopoulos.bibliocore.dto.response.AuthResponse;
import com.iliaspiotopoulos.bibliocore.dto.response.BookResponse;
import com.iliaspiotopoulos.bibliocore.dto.response.LoanResponse;
import com.iliaspiotopoulos.bibliocore.dto.response.ReturnLoanResponse;
import com.iliaspiotopoulos.bibliocore.model.entity.User;
import com.iliaspiotopoulos.bibliocore.model.enums.LoanStatus;
import com.iliaspiotopoulos.bibliocore.model.enums.Role;
import com.iliaspiotopoulos.bibliocore.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest()
@AutoConfigureMockMvc
@AutoConfigureJson
@ActiveProfiles("test")
@Transactional
@DisplayName("Loan Lifecycle Integration Tests")
class LoanLifecycleIT {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String memberToken;
    private String adminToken;
    private Long bookId;

    @BeforeEach
    void setUp() throws Exception {
        createAdminUser();
        registerMember();
        createTestBook();
    }

    private void createAdminUser() throws Exception {
        if (!userRepository.existsByEmail("admin@library.com")) {
            User admin = User.builder()
                    .email("admin@library.com")
                    .passwordHash(passwordEncoder.encode("adminpass123"))
                    .role(Role.ROLE_ADMIN)
                    .build();
            userRepository.save(admin);
        }

        LoginRequest loginRequest = new LoginRequest("admin@library.com", "adminpass123");
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), AuthResponse.class);
        adminToken = response.accessToken();
    }

    private void registerMember() throws Exception {
        String email = "member" + System.currentTimeMillis() + "@test.com";
        RegisterMemberRequest request = new RegisterMemberRequest(
                "Test Member", email, "password123"
        );

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        AuthResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), AuthResponse.class);
        memberToken = response.accessToken();
    }

    private void createTestBook() throws Exception {
        String isbn = "978-" + (System.nanoTime() % 1000000000);
        CreateBookRequest request = new CreateBookRequest(
                isbn,
                "Integration Test Book",
                List.of("Test Author"),
                "Technology",
                LocalDate.of(2024, 6, 15),
                3
        );

        MvcResult result = mockMvc.perform(post("/api/v1/books")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        BookResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), BookResponse.class);
        bookId = response.id();
    }

    @Nested
    @DisplayName("Complete Loan Lifecycle")
    class CompleteLoanLifecycle {

        @Test
        @DisplayName("Member can borrow and return a book successfully")
        void borrowAndReturnBook_Success() throws Exception {
            // Borrow the book
            BorrowBookRequest borrowRequest = new BorrowBookRequest(bookId);

            MvcResult borrowResult = mockMvc.perform(post("/api/v1/loans/borrow")
                            .header("Authorization", "Bearer " + memberToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(borrowRequest)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.bookId").value(bookId))
                    .andReturn();

            LoanResponse loanResponse = objectMapper.readValue(
                    borrowResult.getResponse().getContentAsString(), LoanResponse.class);
            Long loanId = loanResponse.id();

            assertThat(loanResponse.status()).isEqualTo(LoanStatus.ACTIVE);
            assertThat(loanResponse.dueDate()).isAfter(LocalDate.now());

            // Verify book availability decreased
            mockMvc.perform(get("/api/v1/books/" + bookId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.availableCopies").value(2));

            // Return the book
            MvcResult returnResult = mockMvc.perform(post("/api/v1/loans/" + loanId + "/return")
                            .header("Authorization", "Bearer " + memberToken))
                    .andExpect(status().isOk())
                    .andReturn();

            ReturnLoanResponse returnResponse = objectMapper.readValue(
                    returnResult.getResponse().getContentAsString(), ReturnLoanResponse.class);

            assertThat(returnResponse.fineAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(returnResponse.daysOverdue()).isEqualTo(0);
            assertThat(returnResponse.returnDate()).isEqualTo(LocalDate.now());

            // Verify book availability increased
            mockMvc.perform(get("/api/v1/books/" + bookId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.availableCopies").value(3));
        }

        @Test
        @DisplayName("Member can view their loan history")
        void viewLoanHistory_Success() throws Exception {
            // Borrow a book first
            BorrowBookRequest borrowRequest = new BorrowBookRequest(bookId);
            mockMvc.perform(post("/api/v1/loans/borrow")
                            .header("Authorization", "Bearer " + memberToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(borrowRequest)))
                    .andExpect(status().isCreated());

            // View loan history
            mockMvc.perform(get("/api/v1/loans/my-loans")
                            .header("Authorization", "Bearer " + memberToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content[0].bookId").value(bookId));
        }
    }

    @Nested
    @DisplayName("Loan Validation Rules")
    class LoanValidationRules {

        @Test
        @DisplayName("Should reject duplicate borrow of same book")
        void borrowBook_Duplicate_Rejected() throws Exception {
            // First borrow succeeds
            BorrowBookRequest request = new BorrowBookRequest(bookId);
            mockMvc.perform(post("/api/v1/loans/borrow")
                            .header("Authorization", "Bearer " + memberToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            // Second borrow of same book fails
            mockMvc.perform(post("/api/v1/loans/borrow")
                            .header("Authorization", "Bearer " + memberToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.detail").value("Member already has this book on loan"));
        }

        @Test
        @DisplayName("Should enforce loan limit")
        void borrowBook_ExceedLimit_Rejected() throws Exception {
            // Create 3 more books and borrow them to reach the limit
            for (int i = 0; i < 3; i++) {
                String isbn = "978-L" + (System.nanoTime() % 100000000) + i;
                CreateBookRequest bookRequest = new CreateBookRequest(
                        isbn, "Book " + i, List.of("Author"), "Genre", null, 1
                );
                MvcResult bookResult = mockMvc.perform(post("/api/v1/books")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(bookRequest)))
                        .andExpect(status().isCreated())
                        .andReturn();

                BookResponse book = objectMapper.readValue(
                        bookResult.getResponse().getContentAsString(), BookResponse.class);

                BorrowBookRequest borrowRequest = new BorrowBookRequest(book.id());
                mockMvc.perform(post("/api/v1/loans/borrow")
                                .header("Authorization", "Bearer " + memberToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(borrowRequest)))
                        .andExpect(status().isCreated());
            }

            // 4th borrow should fail
            BorrowBookRequest request = new BorrowBookRequest(bookId);
            mockMvc.perform(post("/api/v1/loans/borrow")
                            .header("Authorization", "Bearer " + memberToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.detail").value("Member has reached the loan limit of 3"));
        }
    }

    @Nested
    @DisplayName("Book Management")
    class BookManagement {

        @Test
        @DisplayName("Admin can create and delete a book")
        void createAndDeleteBook_Success() throws Exception {
            String isbn = "978-D" + (System.nanoTime() % 1000000000);
            CreateBookRequest createRequest = new CreateBookRequest(
                    isbn, "Deletable Book", List.of("Author"), "Genre", null, 1
            );

            MvcResult createResult = mockMvc.perform(post("/api/v1/books")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createRequest)))
                    .andExpect(status().isCreated())
                    .andReturn();

            BookResponse book = objectMapper.readValue(
                    createResult.getResponse().getContentAsString(), BookResponse.class);

            // Delete the book
            mockMvc.perform(delete("/api/v1/books/" + book.id())
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isNoContent());

            // Verify book is soft-deleted (not accessible)
            mockMvc.perform(get("/api/v1/books/" + book.id()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Cannot delete book with active loans")
        void deleteBook_WithActiveLoans_Rejected() throws Exception {
            // Borrow the book
            BorrowBookRequest borrowRequest = new BorrowBookRequest(bookId);
            mockMvc.perform(post("/api/v1/loans/borrow")
                            .header("Authorization", "Bearer " + memberToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(borrowRequest)))
                    .andExpect(status().isCreated());

            // Try to delete
            mockMvc.perform(delete("/api/v1/books/" + bookId)
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.detail").value("Cannot delete book with 1 active loan(s)"));
        }

        @Test
        @DisplayName("Public can search books without authentication")
        void searchBooks_Public_Success() throws Exception {
            mockMvc.perform(get("/api/v1/books")
                            .param("title", "Integration"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray());
        }
    }

    @Nested
    @DisplayName("Authorization Tests")
    class AuthorizationTests {

        @Test
        @DisplayName("Unauthenticated user cannot borrow books")
        void borrowBook_Unauthenticated_Rejected() throws Exception {
            BorrowBookRequest request = new BorrowBookRequest(bookId);
            mockMvc.perform(post("/api/v1/loans/borrow")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Member cannot access admin endpoints")
        void adminEndpoint_AsMember_Rejected() throws Exception {
            mockMvc.perform(get("/api/v1/admin/members")
                            .header("Authorization", "Bearer " + memberToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Member cannot create books")
        void createBook_AsMember_Rejected() throws Exception {
            CreateBookRequest request = new CreateBookRequest(
                    "978-U" + (System.nanoTime() % 1000000000),
                    "Test", List.of("Author"), null, null, 1
            );
            mockMvc.perform(post("/api/v1/books")
                            .header("Authorization", "Bearer " + memberToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Admin can view all loans")
        void viewAllLoans_AsAdmin_Success() throws Exception {
            mockMvc.perform(get("/api/v1/admin/loans")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray());
        }
    }
}
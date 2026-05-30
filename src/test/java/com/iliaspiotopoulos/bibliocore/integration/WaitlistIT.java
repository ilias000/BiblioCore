package com.iliaspiotopoulos.bibliocore.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iliaspiotopoulos.bibliocore.dto.request.BorrowBookRequest;
import com.iliaspiotopoulos.bibliocore.dto.request.CreateBookRequest;
import com.iliaspiotopoulos.bibliocore.dto.request.LoginRequest;
import com.iliaspiotopoulos.bibliocore.dto.request.RegisterMemberRequest;
import com.iliaspiotopoulos.bibliocore.dto.response.AuthResponse;
import com.iliaspiotopoulos.bibliocore.dto.response.BookResponse;
import com.iliaspiotopoulos.bibliocore.dto.response.WaitlistResponse;
import com.iliaspiotopoulos.bibliocore.model.entity.User;
import com.iliaspiotopoulos.bibliocore.model.enums.Role;
import com.iliaspiotopoulos.bibliocore.model.enums.WaitlistStatus;
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

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureJson
@ActiveProfiles("test")
@Transactional
@DisplayName("Waitlist Integration Tests")
class WaitlistIT {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String member1Token;
    private String member2Token;
    private String adminToken;
    private Long bookId;

    @BeforeEach
    void setUp() throws Exception {
        createAdminUser();
        member1Token = registerMember("member1");
        member2Token = registerMember("member2");
        createBookWithSingleCopy();
    }

    private void createAdminUser() throws Exception {
        if (!userRepository.existsByEmail("admin@waitlist-test.com")) {
            User admin = User.builder()
                    .email("admin@waitlist-test.com")
                    .passwordHash(passwordEncoder.encode("adminpass123"))
                    .role(Role.ROLE_ADMIN)
                    .build();
            userRepository.save(admin);
        }

        LoginRequest loginRequest = new LoginRequest("admin@waitlist-test.com", "adminpass123");
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), AuthResponse.class);
        adminToken = response.accessToken();
    }

    private String registerMember(String prefix) throws Exception {
        String email = prefix + System.nanoTime() + "@test.com";
        RegisterMemberRequest request = new RegisterMemberRequest(
                "Test " + prefix, email, "password123"
        );

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        AuthResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), AuthResponse.class);
        return response.accessToken();
    }

    private void createBookWithSingleCopy() throws Exception {
        String isbn = "978-W" + (System.nanoTime() % 1000000000);
        CreateBookRequest request = new CreateBookRequest(
                isbn,
                "Waitlist Test Book",
                List.of("Test Author"),
                "Technology",
                LocalDate.of(2024, 1, 1),
                1 // Single copy to force waitlist scenario
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

    private void borrowBookAsMember1() throws Exception {
        BorrowBookRequest borrowRequest = new BorrowBookRequest(bookId);
        mockMvc.perform(post("/api/v1/loans/borrow")
                        .header("Authorization", "Bearer " + member1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(borrowRequest)))
                .andExpect(status().isCreated());
    }

    @Nested
    @DisplayName("Waitlist Rejoin Behavior")
    class WaitlistRejoinBehavior {

        @Test
        @DisplayName("Member can rejoin waitlist after cancelling and goes to end of queue")
        void rejoinAfterCancel_GoesToEndOfQueue() throws Exception {
            // Member1 borrows the only copy
            borrowBookAsMember1();

            // Member2 joins waitlist - should be position 1
            MvcResult firstJoinResult = mockMvc.perform(post("/api/v1/waitlist/books/" + bookId)
                            .header("Authorization", "Bearer " + member2Token))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("WAITING"))
                    .andExpect(jsonPath("$.position").value(1))
                    .andReturn();

            WaitlistResponse firstJoin = objectMapper.readValue(
                    firstJoinResult.getResponse().getContentAsString(), WaitlistResponse.class);
            assertThat(firstJoin.status()).isEqualTo(WaitlistStatus.WAITING);
            assertThat(firstJoin.position()).isEqualTo(1);

            // Member2 cancels their waitlist entry
            mockMvc.perform(delete("/api/v1/waitlist/books/" + bookId)
                            .header("Authorization", "Bearer " + member2Token))
                    .andExpect(status().isNoContent());

            // Register a third member who joins the waitlist
            String member3Token = registerMember("member3");
            mockMvc.perform(post("/api/v1/waitlist/books/" + bookId)
                            .header("Authorization", "Bearer " + member3Token))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.position").value(1));

            // Member2 rejoins - should be position 2 (end of queue)
            MvcResult rejoinResult = mockMvc.perform(post("/api/v1/waitlist/books/" + bookId)
                            .header("Authorization", "Bearer " + member2Token))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("WAITING"))
                    .andReturn();

            WaitlistResponse rejoin = objectMapper.readValue(
                    rejoinResult.getResponse().getContentAsString(), WaitlistResponse.class);
            assertThat(rejoin.status()).isEqualTo(WaitlistStatus.WAITING);
            assertThat(rejoin.position()).isEqualTo(2); // End of queue
            assertThat(rejoin.id()).isEqualTo(firstJoin.id()); // Same entry reactivated
        }

        @Test
        @DisplayName("Member cannot rejoin if already waiting")
        void cannotRejoinIfAlreadyWaiting() throws Exception {
            // Member1 borrows the only copy
            borrowBookAsMember1();

            // Member2 joins waitlist
            mockMvc.perform(post("/api/v1/waitlist/books/" + bookId)
                            .header("Authorization", "Bearer " + member2Token))
                    .andExpect(status().isCreated());

            // Member2 tries to join again - should fail
            mockMvc.perform(post("/api/v1/waitlist/books/" + bookId)
                            .header("Authorization", "Bearer " + member2Token))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.detail").value("Member is already on the waitlist for this book"));
        }

        @Test
        @DisplayName("Cannot join waitlist when book is available")
        void cannotJoinWaitlistWhenBookAvailable() throws Exception {
            // Book has 1 copy available - should not be able to join waitlist
            mockMvc.perform(post("/api/v1/waitlist/books/" + bookId)
                            .header("Authorization", "Bearer " + member2Token))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.detail").value("Book is currently available. No need to join the waitlist."));
        }

        @Test
        @DisplayName("Cannot join waitlist if already borrowed the book")
        void cannotJoinWaitlistIfAlreadyBorrowed() throws Exception {
            // Member1 borrows the only copy
            borrowBookAsMember1();

            // Member1 tries to join waitlist for same book - should fail
            mockMvc.perform(post("/api/v1/waitlist/books/" + bookId)
                            .header("Authorization", "Bearer " + member1Token))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.detail").value("Member already has this book on loan"));
        }

        @Test
        @DisplayName("Cancel and rejoin preserves entry ID but updates queue position")
        void cancelAndRejoinPreservesEntryId() throws Exception {
            // Member1 borrows the only copy
            borrowBookAsMember1();

            // Member2 joins waitlist
            MvcResult firstJoinResult = mockMvc.perform(post("/api/v1/waitlist/books/" + bookId)
                            .header("Authorization", "Bearer " + member2Token))
                    .andExpect(status().isCreated())
                    .andReturn();

            WaitlistResponse firstJoin = objectMapper.readValue(
                    firstJoinResult.getResponse().getContentAsString(), WaitlistResponse.class);
            Long originalEntryId = firstJoin.id();

            // Member2 cancels
            mockMvc.perform(delete("/api/v1/waitlist/books/" + bookId)
                            .header("Authorization", "Bearer " + member2Token))
                    .andExpect(status().isNoContent());

            // Member2 rejoins
            MvcResult rejoinResult = mockMvc.perform(post("/api/v1/waitlist/books/" + bookId)
                            .header("Authorization", "Bearer " + member2Token))
                    .andExpect(status().isCreated())
                    .andReturn();

            WaitlistResponse rejoin = objectMapper.readValue(
                    rejoinResult.getResponse().getContentAsString(), WaitlistResponse.class);

            // Same entry ID (reactivated), not a new entry
            assertThat(rejoin.id()).isEqualTo(originalEntryId);
            assertThat(rejoin.status()).isEqualTo(WaitlistStatus.WAITING);
        }
    }
}
package com.iliaspiotopoulos.bibliocore.service;

import com.iliaspiotopoulos.bibliocore.config.LoanProperties;
import com.iliaspiotopoulos.bibliocore.dto.response.LoanResponse;
import com.iliaspiotopoulos.bibliocore.dto.response.ReturnLoanResponse;
import com.iliaspiotopoulos.bibliocore.exception.BusinessRuleException;
import com.iliaspiotopoulos.bibliocore.exception.ResourceNotFoundException;
import com.iliaspiotopoulos.bibliocore.mapper.LoanMapper;
import com.iliaspiotopoulos.bibliocore.model.entity.Book;
import com.iliaspiotopoulos.bibliocore.model.entity.Loan;
import com.iliaspiotopoulos.bibliocore.model.entity.Member;
import com.iliaspiotopoulos.bibliocore.model.entity.User;
import com.iliaspiotopoulos.bibliocore.model.enums.LoanStatus;
import com.iliaspiotopoulos.bibliocore.model.enums.MembershipStatus;
import com.iliaspiotopoulos.bibliocore.model.enums.Role;
import com.iliaspiotopoulos.bibliocore.repository.BookRepository;
import com.iliaspiotopoulos.bibliocore.repository.LoanRepository;
import com.iliaspiotopoulos.bibliocore.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoanService Unit Tests")
class LoanServiceTest {

    @Mock
    private LoanRepository loanRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private LoanMapper loanMapper;

    @Mock
    private LoanProperties loanProperties;

    @Mock
    private AuditService auditService;

    @Mock
    private WaitlistService waitlistService;

    @InjectMocks
    private LoanService loanService;

    private User testUser;
    private Member testMember;
    private Book testBook;
    private Loan testLoan;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .email("member@test.com")
                .passwordHash("hash")
                .role(Role.ROLE_MEMBER)
                .build();

        testMember = Member.builder()
                .id(1L)
                .user(testUser)
                .name("Test Member")
                .membershipStatus(MembershipStatus.ACTIVE)
                .loanLimit(3)
                .build();

        testBook = Book.builder()
                .id(1L)
                .isbn("978-0-123456-78-9")
                .title("Test Book")
                .totalCopies(5)
                .availableCopies(3)
                .deleted(false)
                .build();

        testLoan = Loan.builder()
                .id(1L)
                .member(testMember)
                .book(testBook)
                .status(LoanStatus.ACTIVE)
                .borrowDate(LocalDate.now())
                .dueDate(LocalDate.now().plusDays(14))
                .fineAmount(BigDecimal.ZERO)
                .build();
    }

    @Nested
    @DisplayName("Borrow Book Tests")
    class BorrowBookTests {

        @Test
        @DisplayName("Should successfully borrow a book when all conditions are met")
        void borrowBook_Success() {
            // Given
            when(memberRepository.findById(1L)).thenReturn(Optional.of(testMember));
            when(loanRepository.countActiveLoansByMemberId(1L)).thenReturn(0);
            when(loanRepository.existsByMemberIdAndBookIdAndStatusIn(anyLong(), anyLong(), any()))
                    .thenReturn(false);
            when(bookRepository.findByIdWithLock(1L)).thenReturn(Optional.of(testBook));
            when(loanProperties.periodDays()).thenReturn(14);
            when(loanRepository.save(any(Loan.class))).thenReturn(testLoan);
            when(loanMapper.toResponse(any(Loan.class))).thenReturn(createLoanResponse());

            // When
            LoanResponse response = loanService.borrowBook(1L, 1L);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.bookId()).isEqualTo(1L);
            assertThat(response.memberId()).isEqualTo(1L);
            verify(bookRepository).save(any(Book.class));
            verify(loanRepository).save(any(Loan.class));
        }

        @Test
        @DisplayName("Should reject borrow when member is suspended")
        void borrowBook_MemberSuspended_ThrowsException() {
            // Given
            testMember.setMembershipStatus(MembershipStatus.SUSPENDED);
            when(memberRepository.findById(1L)).thenReturn(Optional.of(testMember));

            // When & Then
            assertThatThrownBy(() -> loanService.borrowBook(1L, 1L))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Suspended members cannot borrow books");

            verify(bookRepository, never()).findByIdWithLock(anyLong());
        }

        @Test
        @DisplayName("Should reject borrow when loan limit reached")
        void borrowBook_LoanLimitReached_ThrowsException() {
            // Given
            when(memberRepository.findById(1L)).thenReturn(Optional.of(testMember));
            when(loanRepository.countActiveLoansByMemberId(1L)).thenReturn(3);

            // When & Then
            assertThatThrownBy(() -> loanService.borrowBook(1L, 1L))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("loan limit");

            verify(bookRepository, never()).findByIdWithLock(anyLong());
        }

        @Test
        @DisplayName("Should reject borrow when no copies available")
        void borrowBook_NoCopiesAvailable_ThrowsException() {
            // Given
            testBook.setAvailableCopies(0);
            when(memberRepository.findById(1L)).thenReturn(Optional.of(testMember));
            when(loanRepository.countActiveLoansByMemberId(1L)).thenReturn(0);
            when(loanRepository.existsByMemberIdAndBookIdAndStatusIn(anyLong(), anyLong(), any()))
                    .thenReturn(false);
            when(bookRepository.findByIdWithLock(1L)).thenReturn(Optional.of(testBook));

            // When & Then
            assertThatThrownBy(() -> loanService.borrowBook(1L, 1L))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("No copies available");
        }

        @Test
        @DisplayName("Should reject borrow when member already has the book")
        void borrowBook_AlreadyBorrowed_ThrowsException() {
            // Given
            when(memberRepository.findById(1L)).thenReturn(Optional.of(testMember));
            when(loanRepository.countActiveLoansByMemberId(1L)).thenReturn(1);
            when(loanRepository.existsByMemberIdAndBookIdAndStatusIn(1L, 1L,
                    List.of(LoanStatus.ACTIVE, LoanStatus.OVERDUE))).thenReturn(true);

            // When & Then
            assertThatThrownBy(() -> loanService.borrowBook(1L, 1L))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("already has this book");
        }

        @Test
        @DisplayName("Should throw exception when member not found")
        void borrowBook_MemberNotFound_ThrowsException() {
            // Given
            when(memberRepository.findById(999L)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> loanService.borrowBook(999L, 1L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Member");
        }
    }

    @Nested
    @DisplayName("Return Book Tests")
    class ReturnBookTests {

        @Test
        @DisplayName("Should successfully return a book on time with no fine")
        void returnBook_OnTime_NoFine() {
            // Given
            testLoan.setDueDate(LocalDate.now().plusDays(7));
            when(loanRepository.findById(1L)).thenReturn(Optional.of(testLoan));
            when(loanRepository.save(any(Loan.class))).thenReturn(testLoan);
            when(bookRepository.save(any(Book.class))).thenReturn(testBook);

            // When
            ReturnLoanResponse response = loanService.returnBook(1L);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.fineAmount()).isEqualTo(BigDecimal.ZERO);
            assertThat(response.daysOverdue()).isEqualTo(0);
            assertThat(response.message()).contains("No fine");
            verify(waitlistService).notifyNextInWaitlist(testBook.getId());
        }

        @Test
        @DisplayName("Should calculate fine for overdue book")
        void returnBook_Overdue_CalculatesFine() {
            // Given
            testLoan.setDueDate(LocalDate.now().minusDays(5));
            when(loanRepository.findById(1L)).thenReturn(Optional.of(testLoan));
            when(loanRepository.save(any(Loan.class))).thenReturn(testLoan);
            when(bookRepository.save(any(Book.class))).thenReturn(testBook);
            when(loanProperties.finePerDay()).thenReturn(new BigDecimal("0.20"));

            // When
            ReturnLoanResponse response = loanService.returnBook(1L);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.fineAmount()).isEqualTo(new BigDecimal("1.00"));
            assertThat(response.daysOverdue()).isEqualTo(5);
            assertThat(response.message()).contains("fine");
        }

        @Test
        @DisplayName("Should reject return when loan already returned")
        void returnBook_AlreadyReturned_ThrowsException() {
            // Given
            testLoan.setStatus(LoanStatus.RETURNED);
            when(loanRepository.findById(1L)).thenReturn(Optional.of(testLoan));

            // When & Then
            assertThatThrownBy(() -> loanService.returnBook(1L))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("already been returned");
        }

        @Test
        @DisplayName("Should throw exception when loan not found")
        void returnBook_LoanNotFound_ThrowsException() {
            // Given
            when(loanRepository.findById(999L)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> loanService.returnBook(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Loan");
        }
    }

    private LoanResponse createLoanResponse() {
        return new LoanResponse(
                1L, 1L, "Test Member", 1L, "Test Book", "978-0-123456-78-9",
                LoanStatus.ACTIVE, LocalDate.now(), LocalDate.now().plusDays(14),
                null, BigDecimal.ZERO, false
        );
    }
}
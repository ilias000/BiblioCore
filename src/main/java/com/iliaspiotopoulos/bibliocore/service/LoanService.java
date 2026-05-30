package com.iliaspiotopoulos.bibliocore.service;

import com.iliaspiotopoulos.bibliocore.config.LoanProperties;
import com.iliaspiotopoulos.bibliocore.dto.response.LoanResponse;
import com.iliaspiotopoulos.bibliocore.dto.response.ReturnLoanResponse;
import com.iliaspiotopoulos.bibliocore.exception.BusinessRuleException;
import com.iliaspiotopoulos.bibliocore.exception.ConcurrencyException;
import com.iliaspiotopoulos.bibliocore.exception.ResourceNotFoundException;
import com.iliaspiotopoulos.bibliocore.mapper.LoanMapper;
import com.iliaspiotopoulos.bibliocore.model.entity.Book;
import com.iliaspiotopoulos.bibliocore.model.entity.Loan;
import com.iliaspiotopoulos.bibliocore.model.entity.Member;
import com.iliaspiotopoulos.bibliocore.model.enums.LoanStatus;
import com.iliaspiotopoulos.bibliocore.model.enums.MembershipStatus;
import com.iliaspiotopoulos.bibliocore.repository.BookRepository;
import com.iliaspiotopoulos.bibliocore.repository.LoanRepository;
import com.iliaspiotopoulos.bibliocore.repository.MemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class LoanService {

    private static final Logger log = LoggerFactory.getLogger(LoanService.class);

    private final LoanRepository loanRepository;
    private final MemberRepository memberRepository;
    private final BookRepository bookRepository;
    private final LoanMapper loanMapper;
    private final LoanProperties loanProperties;
    private final AuditService auditService;
    private final WaitlistService waitlistService;

    public LoanService(LoanRepository loanRepository,
                       MemberRepository memberRepository,
                       BookRepository bookRepository,
                       LoanMapper loanMapper,
                       LoanProperties loanProperties,
                       AuditService auditService,
                       WaitlistService waitlistService) {
        this.loanRepository = loanRepository;
        this.memberRepository = memberRepository;
        this.bookRepository = bookRepository;
        this.loanMapper = loanMapper;
        this.loanProperties = loanProperties;
        this.auditService = auditService;
        this.waitlistService = waitlistService;
    }

    @Transactional
    public LoanResponse borrowBook(Long memberId, Long bookId) {
        log.info("Member {} attempting to borrow book {}", memberId, bookId);

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member", "id", memberId));

        if (member.getMembershipStatus() == MembershipStatus.SUSPENDED) {
            throw new BusinessRuleException("MEMBER_SUSPENDED", "Suspended members cannot borrow books");
        }

        if (member.getMembershipStatus() == MembershipStatus.EXPIRED) {
            throw new BusinessRuleException("MEMBER_EXPIRED", "Expired membership. Please renew to borrow books");
        }

        int activeLoans = loanRepository.countActiveLoansByMemberId(memberId);
        if (activeLoans >= member.getLoanLimit()) {
            throw new BusinessRuleException("LOAN_LIMIT_REACHED",
                    "Member has reached the loan limit of " + member.getLoanLimit());
        }

        if (loanRepository.existsByMemberIdAndBookIdAndStatusIn(memberId, bookId,
                List.of(LoanStatus.ACTIVE, LoanStatus.OVERDUE))) {
            throw new BusinessRuleException("ALREADY_BORROWED", "Member already has this book on loan");
        }

        try {
            Book book = bookRepository.findByIdWithLock(bookId)
                    .orElseThrow(() -> new ResourceNotFoundException("Book", "id", bookId));

            if (book.getAvailableCopies() <= 0) {
                throw new BusinessRuleException("NO_COPIES_AVAILABLE",
                        "No copies available. Consider joining the waitlist.");
            }

            book.decrementAvailableCopies();
            bookRepository.save(book);

            LocalDate borrowDate = LocalDate.now();
            LocalDate dueDate = borrowDate.plusDays(loanProperties.periodDays());

            Loan loan = Loan.builder()
                    .member(member)
                    .book(book)
                    .status(LoanStatus.ACTIVE)
                    .borrowDate(borrowDate)
                    .dueDate(dueDate)
                    .fineAmount(BigDecimal.ZERO)
                    .build();

            loan = loanRepository.save(loan);
            auditService.logStateChange("LOAN", loan.getId(), "CREATED", "status", null, LoanStatus.ACTIVE.name());

            log.info("Book {} borrowed by member {}. Loan ID: {}", bookId, memberId, loan.getId());
            return loanMapper.toResponse(loan);

        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Optimistic lock failure when borrowing book {}. Concurrent request detected.", bookId);
            throw new ConcurrencyException(
                    "Another request modified this book. Please try again.", e);
        }
    }

    @Transactional
    public ReturnLoanResponse returnBook(Long loanId) {
        log.info("Processing return for loan {}", loanId);

        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan", "id", loanId));

        if (!loan.isActive()) {
            throw new BusinessRuleException("LOAN_NOT_ACTIVE", "This loan has already been returned");
        }

        LocalDate returnDate = LocalDate.now();
        loan.setReturnDate(returnDate);

        BigDecimal fineAmount = calculateFine(loan.getDueDate(), returnDate);
        loan.setFineAmount(fineAmount);

        LoanStatus oldStatus = loan.getStatus();
        loan.setStatus(LoanStatus.RETURNED);
        loanRepository.save(loan);

        Book book = loan.getBook();
        book.incrementAvailableCopies();
        bookRepository.save(book);

        auditService.logStateChange("LOAN", loanId, "RETURNED", "status",
                oldStatus.name(), LoanStatus.RETURNED.name());

        waitlistService.notifyNextInWaitlist(book.getId());

        int daysOverdue = calculateDaysOverdue(loan.getDueDate(), returnDate);
        String message = fineAmount.compareTo(BigDecimal.ZERO) > 0
                ? String.format("Book returned with fine of %.2f EUR for %d day(s) overdue", fineAmount, daysOverdue)
                : "Book returned successfully. No fine incurred.";

        log.info("Loan {} returned. Fine: {} EUR", loanId, fineAmount);

        return new ReturnLoanResponse(
                loanId,
                book.getTitle(),
                loan.getBorrowDate(),
                loan.getDueDate(),
                returnDate,
                Math.max(0, daysOverdue),
                fineAmount,
                message
        );
    }

    @Transactional(readOnly = true)
    public Page<LoanResponse> getMemberLoans(Long memberId, Pageable pageable) {
        if (!memberRepository.existsById(memberId)) {
            throw new ResourceNotFoundException("Member", "id", memberId);
        }
        return loanRepository.findByMemberId(memberId, pageable)
                .map(loanMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<LoanResponse> getMemberActiveLoans(Long memberId, Pageable pageable) {
        return loanRepository.findByMemberIdAndStatus(memberId, LoanStatus.ACTIVE, pageable)
                .map(loanMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<LoanResponse> getAllLoans(Pageable pageable) {
        return loanRepository.findAll(pageable)
                .map(loanMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<LoanResponse> getOverdueLoans(Pageable pageable) {
        return loanRepository.findAllByStatus(LoanStatus.OVERDUE, pageable)
                .map(loanMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public LoanResponse getLoanById(Long loanId) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan", "id", loanId));
        return loanMapper.toResponse(loan);
    }

    private BigDecimal calculateFine(LocalDate dueDate, LocalDate returnDate) {
        int daysOverdue = calculateDaysOverdue(dueDate, returnDate);
        if (daysOverdue <= 0) {
            return BigDecimal.ZERO;
        }
        return loanProperties.finePerDay()
                .multiply(BigDecimal.valueOf(daysOverdue))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private int calculateDaysOverdue(LocalDate dueDate, LocalDate returnDate) {
        long days = ChronoUnit.DAYS.between(dueDate, returnDate);
        return (int) Math.max(0, days);
    }
}
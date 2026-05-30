package com.iliaspiotopoulos.bibliocore.service;

import com.iliaspiotopoulos.bibliocore.dto.response.WaitlistResponse;
import com.iliaspiotopoulos.bibliocore.exception.BusinessRuleException;
import com.iliaspiotopoulos.bibliocore.exception.ResourceNotFoundException;
import com.iliaspiotopoulos.bibliocore.mapper.WaitlistMapper;
import com.iliaspiotopoulos.bibliocore.model.entity.Book;
import com.iliaspiotopoulos.bibliocore.model.entity.Member;
import com.iliaspiotopoulos.bibliocore.model.entity.WaitlistEntry;
import com.iliaspiotopoulos.bibliocore.model.enums.LoanStatus;
import com.iliaspiotopoulos.bibliocore.model.enums.MembershipStatus;
import com.iliaspiotopoulos.bibliocore.model.enums.WaitlistStatus;
import com.iliaspiotopoulos.bibliocore.repository.BookRepository;
import com.iliaspiotopoulos.bibliocore.repository.LoanRepository;
import com.iliaspiotopoulos.bibliocore.repository.MemberRepository;
import com.iliaspiotopoulos.bibliocore.repository.WaitlistRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class WaitlistService {

    private static final Logger log = LoggerFactory.getLogger(WaitlistService.class);

    private final WaitlistRepository waitlistRepository;
    private final MemberRepository memberRepository;
    private final BookRepository bookRepository;
    private final LoanRepository loanRepository;
    private final WaitlistMapper waitlistMapper;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    public WaitlistService(WaitlistRepository waitlistRepository,
                           MemberRepository memberRepository,
                           BookRepository bookRepository,
                           LoanRepository loanRepository,
                           WaitlistMapper waitlistMapper,
                           AuditService auditService,
                           ApplicationEventPublisher eventPublisher) {
        this.waitlistRepository = waitlistRepository;
        this.memberRepository = memberRepository;
        this.bookRepository = bookRepository;
        this.loanRepository = loanRepository;
        this.waitlistMapper = waitlistMapper;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public WaitlistResponse joinWaitlist(Long memberId, Long bookId) {
        log.info("Member {} joining waitlist for book {}", memberId, bookId);

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member", "id", memberId));

        if (member.getMembershipStatus() != MembershipStatus.ACTIVE) {
            throw new BusinessRuleException("MEMBER_NOT_ACTIVE", "Only active members can join the waitlist");
        }

        Book book = bookRepository.findByIdAndDeletedFalse(bookId)
                .orElseThrow(() -> new ResourceNotFoundException("Book", "id", bookId));

        if (book.getAvailableCopies() > 0) {
            throw new BusinessRuleException("BOOK_AVAILABLE",
                    "Book is currently available. No need to join the waitlist.");
        }

        if (loanRepository.existsByMemberIdAndBookIdAndStatusIn(memberId, bookId,
                List.of(LoanStatus.ACTIVE, LoanStatus.OVERDUE))) {
            throw new BusinessRuleException("ALREADY_BORROWED", "Member already has this book on loan");
        }

        // Check for existing entry (any status)
        var existingEntry = waitlistRepository.findByMemberIdAndBookId(memberId, bookId);

        WaitlistEntry entry;
        Instant now = Instant.now();

        if (existingEntry.isPresent()) {
            entry = existingEntry.get();
            if (entry.getStatus() == WaitlistStatus.WAITING) {
                throw new BusinessRuleException("ALREADY_ON_WAITLIST", "Member is already on the waitlist for this book");
            }
            // Reactivate cancelled/notified entry - goes to end of queue
            String previousStatus = entry.getStatus().name();
            entry.setStatus(WaitlistStatus.WAITING);
            entry.setQueuedAt(now);
            entry.setNotifiedAt(null);
            entry = waitlistRepository.save(entry);
            auditService.logStateChange("WAITLIST", entry.getId(), "REJOINED", "status",
                    previousStatus, WaitlistStatus.WAITING.name());
            log.info("Member {} rejoined waitlist for book {} (previous status: {})", memberId, bookId, previousStatus);
        } else {
            // Create new entry
            entry = WaitlistEntry.builder()
                    .member(member)
                    .book(book)
                    .status(WaitlistStatus.WAITING)
                    .queuedAt(now)
                    .build();
            entry = waitlistRepository.save(entry);
            auditService.logAction("WAITLIST", entry.getId(), "JOINED");
            log.info("Member {} added to waitlist for book {}", memberId, bookId);
        }

        int position = calculatePosition(bookId, entry.getId());
        log.info("Member {} waitlist position for book {}: {}", memberId, bookId, position);

        return waitlistMapper.toResponse(entry, position);
    }

    @Transactional
    public void cancelWaitlistEntry(Long memberId, Long bookId) {
        WaitlistEntry entry = waitlistRepository.findByMemberIdAndBookIdAndStatus(memberId, bookId, WaitlistStatus.WAITING)
                .orElseThrow(() -> new ResourceNotFoundException("WaitlistEntry", "memberId,bookId", memberId + "," + bookId));

        entry.setStatus(WaitlistStatus.CANCELLED);
        waitlistRepository.save(entry);
        auditService.logStateChange("WAITLIST", entry.getId(), "CANCELLED", "status",
                WaitlistStatus.WAITING.name(), WaitlistStatus.CANCELLED.name());

        log.info("Member {} cancelled waitlist for book {}", memberId, bookId);
    }

    @Transactional
    public void notifyNextInWaitlist(Long bookId) {
        waitlistRepository.findFirstByBookIdAndStatusOrderByQueuedAtAsc(bookId, WaitlistStatus.WAITING)
                .ifPresent(entry -> {
                    entry.setStatus(WaitlistStatus.NOTIFIED);
                    entry.setNotifiedAt(Instant.now());
                    waitlistRepository.save(entry);

                    auditService.logStateChange("WAITLIST", entry.getId(), "NOTIFIED", "status",
                            WaitlistStatus.WAITING.name(), WaitlistStatus.NOTIFIED.name());

                    log.info("NOTIFICATION: Member {} (ID: {}) - Book '{}' (ID: {}) is now available!",
                            entry.getMember().getName(),
                            entry.getMember().getId(),
                            entry.getBook().getTitle(),
                            bookId);

                    eventPublisher.publishEvent(new BookAvailableEvent(
                            entry.getMember().getId(),
                            entry.getMember().getUser().getEmail(),
                            entry.getBook().getId(),
                            entry.getBook().getTitle()
                    ));
                });
    }

    @Transactional(readOnly = true)
    public Page<WaitlistResponse> getMemberWaitlist(Long memberId, Pageable pageable) {
        return waitlistRepository.findByMemberId(memberId, pageable)
                .map(entry -> {
                    int position = entry.getStatus() == WaitlistStatus.WAITING
                            ? calculatePosition(entry.getBook().getId(), entry.getId())
                            : 0;
                    return waitlistMapper.toResponse(entry, position);
                });
    }

    @Transactional(readOnly = true)
    public Page<WaitlistResponse> getBookWaitlist(Long bookId, Pageable pageable) {
        return waitlistRepository.findByBookId(bookId, pageable)
                .map(entry -> {
                    int position = entry.getStatus() == WaitlistStatus.WAITING
                            ? calculatePosition(bookId, entry.getId())
                            : 0;
                    return waitlistMapper.toResponse(entry, position);
                });
    }

    private int calculatePosition(Long bookId, Long entryId) {
        return (int) waitlistRepository.calculatePosition(bookId, entryId);
    }

    public record BookAvailableEvent(Long memberId, String memberEmail, Long bookId, String bookTitle) {}
}
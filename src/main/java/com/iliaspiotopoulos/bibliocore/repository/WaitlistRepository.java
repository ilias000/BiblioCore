package com.iliaspiotopoulos.bibliocore.repository;

import com.iliaspiotopoulos.bibliocore.model.entity.WaitlistEntry;
import com.iliaspiotopoulos.bibliocore.model.enums.WaitlistStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WaitlistRepository extends JpaRepository<WaitlistEntry, Long> {

    @Query("SELECT w FROM WaitlistEntry w WHERE w.book.id = :bookId AND w.status = 'WAITING' ORDER BY w.queuedAt ASC")
    List<WaitlistEntry> findWaitingEntriesByBookIdOrderByQueuedAt(@Param("bookId") Long bookId);

    Optional<WaitlistEntry> findFirstByBookIdAndStatusOrderByQueuedAtAsc(Long bookId, WaitlistStatus status);

    Optional<WaitlistEntry> findByMemberIdAndBookIdAndStatus(Long memberId, Long bookId, WaitlistStatus status);

    Optional<WaitlistEntry> findByMemberIdAndBookId(Long memberId, Long bookId);

    boolean existsByMemberIdAndBookIdAndStatus(Long memberId, Long bookId, WaitlistStatus status);

    Page<WaitlistEntry> findByMemberId(Long memberId, Pageable pageable);

    Page<WaitlistEntry> findByBookId(Long bookId, Pageable pageable);

    @Query("SELECT COUNT(w) FROM WaitlistEntry w WHERE w.book.id = :bookId AND w.status = 'WAITING'")
    int countWaitingByBookId(@Param("bookId") Long bookId);
}
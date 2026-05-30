package com.iliaspiotopoulos.bibliocore.repository;

import com.iliaspiotopoulos.bibliocore.model.entity.Loan;
import com.iliaspiotopoulos.bibliocore.model.enums.LoanStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface LoanRepository extends JpaRepository<Loan, Long> {

    Page<Loan> findByMemberId(Long memberId, Pageable pageable);

    Page<Loan> findByMemberIdAndStatus(Long memberId, LoanStatus status, Pageable pageable);

    @Query("SELECT l FROM Loan l WHERE l.member.id = :memberId AND l.status IN ('ACTIVE', 'OVERDUE')")
    List<Loan> findActiveLoansByMemberId(@Param("memberId") Long memberId);

    @Query("SELECT COUNT(l) FROM Loan l WHERE l.member.id = :memberId AND l.status IN ('ACTIVE', 'OVERDUE')")
    int countActiveLoansByMemberId(@Param("memberId") Long memberId);

    @Query("SELECT l FROM Loan l WHERE l.status = 'ACTIVE' AND l.dueDate < :today")
    List<Loan> findOverdueLoans(@Param("today") LocalDate today);

    @Modifying
    @Query("UPDATE Loan l SET l.status = 'OVERDUE' WHERE l.status = 'ACTIVE' AND l.dueDate < :today")
    int markLoansAsOverdue(@Param("today") LocalDate today);

    Optional<Loan> findByMemberIdAndBookIdAndStatusIn(Long memberId, Long bookId, List<LoanStatus> statuses);

    boolean existsByMemberIdAndBookIdAndStatusIn(Long memberId, Long bookId, List<LoanStatus> statuses);

    Page<Loan> findAllByStatus(LoanStatus status, Pageable pageable);
}
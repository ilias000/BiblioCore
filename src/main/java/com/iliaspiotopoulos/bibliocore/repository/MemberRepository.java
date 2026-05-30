package com.iliaspiotopoulos.bibliocore.repository;

import com.iliaspiotopoulos.bibliocore.model.entity.Member;
import com.iliaspiotopoulos.bibliocore.model.enums.MembershipStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Member m WHERE m.id = :id")
    Optional<Member> findByIdWithLock(@Param("id") Long id);

    @Query("SELECT m FROM Member m JOIN FETCH m.user WHERE m.user.email = :email")
    Optional<Member> findByUserEmail(@Param("email") String email);

    Page<Member> findByMembershipStatus(MembershipStatus status, Pageable pageable);

    @Query("SELECT COUNT(l) FROM Loan l WHERE l.member.id = :memberId AND l.status IN ('ACTIVE', 'OVERDUE')")
    int countActiveLoans(@Param("memberId") Long memberId);

    boolean existsByUserEmail(String email);
}
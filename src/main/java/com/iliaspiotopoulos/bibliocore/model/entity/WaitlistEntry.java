package com.iliaspiotopoulos.bibliocore.model.entity;

import com.iliaspiotopoulos.bibliocore.model.enums.WaitlistStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "waitlist", indexes = {
        @Index(name = "idx_waitlist_book_id", columnList = "book_id"),
        @Index(name = "idx_waitlist_member_id", columnList = "member_id"),
        @Index(name = "idx_waitlist_status", columnList = "status"),
        @Index(name = "idx_waitlist_book_status", columnList = "book_id, status")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_waitlist_member_book", columnNames = {"member_id", "book_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WaitlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private WaitlistStatus status = WaitlistStatus.WAITING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Timestamp when member joined/rejoined the queue.
     * Used for position calculation. Updated on rejoin to put member at end of queue.
     */
    @Column(name = "queued_at", nullable = false)
    private Instant queuedAt;

    @Column(name = "notified_at")
    private Instant notifiedAt;
}
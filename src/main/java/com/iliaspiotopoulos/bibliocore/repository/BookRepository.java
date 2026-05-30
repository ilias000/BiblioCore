package com.iliaspiotopoulos.bibliocore.repository;

import com.iliaspiotopoulos.bibliocore.model.entity.Book;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BookRepository extends JpaRepository<Book, Long>, JpaSpecificationExecutor<Book> {

    Optional<Book> findByIsbnAndDeletedFalse(String isbn);

    Optional<Book> findByIdAndDeletedFalse(Long id);

    @Lock(LockModeType.OPTIMISTIC)
    @Query("SELECT b FROM Book b WHERE b.id = :id AND b.deleted = false")
    Optional<Book> findByIdWithLock(@Param("id") Long id);

    Page<Book> findAllByDeletedFalse(Pageable pageable);

    boolean existsByIsbn(String isbn);

    @Query("SELECT COUNT(l) FROM Loan l WHERE l.book.id = :bookId AND l.status IN ('ACTIVE', 'OVERDUE')")
    int countActiveLoans(@Param("bookId") Long bookId);
}
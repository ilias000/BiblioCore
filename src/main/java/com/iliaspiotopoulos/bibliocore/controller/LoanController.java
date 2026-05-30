package com.iliaspiotopoulos.bibliocore.controller;

import com.iliaspiotopoulos.bibliocore.dto.request.BorrowBookRequest;
import com.iliaspiotopoulos.bibliocore.dto.response.LoanResponse;
import com.iliaspiotopoulos.bibliocore.dto.response.ReturnLoanResponse;
import com.iliaspiotopoulos.bibliocore.exception.UnauthorizedException;
import com.iliaspiotopoulos.bibliocore.model.entity.Member;
import com.iliaspiotopoulos.bibliocore.security.CurrentUser;
import com.iliaspiotopoulos.bibliocore.service.LoanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/loans")
@Tag(name = "Loans", description = "Loan management for members")
@SecurityRequirement(name = "bearerAuth")
public class LoanController {

    private final LoanService loanService;
    private final CurrentUser currentUser;

    public LoanController(LoanService loanService, CurrentUser currentUser) {
        this.loanService = loanService;
        this.currentUser = currentUser;
    }

    @PostMapping("/borrow")
    @Operation(summary = "Borrow a book", description = "Borrow a book. Available to members only.")
    public ResponseEntity<LoanResponse> borrowBook(@Valid @RequestBody BorrowBookRequest request) {
        Member member = currentUser.getMember()
                .orElseThrow(() -> new UnauthorizedException("Not authenticated as a member"));

        LoanResponse response = loanService.borrowBook(member.getId(), request.bookId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{loanId}/return")
    @Operation(summary = "Return a book", description = "Return a borrowed book. Fine is calculated if overdue.")
    public ResponseEntity<ReturnLoanResponse> returnBook(@PathVariable Long loanId) {
        Member member = currentUser.getMember()
                .orElseThrow(() -> new UnauthorizedException("Not authenticated as a member"));

        LoanResponse loan = loanService.getLoanById(loanId);
        if (!currentUser.isAdmin() && !loan.memberId().equals(member.getId())) {
            throw new AccessDeniedException("You can only return your own loans");
        }

        ReturnLoanResponse response = loanService.returnBook(loanId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my-loans")
    @Operation(summary = "Get my loans", description = "Get current member's loan history")
    public ResponseEntity<Page<LoanResponse>> getMyLoans(
            @PageableDefault(size = 20, sort = "borrowDate", direction = Sort.Direction.DESC) Pageable pageable) {

        Member member = currentUser.getMember()
                .orElseThrow(() -> new UnauthorizedException("Not authenticated as a member"));

        Page<LoanResponse> loans = loanService.getMemberLoans(member.getId(), pageable);
        return ResponseEntity.ok(loans);
    }

    @GetMapping("/my-loans/active")
    @Operation(summary = "Get my active loans", description = "Get current member's active loans")
    public ResponseEntity<Page<LoanResponse>> getMyActiveLoans(
            @PageableDefault(size = 20, sort = "dueDate", direction = Sort.Direction.ASC) Pageable pageable) {

        Member member = currentUser.getMember()
                .orElseThrow(() -> new UnauthorizedException("Not authenticated as a member"));

        Page<LoanResponse> loans = loanService.getMemberActiveLoans(member.getId(), pageable);
        return ResponseEntity.ok(loans);
    }

    @GetMapping("/{loanId}")
    @Operation(summary = "Get loan details", description = "Get details of a specific loan. Members can only view their own.")
    public ResponseEntity<LoanResponse> getLoanById(@PathVariable Long loanId) {
        LoanResponse loan = loanService.getLoanById(loanId);

        if (!currentUser.isAdmin()) {
            Member member = currentUser.getMember()
                    .orElseThrow(() -> new UnauthorizedException("Not authenticated as a member"));
            if (!loan.memberId().equals(member.getId())) {
                throw new AccessDeniedException("You can only view your own loans");
            }
        }

        return ResponseEntity.ok(loan);
    }
}
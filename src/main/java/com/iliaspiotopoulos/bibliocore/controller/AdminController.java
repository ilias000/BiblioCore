package com.iliaspiotopoulos.bibliocore.controller;

import com.iliaspiotopoulos.bibliocore.dto.request.UpdateMemberRequest;
import com.iliaspiotopoulos.bibliocore.dto.response.AuditLogResponse;
import com.iliaspiotopoulos.bibliocore.dto.response.LoanResponse;
import com.iliaspiotopoulos.bibliocore.dto.response.MemberResponse;
import com.iliaspiotopoulos.bibliocore.dto.response.WaitlistResponse;
import com.iliaspiotopoulos.bibliocore.model.enums.MembershipStatus;
import com.iliaspiotopoulos.bibliocore.service.AuditService;
import com.iliaspiotopoulos.bibliocore.service.LoanService;
import com.iliaspiotopoulos.bibliocore.service.MemberService;
import com.iliaspiotopoulos.bibliocore.service.OverdueDetectionService;
import com.iliaspiotopoulos.bibliocore.service.WaitlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin", description = "Administrative operations (Librarians only)")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private final MemberService memberService;
    private final LoanService loanService;
    private final WaitlistService waitlistService;
    private final AuditService auditService;
    private final OverdueDetectionService overdueDetectionService;

    public AdminController(MemberService memberService,
                           LoanService loanService,
                           WaitlistService waitlistService,
                           AuditService auditService,
                           OverdueDetectionService overdueDetectionService) {
        this.memberService = memberService;
        this.loanService = loanService;
        this.waitlistService = waitlistService;
        this.auditService = auditService;
        this.overdueDetectionService = overdueDetectionService;
    }

    @GetMapping("/members")
    @Operation(summary = "List all members", description = "Get all members with optional status filter")
    public ResponseEntity<Page<MemberResponse>> getAllMembers(
            @Parameter(description = "Filter by membership status")
            @RequestParam(required = false) MembershipStatus status,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {

        Page<MemberResponse> members = status != null
                ? memberService.getMembersByStatus(status, pageable)
                : memberService.getAllMembers(pageable);
        return ResponseEntity.ok(members);
    }

    @GetMapping("/members/{id}")
    @Operation(summary = "Get member by ID", description = "Retrieve a member's details by ID")
    public ResponseEntity<MemberResponse> getMemberById(@PathVariable Long id) {
        MemberResponse member = memberService.getMemberById(id);
        return ResponseEntity.ok(member);
    }

    @PutMapping("/members/{id}")
    @Operation(summary = "Update member", description = "Update a member's details")
    public ResponseEntity<MemberResponse> updateMember(
            @PathVariable Long id,
            @Valid @RequestBody UpdateMemberRequest request) {
        MemberResponse member = memberService.updateMember(id, request);
        return ResponseEntity.ok(member);
    }

    @PatchMapping("/members/{id}/suspend")
    @Operation(summary = "Suspend member", description = "Suspend a member's account")
    public ResponseEntity<MemberResponse> suspendMember(@PathVariable Long id) {
        MemberResponse member = memberService.suspendMember(id);
        return ResponseEntity.ok(member);
    }

    @PatchMapping("/members/{id}/activate")
    @Operation(summary = "Activate member", description = "Activate a member's account")
    public ResponseEntity<MemberResponse> activateMember(@PathVariable Long id) {
        MemberResponse member = memberService.activateMember(id);
        return ResponseEntity.ok(member);
    }

    @GetMapping("/loans")
    @Operation(summary = "List all loans", description = "Get all loans with pagination")
    public ResponseEntity<Page<LoanResponse>> getAllLoans(
            @PageableDefault(size = 20, sort = "borrowDate", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<LoanResponse> loans = loanService.getAllLoans(pageable);
        return ResponseEntity.ok(loans);
    }

    @GetMapping("/loans/overdue")
    @Operation(summary = "List overdue loans", description = "Get all overdue loans")
    public ResponseEntity<Page<LoanResponse>> getOverdueLoans(
            @PageableDefault(size = 20, sort = "dueDate") Pageable pageable) {
        Page<LoanResponse> loans = loanService.getOverdueLoans(pageable);
        return ResponseEntity.ok(loans);
    }

    @GetMapping("/loans/member/{memberId}")
    @Operation(summary = "Get member loans", description = "Get all loans for a specific member")
    public ResponseEntity<Page<LoanResponse>> getMemberLoans(
            @PathVariable Long memberId,
            @PageableDefault(size = 20, sort = "borrowDate", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<LoanResponse> loans = loanService.getMemberLoans(memberId, pageable);
        return ResponseEntity.ok(loans);
    }

    @GetMapping("/waitlist/book/{bookId}")
    @Operation(summary = "Get book waitlist", description = "Get waitlist for a specific book")
    public ResponseEntity<Page<WaitlistResponse>> getBookWaitlist(
            @PathVariable Long bookId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        Page<WaitlistResponse> waitlist = waitlistService.getBookWaitlist(bookId, pageable);
        return ResponseEntity.ok(waitlist);
    }

    @GetMapping("/audit/{entityType}/{entityId}")
    @Operation(summary = "Get audit log", description = "Get audit log for a specific entity")
    public ResponseEntity<Page<AuditLogResponse>> getAuditLog(
            @PathVariable String entityType,
            @PathVariable Long entityId,
            @PageableDefault(size = 50, sort = "performedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<AuditLogResponse> auditLogs = auditService.getAuditLogsByEntity(entityType.toUpperCase(), entityId, pageable);
        return ResponseEntity.ok(auditLogs);
    }

    @PostMapping("/loans/detect-overdue")
    @Operation(summary = "Run overdue detection", description = "Manually trigger overdue loan detection")
    public ResponseEntity<Map<String, Object>> runOverdueDetection() {
        int updatedCount = overdueDetectionService.runManualOverdueDetection();
        return ResponseEntity.ok(Map.of(
                "message", "Overdue detection completed",
                "loansMarkedOverdue", updatedCount
        ));
    }
}
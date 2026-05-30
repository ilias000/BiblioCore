package com.iliaspiotopoulos.bibliocore.controller;

import com.iliaspiotopoulos.bibliocore.dto.response.WaitlistResponse;
import com.iliaspiotopoulos.bibliocore.exception.UnauthorizedException;
import com.iliaspiotopoulos.bibliocore.model.entity.Member;
import com.iliaspiotopoulos.bibliocore.security.CurrentUser;
import com.iliaspiotopoulos.bibliocore.service.WaitlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/waitlist")
@Tag(name = "Waitlist", description = "Book reservation waitlist management")
@SecurityRequirement(name = "bearerAuth")
public class WaitlistController {

    private final WaitlistService waitlistService;
    private final CurrentUser currentUser;

    public WaitlistController(WaitlistService waitlistService, CurrentUser currentUser) {
        this.waitlistService = waitlistService;
        this.currentUser = currentUser;
    }

    @PostMapping("/books/{bookId}")
    @Operation(summary = "Join waitlist", description = "Join the waitlist for an unavailable book")
    public ResponseEntity<WaitlistResponse> joinWaitlist(@PathVariable Long bookId) {
        Member member = currentUser.getMember()
                .orElseThrow(() -> new UnauthorizedException("Not authenticated as a member"));

        WaitlistResponse response = waitlistService.joinWaitlist(member.getId(), bookId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/books/{bookId}")
    @Operation(summary = "Cancel waitlist entry", description = "Remove yourself from a book's waitlist")
    public ResponseEntity<Void> cancelWaitlist(@PathVariable Long bookId) {
        Member member = currentUser.getMember()
                .orElseThrow(() -> new UnauthorizedException("Not authenticated as a member"));

        waitlistService.cancelWaitlistEntry(member.getId(), bookId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/my-waitlist")
    @Operation(summary = "Get my waitlist entries", description = "Get current member's waitlist entries")
    public ResponseEntity<Page<WaitlistResponse>> getMyWaitlist(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Member member = currentUser.getMember()
                .orElseThrow(() -> new UnauthorizedException("Not authenticated as a member"));

        Page<WaitlistResponse> waitlist = waitlistService.getMemberWaitlist(member.getId(), pageable);
        return ResponseEntity.ok(waitlist);
    }
}
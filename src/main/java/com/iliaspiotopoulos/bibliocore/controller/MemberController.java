package com.iliaspiotopoulos.bibliocore.controller;

import com.iliaspiotopoulos.bibliocore.dto.response.MemberResponse;
import com.iliaspiotopoulos.bibliocore.exception.UnauthorizedException;
import com.iliaspiotopoulos.bibliocore.model.entity.Member;
import com.iliaspiotopoulos.bibliocore.security.CurrentUser;
import com.iliaspiotopoulos.bibliocore.service.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/members")
@Tag(name = "Members", description = "Member profile endpoints")
@SecurityRequirement(name = "bearerAuth")
public class MemberController {

    private final MemberService memberService;
    private final CurrentUser currentUser;

    public MemberController(MemberService memberService, CurrentUser currentUser) {
        this.memberService = memberService;
        this.currentUser = currentUser;
    }

    @GetMapping("/me")
    @Operation(summary = "Get current member profile", description = "Retrieve the authenticated member's profile")
    public ResponseEntity<MemberResponse> getCurrentMember() {
        Member member = currentUser.getMember()
                .orElseThrow(() -> new UnauthorizedException("Not authenticated as a member"));
        MemberResponse response = memberService.getMemberById(member.getId());
        return ResponseEntity.ok(response);
    }
}
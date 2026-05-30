package com.iliaspiotopoulos.bibliocore.service;

import com.iliaspiotopoulos.bibliocore.dto.request.UpdateMemberRequest;
import com.iliaspiotopoulos.bibliocore.dto.response.MemberResponse;
import com.iliaspiotopoulos.bibliocore.exception.ResourceNotFoundException;
import com.iliaspiotopoulos.bibliocore.mapper.MemberMapper;
import com.iliaspiotopoulos.bibliocore.model.entity.Member;
import com.iliaspiotopoulos.bibliocore.model.enums.MembershipStatus;
import com.iliaspiotopoulos.bibliocore.repository.LoanRepository;
import com.iliaspiotopoulos.bibliocore.repository.MemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberService {

    private static final Logger log = LoggerFactory.getLogger(MemberService.class);

    private final MemberRepository memberRepository;
    private final LoanRepository loanRepository;
    private final MemberMapper memberMapper;
    private final AuditService auditService;

    public MemberService(MemberRepository memberRepository,
                         LoanRepository loanRepository,
                         MemberMapper memberMapper,
                         AuditService auditService) {
        this.memberRepository = memberRepository;
        this.loanRepository = loanRepository;
        this.memberMapper = memberMapper;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<MemberResponse> getAllMembers(Pageable pageable) {
        return memberRepository.findAll(pageable)
                .map(this::toResponseWithActiveLoans);
    }

    @Transactional(readOnly = true)
    public Page<MemberResponse> getMembersByStatus(MembershipStatus status, Pageable pageable) {
        return memberRepository.findByMembershipStatus(status, pageable)
                .map(this::toResponseWithActiveLoans);
    }

    @Transactional(readOnly = true)
    public MemberResponse getMemberById(Long id) {
        Member member = findMemberById(id);
        return toResponseWithActiveLoans(member);
    }

    @Transactional(readOnly = true)
    public MemberResponse getMemberByEmail(String email) {
        Member member = memberRepository.findByUserEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Member", "email", email));
        return toResponseWithActiveLoans(member);
    }

    @Transactional
    public MemberResponse updateMember(Long id, UpdateMemberRequest request) {
        Member member = findMemberById(id);
        log.info("Updating member: {}", id);

        if (request.name() != null && !request.name().isBlank()) {
            String oldName = member.getName();
            member.setName(request.name());
            auditService.logStateChange("MEMBER", id, "UPDATE", "name", oldName, request.name());
        }

        if (request.membershipStatus() != null && request.membershipStatus() != member.getMembershipStatus()) {
            MembershipStatus oldStatus = member.getMembershipStatus();
            member.setMembershipStatus(request.membershipStatus());
            auditService.logStateChange("MEMBER", id, "UPDATE", "membershipStatus",
                    oldStatus.name(), request.membershipStatus().name());
        }

        if (request.loanLimit() != null && !request.loanLimit().equals(member.getLoanLimit())) {
            Integer oldLimit = member.getLoanLimit();
            member.setLoanLimit(request.loanLimit());
            auditService.logStateChange("MEMBER", id, "UPDATE", "loanLimit",
                    String.valueOf(oldLimit), String.valueOf(request.loanLimit()));
        }

        member = memberRepository.save(member);
        log.info("Member {} updated successfully", id);
        return toResponseWithActiveLoans(member);
    }

    @Transactional
    public MemberResponse suspendMember(Long id) {
        Member member = findMemberById(id);
        MembershipStatus oldStatus = member.getMembershipStatus();
        member.setMembershipStatus(MembershipStatus.SUSPENDED);
        member = memberRepository.save(member);
        auditService.logStateChange("MEMBER", id, "SUSPEND", "membershipStatus",
                oldStatus.name(), MembershipStatus.SUSPENDED.name());
        log.info("Member {} suspended", id);
        return toResponseWithActiveLoans(member);
    }

    @Transactional
    public MemberResponse activateMember(Long id) {
        Member member = findMemberById(id);
        MembershipStatus oldStatus = member.getMembershipStatus();
        member.setMembershipStatus(MembershipStatus.ACTIVE);
        member = memberRepository.save(member);
        auditService.logStateChange("MEMBER", id, "ACTIVATE", "membershipStatus",
                oldStatus.name(), MembershipStatus.ACTIVE.name());
        log.info("Member {} activated", id);
        return toResponseWithActiveLoans(member);
    }

    private Member findMemberById(Long id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Member", "id", id));
    }

    private MemberResponse toResponseWithActiveLoans(Member member) {
        int activeLoans = loanRepository.countActiveLoansByMemberId(member.getId());
        return memberMapper.toResponse(member, activeLoans);
    }
}
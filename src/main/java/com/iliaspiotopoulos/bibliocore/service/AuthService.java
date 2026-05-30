package com.iliaspiotopoulos.bibliocore.service;

import com.iliaspiotopoulos.bibliocore.config.JwtProperties;
import com.iliaspiotopoulos.bibliocore.config.LoanProperties;
import com.iliaspiotopoulos.bibliocore.dto.request.LoginRequest;
import com.iliaspiotopoulos.bibliocore.dto.request.RegisterMemberRequest;
import com.iliaspiotopoulos.bibliocore.dto.response.AuthResponse;
import com.iliaspiotopoulos.bibliocore.dto.response.UserResponse;
import com.iliaspiotopoulos.bibliocore.exception.DuplicateResourceException;
import com.iliaspiotopoulos.bibliocore.mapper.UserMapper;
import com.iliaspiotopoulos.bibliocore.model.entity.Member;
import com.iliaspiotopoulos.bibliocore.model.entity.User;
import com.iliaspiotopoulos.bibliocore.model.enums.MembershipStatus;
import com.iliaspiotopoulos.bibliocore.model.enums.Role;
import com.iliaspiotopoulos.bibliocore.repository.MemberRepository;
import com.iliaspiotopoulos.bibliocore.repository.UserRepository;
import com.iliaspiotopoulos.bibliocore.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserMapper userMapper;
    private final LoanProperties loanProperties;
    private final JwtProperties jwtProperties;
    private final AuditService auditService;

    public AuthService(UserRepository userRepository,
                       MemberRepository memberRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       UserMapper userMapper,
                       LoanProperties loanProperties,
                       JwtProperties jwtProperties,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userMapper = userMapper;
        this.loanProperties = loanProperties;
        this.jwtProperties = jwtProperties;
        this.auditService = auditService;
    }

    @Transactional
    public AuthResponse register(RegisterMemberRequest request) {
        log.info("Registering new member with email: {}", request.email());

        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("User", "email", request.email());
        }

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(Role.ROLE_MEMBER)
                .build();
        user = userRepository.save(user);

        Member member = Member.builder()
                .user(user)
                .name(request.name())
                .membershipStatus(MembershipStatus.ACTIVE)
                .loanLimit(loanProperties.defaultLimit())
                .build();
        memberRepository.save(member);

        auditService.logAction("MEMBER", member.getId(), "CREATED");

        String token = jwtTokenProvider.generateToken(user);
        UserResponse userResponse = userMapper.toResponse(user);

        log.info("Successfully registered member: {}", request.email());
        return new AuthResponse(token, (long) jwtProperties.accessTokenTtl() * 60, userResponse);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        log.info("Login attempt for email: {}", request.email());

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("Failed login attempt for email: {}", request.email());
            throw new BadCredentialsException("Invalid email or password");
        }

        String token = jwtTokenProvider.generateToken(user);
        UserResponse userResponse = userMapper.toResponse(user);

        log.info("Successful login for email: {}", request.email());
        return new AuthResponse(token, (long) jwtProperties.accessTokenTtl() * 60, userResponse);
    }
}
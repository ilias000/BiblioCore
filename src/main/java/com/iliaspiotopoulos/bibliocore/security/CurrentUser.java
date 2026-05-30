package com.iliaspiotopoulos.bibliocore.security;

import com.iliaspiotopoulos.bibliocore.model.entity.Member;
import com.iliaspiotopoulos.bibliocore.model.entity.User;
import com.iliaspiotopoulos.bibliocore.model.enums.Role;
import com.iliaspiotopoulos.bibliocore.repository.MemberRepository;
import com.iliaspiotopoulos.bibliocore.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class CurrentUser {

    private final UserRepository userRepository;
    private final MemberRepository memberRepository;

    public CurrentUser(UserRepository userRepository, MemberRepository memberRepository) {
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
    }

    public Optional<User> getUser() {
        String email = getEmail();
        if (email == null) {
            return Optional.empty();
        }
        return userRepository.findByEmail(email);
    }

    public Optional<Member> getMember() {
        String email = getEmail();
        if (email == null) {
            return Optional.empty();
        }
        return memberRepository.findByUserEmail(email);
    }

    public Long getUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            return null;
        }
        return jwt.getClaim("userId");
    }

    public String getEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return null;
        }
        return auth.getName();
    }

    public boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(Role.ROLE_ADMIN.name()));
    }

    public boolean isMember() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(Role.ROLE_MEMBER.name()));
    }
}
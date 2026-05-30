package com.iliaspiotopoulos.bibliocore.mapper;

import com.iliaspiotopoulos.bibliocore.dto.response.MemberResponse;
import com.iliaspiotopoulos.bibliocore.model.entity.Member;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MemberMapper {

    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "activeLoans", ignore = true)
    MemberResponse toResponse(Member member);

    default MemberResponse toResponse(Member member, int activeLoans) {
        MemberResponse base = toResponse(member);
        return new MemberResponse(
                base.id(),
                base.name(),
                base.email(),
                base.membershipStatus(),
                base.loanLimit(),
                activeLoans,
                base.createdAt()
        );
    }
}
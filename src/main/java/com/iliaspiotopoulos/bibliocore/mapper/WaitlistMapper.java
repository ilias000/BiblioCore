package com.iliaspiotopoulos.bibliocore.mapper;

import com.iliaspiotopoulos.bibliocore.dto.response.WaitlistResponse;
import com.iliaspiotopoulos.bibliocore.model.entity.WaitlistEntry;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface WaitlistMapper {

    @Mapping(target = "memberId", source = "member.id")
    @Mapping(target = "memberName", source = "member.name")
    @Mapping(target = "bookId", source = "book.id")
    @Mapping(target = "bookTitle", source = "book.title")
    @Mapping(target = "position", ignore = true)
    WaitlistResponse toResponse(WaitlistEntry entry);

    default WaitlistResponse toResponse(WaitlistEntry entry, int position) {
        WaitlistResponse base = toResponse(entry);
        return new WaitlistResponse(
                base.id(),
                base.memberId(),
                base.memberName(),
                base.bookId(),
                base.bookTitle(),
                base.status(),
                position,
                base.createdAt(),
                base.notifiedAt()
        );
    }
}
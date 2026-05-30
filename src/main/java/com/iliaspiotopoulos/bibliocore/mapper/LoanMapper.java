package com.iliaspiotopoulos.bibliocore.mapper;

import com.iliaspiotopoulos.bibliocore.dto.response.LoanResponse;
import com.iliaspiotopoulos.bibliocore.model.entity.Loan;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface LoanMapper {

    @Mapping(target = "memberId", source = "member.id")
    @Mapping(target = "memberName", source = "member.name")
    @Mapping(target = "bookId", source = "book.id")
    @Mapping(target = "bookTitle", source = "book.title")
    @Mapping(target = "bookIsbn", source = "book.isbn")
    @Mapping(target = "overdue", expression = "java(loan.isOverdue())")
    LoanResponse toResponse(Loan loan);
}
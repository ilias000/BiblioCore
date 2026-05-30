package com.iliaspiotopoulos.bibliocore.mapper;

import com.iliaspiotopoulos.bibliocore.dto.response.UserResponse;
import com.iliaspiotopoulos.bibliocore.model.entity.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse toResponse(User user);
}
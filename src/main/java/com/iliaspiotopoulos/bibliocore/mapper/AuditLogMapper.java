package com.iliaspiotopoulos.bibliocore.mapper;

import com.iliaspiotopoulos.bibliocore.dto.response.AuditLogResponse;
import com.iliaspiotopoulos.bibliocore.model.entity.AuditLog;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AuditLogMapper {

    @Mapping(target = "performedById", source = "performedBy.id")
    @Mapping(target = "performedByEmail", source = "performedBy.email")
    AuditLogResponse toResponse(AuditLog auditLog);
}
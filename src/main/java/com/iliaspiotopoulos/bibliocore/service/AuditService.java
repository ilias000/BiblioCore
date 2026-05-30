package com.iliaspiotopoulos.bibliocore.service;

import com.iliaspiotopoulos.bibliocore.dto.response.AuditLogResponse;
import com.iliaspiotopoulos.bibliocore.mapper.AuditLogMapper;
import com.iliaspiotopoulos.bibliocore.model.entity.AuditLog;
import com.iliaspiotopoulos.bibliocore.model.entity.User;
import com.iliaspiotopoulos.bibliocore.repository.AuditLogRepository;
import com.iliaspiotopoulos.bibliocore.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final AuditLogMapper auditLogMapper;

    public AuditService(AuditLogRepository auditLogRepository,
                        UserRepository userRepository,
                        AuditLogMapper auditLogMapper) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.auditLogMapper = auditLogMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logStateChange(String entityType, Long entityId, String action,
                               String fieldName, String oldValue, String newValue) {
        User performedBy = getCurrentUser();
        AuditLog auditLog = AuditLog.builder()
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .fieldName(fieldName)
                .oldValue(oldValue)
                .newValue(newValue)
                .performedBy(performedBy)
                .build();
        auditLogRepository.save(auditLog);
        log.info("Audit: {} {} on {}#{} - {} changed from '{}' to '{}'",
                performedBy != null ? performedBy.getEmail() : "SYSTEM",
                action, entityType, entityId, fieldName, oldValue, newValue);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAction(String entityType, Long entityId, String action) {
        User performedBy = getCurrentUser();
        AuditLog auditLog = AuditLog.builder()
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .performedBy(performedBy)
                .build();
        auditLogRepository.save(auditLog);
        log.info("Audit: {} {} {}#{}",
                performedBy != null ? performedBy.getEmail() : "SYSTEM",
                action, entityType, entityId);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> getAuditLogsByEntity(String entityType, Long entityId, Pageable pageable) {
        return auditLogRepository.findByEntityTypeAndEntityId(entityType, entityId, pageable)
                .map(auditLogMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> getAuditLogsByUser(Long userId, Pageable pageable) {
        return auditLogRepository.findByPerformedById(userId, pageable)
                .map(auditLogMapper::toResponse);
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        String email = authentication.getName();
        return userRepository.findByEmail(email).orElse(null);
    }
}
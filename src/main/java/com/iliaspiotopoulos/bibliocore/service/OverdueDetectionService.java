package com.iliaspiotopoulos.bibliocore.service;

import com.iliaspiotopoulos.bibliocore.model.entity.Loan;
import com.iliaspiotopoulos.bibliocore.model.enums.LoanStatus;
import com.iliaspiotopoulos.bibliocore.repository.LoanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class OverdueDetectionService {

    private static final Logger log = LoggerFactory.getLogger(OverdueDetectionService.class);

    private final LoanRepository loanRepository;
    private final AuditService auditService;

    public OverdueDetectionService(LoanRepository loanRepository, AuditService auditService) {
        this.loanRepository = loanRepository;
        this.auditService = auditService;
    }

    @Scheduled(cron = "${bibliocore.loan.overdue-scan-cron}")
    @Transactional
    public void detectOverdueLoans() {
        log.info("Starting overdue loan detection scan");
        LocalDate today = LocalDate.now();

        List<Loan> overdueLoans = loanRepository.findOverdueLoans(today);

        if (overdueLoans.isEmpty()) {
            log.info("No overdue loans detected");
            return;
        }

        for (Loan loan : overdueLoans) {
            loan.setStatus(LoanStatus.OVERDUE);
            loanRepository.save(loan);

            auditService.logStateChange(
                    "LOAN",
                    loan.getId(),
                    "OVERDUE_DETECTION",
                    "status",
                    LoanStatus.ACTIVE.name(),
                    LoanStatus.OVERDUE.name()
            );

            log.info("Loan {} for member {} marked as OVERDUE (due date: {})",
                    loan.getId(),
                    loan.getMember().getId(),
                    loan.getDueDate());
        }

        log.info("Marked {} loan(s) as OVERDUE", overdueLoans.size());
    }

    @Transactional
    public int runManualOverdueDetection() {
        log.info("Running manual overdue detection");
        LocalDate today = LocalDate.now();

        List<Loan> overdueLoans = loanRepository.findOverdueLoans(today);

        for (Loan loan : overdueLoans) {
            loan.setStatus(LoanStatus.OVERDUE);
            loanRepository.save(loan);

            auditService.logStateChange(
                    "LOAN",
                    loan.getId(),
                    "MANUAL_OVERDUE_DETECTION",
                    "status",
                    LoanStatus.ACTIVE.name(),
                    LoanStatus.OVERDUE.name()
            );
        }

        log.info("Manually marked {} loan(s) as OVERDUE", overdueLoans.size());
        return overdueLoans.size();
    }
}
package com.iliaspiotopoulos.bibliocore.dto.request;

import com.iliaspiotopoulos.bibliocore.model.enums.MembershipStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateMemberRequest(
        @Size(max = 255, message = "Name must not exceed 255 characters")
        String name,

        MembershipStatus membershipStatus,

        @Min(value = 0, message = "Loan limit cannot be negative")
        Integer loanLimit
) {}
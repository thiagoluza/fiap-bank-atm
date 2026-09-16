package com.fiap.bank.atm.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TransactionDTO(
        UUID id,
        String type,
        String typeDescription,
        BigDecimal amount,
        String description,
        LocalDateTime timestamp) {
}

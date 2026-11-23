package com.portafolio.banca.dto;

import com.portafolio.banca.entity.CustomerStatus;

import java.time.Instant;

public record CustomerResponse(Long id, String fullName, String email, String phone, CustomerStatus status,
                               Long accountCount, Instant createdAt) {
}

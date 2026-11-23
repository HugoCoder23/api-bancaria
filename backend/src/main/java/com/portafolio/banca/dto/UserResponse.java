package com.portafolio.banca.dto;

import com.portafolio.banca.entity.Role;

/** customerId solo está presente para usuarios con rol CUSTOMER. */
public record UserResponse(Long id, String name, String email, Role role, Long customerId) {
}

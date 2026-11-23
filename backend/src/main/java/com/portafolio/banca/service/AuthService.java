package com.portafolio.banca.service;

import com.portafolio.banca.dto.LoginRequest;
import com.portafolio.banca.dto.LoginResponse;
import com.portafolio.banca.dto.UserResponse;
import com.portafolio.banca.entity.AuditResult;
import com.portafolio.banca.entity.User;
import com.portafolio.banca.mapper.BankMapper;
import com.portafolio.banca.repository.UserRepository;
import com.portafolio.banca.security.CurrentUserProvider;
import com.portafolio.banca.security.JwtService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final BankMapper mapper;
    private final CurrentUserProvider currentUser;
    private final AuditService audit;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                       BankMapper mapper, CurrentUserProvider currentUser, AuditService audit) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.mapper = mapper;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    public LoginResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        Optional<User> user = userRepository.findByEmailIgnoreCase(email)
                .filter(User::isActive)
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()));
        if (user.isEmpty()) {
            // Mismo mensaje para email inexistente, usuario inactivo o contraseña incorrecta.
            audit.recordIndependent(email, "LOGIN", null, AuditResult.DENIED, "Credenciales inválidas");
            throw new BadCredentialsException("Credenciales inválidas");
        }
        audit.recordIndependent(email, "LOGIN", "user:" + user.get().getId(), AuditResult.SUCCESS, null);
        JwtService.IssuedToken token = jwtService.issue(user.get());
        return new LoginResponse(token.value(), "Bearer", token.expiresAt(), mapper.toResponse(user.get()));
    }

    public UserResponse me() {
        return mapper.toResponse(currentUser.get());
    }
}

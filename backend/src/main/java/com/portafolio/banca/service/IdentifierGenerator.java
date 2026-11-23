package com.portafolio.banca.service;

import com.portafolio.banca.repository.AccountRepository;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** Genera números de cuenta y referencias de operación. */
@Component
public class IdentifierGenerator {

    /** Prefijo que identifica las cuentas como ficticias. No sigue el formato de ninguna tarjeta ni CLABE real. */
    public static final String ACCOUNT_PREFIX = "99";
    private static final String REF_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // sin 0/O ni 1/I
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private final SecureRandom random = new SecureRandom();
    private final AccountRepository accounts;
    private final Clock clock;

    public IdentifierGenerator(AccountRepository accounts, Clock clock) {
        this.accounts = accounts;
        this.clock = clock;
    }

    /** 12 dígitos: "99" + 10 aleatorios. La restricción UNIQUE de la base es la garantía final. */
    public String newAccountNumber() {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder sb = new StringBuilder(ACCOUNT_PREFIX);
            for (int i = 0; i < 10; i++) sb.append(random.nextInt(10));
            String number = sb.toString();
            if (!accounts.existsByNumber(number)) return number;
        }
        throw new IllegalStateException("No se pudo generar un número de cuenta único");
    }

    /** Referencia legible y única, p. ej. TRX-20261123-K7Q2M9XA. */
    public String newReference() {
        StringBuilder sb = new StringBuilder("TRX-").append(DAY.format(clock.instant())).append('-');
        for (int i = 0; i < 8; i++) sb.append(REF_ALPHABET.charAt(random.nextInt(REF_ALPHABET.length())));
        return sb.toString();
    }
}

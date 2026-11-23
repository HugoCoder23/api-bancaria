package com.portafolio.banca.service;

import com.portafolio.banca.entity.Account;
import com.portafolio.banca.entity.AccountType;
import com.portafolio.banca.entity.Currency;
import com.portafolio.banca.entity.Customer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.portafolio.banca.mapper.BankMapper.mask;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pruebas unitarias del modelo (sin Spring). */
class AccountDomainTest {

    private Account account() {
        return new Account(new Customer("Test", "t@test.local", "5550000000"), "990000000001",
                AccountType.SAVINGS, Currency.MXN);
    }

    @Test
    @DisplayName("credit/debit usan BigDecimal exacto (0.10 + 0.20 = 0.30)")
    void exactArithmetic() {
        Account a = account();
        a.credit(new BigDecimal("0.10"));
        a.credit(new BigDecimal("0.20"));
        assertThat(a.getBalance()).isEqualByComparingTo("0.30");
        a.debit(new BigDecimal("0.30"));
        assertThat(a.getBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("El saldo nunca puede quedar negativo, aunque se omitan las validaciones del servicio")
    void balanceNeverNegative() {
        Account a = account();
        a.credit(new BigDecimal("10.00"));
        assertThatThrownBy(() -> a.debit(new BigDecimal("10.01"))).isInstanceOf(IllegalStateException.class);
        assertThat(a.getBalance()).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("Importes cero o negativos se rechazan en el dominio")
    void nonPositiveAmounts() {
        Account a = account();
        assertThatThrownBy(() -> a.credit(BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> a.debit(new BigDecimal("-1"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Huella de idempotencia: estable y sensible a cada parámetro")
    void fingerprint() {
        String h1 = IdempotencyService.fingerprint("TRANSFER", 1L, "990000000004", new BigDecimal("10.00"), null);
        String h2 = IdempotencyService.fingerprint("TRANSFER", 1L, "990000000004", new BigDecimal("10.00"), null);
        String h3 = IdempotencyService.fingerprint("TRANSFER", 1L, "990000000004", new BigDecimal("10.01"), null);
        String h4 = IdempotencyService.fingerprint("TRANSFER", 11L, "990000000004", new BigDecimal("0.00"), null);
        assertThat(h1).hasSize(64).isEqualTo(h2).isNotEqualTo(h3).isNotEqualTo(h4);
    }

    @Test
    @DisplayName("Los números de cuenta se enmascaran mostrando solo los últimos 4 dígitos")
    void masking() {
        assertThat(mask("990000001234")).isEqualTo("•••• 1234");
    }
}

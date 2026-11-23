package com.portafolio.banca.repository;

import com.portafolio.banca.entity.*;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

/** Filtros dinámicos para los listados paginados. Un parámetro nulo o vacío no filtra. */
public final class Specs {

    private Specs() {}

    private static String like(String q) {
        return "%" + q.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }

    // ---------- Clientes ----------

    public static Specification<Customer> customers(String q, CustomerStatus status) {
        return (root, query, cb) -> {
            var p = cb.conjunction();
            if (!blank(q)) {
                String pattern = like(q);
                p = cb.and(p, cb.or(
                        cb.like(cb.lower(root.get("fullName")), pattern, '\\'),
                        cb.like(cb.lower(root.get("email")), pattern, '\\')));
            }
            if (status != null) p = cb.and(p, cb.equal(root.get("status"), status));
            return p;
        };
    }

    // ---------- Cuentas ----------

    public static Specification<Account> accounts(Long customerId, AccountStatus status, Currency currency, String number) {
        return (root, query, cb) -> {
            var p = cb.conjunction();
            if (customerId != null) p = cb.and(p, cb.equal(root.get("customer").get("id"), customerId));
            if (status != null) p = cb.and(p, cb.equal(root.get("status"), status));
            if (currency != null) p = cb.and(p, cb.equal(root.get("currency"), currency));
            if (!blank(number)) {
                p = cb.and(p, cb.like(root.get("number"), like(number.replaceAll("[\\s-]", "")), '\\'));
            }
            return p;
        };
    }

    // ---------- Movimientos de una cuenta ----------

    public static Specification<Transaction> accountHistory(Long accountId, TransactionType type, TransactionStatus status) {
        return (root, query, cb) -> {
            var p = cb.or(
                    cb.equal(root.get("sourceAccount").get("id"), accountId),
                    cb.equal(root.get("destinationAccount").get("id"), accountId));
            if (type != null) p = cb.and(p, cb.equal(root.get("type"), type));
            if (status != null) p = cb.and(p, cb.equal(root.get("status"), status));
            return p;
        };
    }

    // ---------- Auditoría ----------

    public static Specification<AuditEvent> audit(String action, String actor, AuditResult result) {
        return (root, query, cb) -> {
            var p = cb.conjunction();
            if (!blank(action)) p = cb.and(p, cb.equal(root.get("action"), action.trim().toUpperCase(Locale.ROOT)));
            if (!blank(actor)) p = cb.and(p, cb.like(cb.lower(root.get("actor")), like(actor), '\\'));
            if (result != null) p = cb.and(p, cb.equal(root.get("result"), result));
            return p;
        };
    }
}

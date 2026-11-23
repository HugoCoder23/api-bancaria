package com.portafolio.banca.security;

import com.portafolio.banca.entity.Account;
import com.portafolio.banca.entity.Transaction;
import com.portafolio.banca.entity.User;
import com.portafolio.banca.exception.ResourceNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Reglas de propiedad de recursos, centralizadas para que ningún endpoint las olvide.
 *
 * <ul>
 *   <li>Un cliente solo ve y opera sus propias cuentas. Para cuentas ajenas recibe 404 (no 403),
 *       así no puede averiguar qué identificadores existen.</li>
 *   <li>Operadores y administradores consultan cualquier cuenta, pero nunca mueven dinero de clientes.</li>
 * </ul>
 */
@Component
public class AccessPolicy {

    public static final String ACCOUNT_NOT_FOUND = "La cuenta no existe";

    public boolean canSee(User actor, Account account) {
        return account != null && (actor.isStaff() || account.isOwnedBy(actor.getCustomerId()));
    }

    public Account requireReadable(User actor, Account account) {
        if (!canSee(actor, account)) throw new ResourceNotFoundException(ACCOUNT_NOT_FOUND);
        return account;
    }

    /** Solo el titular puede depositar, retirar o transferir desde su cuenta. */
    public void requireMoneyMover(User actor, Account account) {
        if (actor.isStaff()) {
            throw new AccessDeniedException("Los operadores y administradores no pueden mover fondos de clientes");
        }
        if (!account.isOwnedBy(actor.getCustomerId())) throw new ResourceNotFoundException(ACCOUNT_NOT_FOUND);
    }

    /** Un movimiento es visible si el usuario es staff o titular de alguna de las cuentas involucradas. */
    public boolean canSee(User actor, Transaction tx) {
        return actor.isStaff() || canSee(actor, tx.getSourceAccount()) || canSee(actor, tx.getDestinationAccount());
    }
}

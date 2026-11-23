package com.portafolio.banca.repository;

import com.portafolio.banca.entity.Account;
import com.portafolio.banca.entity.AccountStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long>, JpaSpecificationExecutor<Account> {

    /**
     * SELECT ... FOR UPDATE sobre la fila de la cuenta. Las operaciones que mueven dinero la usan para que
     * dos peticiones concurrentes sobre la misma cuenta se ejecuten en serie (evita el doble gasto).
     * No hace JOIN con el cliente para no bloquear también su fila (reduciría la concurrencia y podría
     * provocar interbloqueos entre transferencias cruzadas).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") Long id);

    @Query("select a.id from Account a where a.number = :number")
    Optional<Long> findIdByNumber(@Param("number") String number);

    @EntityGraph(attributePaths = "customer")
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findWithCustomerById(@Param("id") Long id);

    boolean existsByNumber(String number);

    long countByStatus(AccountStatus status);

    long countByCustomerId(Long customerId);

    @Override
    @EntityGraph(attributePaths = "customer")
    Page<Account> findAll(Specification<Account> spec, Pageable pageable);

    /** Total de saldos por moneda de las cuentas no cerradas: [currency, sum(balance), count]. */
    @Query("select a.currency, sum(a.balance), count(a) from Account a where a.status <> com.portafolio.banca.entity.AccountStatus.CLOSED group by a.currency order by a.currency")
    List<Object[]> balancesByCurrency();
}

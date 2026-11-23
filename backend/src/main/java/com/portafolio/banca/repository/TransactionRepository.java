package com.portafolio.banca.repository;

import com.portafolio.banca.entity.Transaction;
import com.portafolio.banca.entity.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

    @EntityGraph(attributePaths = {"sourceAccount.customer", "destinationAccount.customer"})
    Optional<Transaction> findByReference(String reference);

    boolean existsByReference(String reference);

    @Override
    @EntityGraph(attributePaths = {"sourceAccount.customer", "destinationAccount.customer"})
    Page<Transaction> findAll(Specification<Transaction> spec, Pageable pageable);

    long countByStatusAndCreatedAtGreaterThanEqual(TransactionStatus status, Instant since);

    /** Volumen completado desde una fecha: [currency, sum(amount), count]. */
    @Query("select t.currency, sum(t.amount), count(t) from Transaction t "
            + "where t.status = com.portafolio.banca.entity.TransactionStatus.COMPLETED and t.createdAt >= :since "
            + "group by t.currency order by t.currency")
    List<Object[]> completedVolumeSince(@Param("since") Instant since);
}

package com.portafolio.banca.repository;

import com.portafolio.banca.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    @EntityGraph(attributePaths = "customer")
    Optional<User> findByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = "customer")
    @Query("select u from User u where u.id = :id")
    Optional<User> findWithCustomerById(@Param("id") Long id);

    boolean existsByEmailIgnoreCase(String email);
}

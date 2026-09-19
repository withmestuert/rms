package com.rms.backend.users.repository;

import com.rms.backend.users.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    java.util.List<User> findByRole(String role);
    java.util.List<User> findByOwnerIdOrId(Long ownerId, Long id);
    boolean existsByRole(String role);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where u.username = :username")
    Optional<User> findForLogin(@org.springframework.data.repository.query.Param("username") String username);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where u.id = :id")
    Optional<User> findForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);
}

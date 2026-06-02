package com.tubereturns.repository;

import com.tubereturns.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByVerificationToken(String token);

    Optional<User> findByPasswordResetToken(String token);

    Optional<User> findByProviderAndProviderId(String provider, String providerId);

    @Query("SELECT u.provider, COUNT(u) FROM User u GROUP BY u.provider")
    List<Object[]> countByProvider();
}

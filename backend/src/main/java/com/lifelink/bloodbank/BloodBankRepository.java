package com.lifelink.bloodbank;

import com.lifelink.user.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BloodBankRepository extends JpaRepository<BloodBank, Long> {

    Optional<BloodBank> findByUserId(Long userId);

    /** Admin verification queue; fetches the user eagerly to keep the listing one query. */
    @Query("SELECT b FROM BloodBank b JOIN FETCH b.user u WHERE u.status = :status ORDER BY b.id")
    List<BloodBank> findByUserStatus(@Param("status") UserStatus status);

    long countByVerifiedTrue();

    long countByUserStatus(UserStatus status);
}

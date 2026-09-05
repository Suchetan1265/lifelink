package com.lifelink.hospital;

import com.lifelink.user.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HospitalRepository extends JpaRepository<Hospital, Long> {

    Optional<Hospital> findByUserId(Long userId);

    /** Admin verification queue; fetches the user eagerly to keep the listing one query. */
    @Query("SELECT h FROM Hospital h JOIN FETCH h.user u WHERE u.status = :status ORDER BY h.id")
    List<Hospital> findByUserStatus(@Param("status") UserStatus status);

    long countByVerifiedTrue();

    long countByUserStatus(UserStatus status);
}

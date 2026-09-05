package com.lifelink.bloodbank;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BloodBankRepository extends JpaRepository<BloodBank, Long> {

    Optional<BloodBank> findByUserId(Long userId);
}

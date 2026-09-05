package com.lifelink.bloodbank;

import com.lifelink.common.BloodGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BloodInventoryRepository extends JpaRepository<BloodInventory, Long> {

    List<BloodInventory> findByBloodBankId(Long bloodBankId);

    Optional<BloodInventory> findByBloodBankIdAndBloodGroup(Long bloodBankId, BloodGroup bloodGroup);
}

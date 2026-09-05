package com.lifelink.request;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestRepository extends JpaRepository<Request, Long> {

    Page<Request> findByHospitalId(Long hospitalId, Pageable pageable);

    Page<Request> findByHospitalIdAndStatus(Long hospitalId, RequestStatus status, Pageable pageable);
}

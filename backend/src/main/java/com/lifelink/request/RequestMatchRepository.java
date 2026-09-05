package com.lifelink.request;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RequestMatchRepository extends JpaRepository<RequestMatch, Long> {

    List<RequestMatch> findByRequestId(Long requestId);

    List<RequestMatch> findByDonorUserIdOrderByNotifiedAtDesc(Long donorUserId);

    Optional<RequestMatch> findByRequestIdAndDonorUserId(Long requestId, Long donorUserId);
}

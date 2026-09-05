package com.lifelink.request;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RequestMatchRepository extends JpaRepository<RequestMatch, Long> {

    List<RequestMatch> findByRequestId(Long requestId);

    List<RequestMatch> findByDonorUserIdOrderByNotifiedAtDesc(Long donorUserId);

    Optional<RequestMatch> findByRequestIdAndDonorUserId(Long requestId, Long donorUserId);

    /** A donor is only ever tied to one live request at a time. */
    @Query("""
            SELECT COUNT(m) > 0 FROM RequestMatch m
            WHERE m.donor.userId = :donorId
              AND m.status IN (com.lifelink.request.MatchStatus.NOTIFIED,
                               com.lifelink.request.MatchStatus.ACCEPTED,
                               com.lifelink.request.MatchStatus.CONFIRMED)
              AND m.request.status IN (com.lifelink.request.RequestStatus.RAISED,
                                       com.lifelink.request.RequestStatus.MATCHED,
                                       com.lifelink.request.RequestStatus.CONFIRMED,
                                       com.lifelink.request.RequestStatus.ESCALATED)
            """)
    boolean existsActiveMatchForDonor(@Param("donorId") Long donorId);
}

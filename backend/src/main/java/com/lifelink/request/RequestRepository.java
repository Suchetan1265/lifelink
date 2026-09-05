package com.lifelink.request;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface RequestRepository extends JpaRepository<Request, Long> {

    Page<Request> findByHospitalId(Long hospitalId, Pageable pageable);

    Page<Request> findByHospitalIdAndStatus(Long hospitalId, RequestStatus status, Pageable pageable);

    /** Active requests with hospital and owner loaded, for the maintenance jobs and bank queue. */
    @Query("SELECT r FROM Request r JOIN FETCH r.hospital h JOIN FETCH h.user WHERE r.status IN :statuses")
    List<Request> findByStatusInWithHospital(@Param("statuses") Collection<RequestStatus> statuses);

    /** [status, count] rows for the admin dashboard. */
    @Query("SELECT r.status, COUNT(r) FROM Request r GROUP BY r.status")
    List<Object[]> countGroupedByStatus();

    /** Mean hours from raise to fulfillment; null until something is fulfilled. */
    @Query(value = """
            SELECT AVG(EXTRACT(EPOCH FROM (closed_at - created_at)) / 3600.0)
            FROM requests
            WHERE status = 'FULFILLED' AND closed_at IS NOT NULL
            """, nativeQuery = true)
    Double avgHoursToFulfill();
}

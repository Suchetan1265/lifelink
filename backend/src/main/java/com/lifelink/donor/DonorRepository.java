package com.lifelink.donor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface DonorRepository extends JpaRepository<Donor, Long> {

    /**
     * Matching engine v1 (spec §2.E.1), plain-SQL Haversine — the Redis GEO
     * variant replaces this in week 3. Returns [user_id, distance_km] rows for
     * donors who are available, eligible, within their own travel radius of the
     * hospital, and not already tied to an active request; nearest first.
     */
    @Query(value = """
            SELECT t.user_id, t.distance_km
            FROM (
                SELECT d.user_id,
                       d.radius_km,
                       6371.0 * acos(least(1.0,
                           cos(radians(:lat)) * cos(radians(d.lat)) * cos(radians(d.lng) - radians(:lng))
                           + sin(radians(:lat)) * sin(radians(d.lat)))) AS distance_km
                FROM donors d
                WHERE d.available = true
                  AND d.blood_group IN (:groups)
                  AND (d.next_eligible_date IS NULL OR d.next_eligible_date <= CURRENT_DATE)
                  AND NOT EXISTS (
                      SELECT 1
                      FROM request_matches rm
                      JOIN requests r ON r.id = rm.request_id
                      WHERE rm.donor_id = d.user_id
                        AND rm.status IN ('NOTIFIED', 'ACCEPTED', 'CONFIRMED')
                        AND r.status IN ('RAISED', 'MATCHED', 'CONFIRMED', 'ESCALATED')
                  )
            ) t
            WHERE t.distance_km <= t.radius_km
            ORDER BY t.distance_km
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findMatchCandidates(
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("groups") Collection<String> groups,
            @Param("limit") int limit);

    /** Everyone who belongs in the Redis GEO sets right now (startup rebuild). */
    @Query("SELECT d FROM Donor d WHERE d.available = true "
            + "AND (d.nextEligibleDate IS NULL OR d.nextEligibleDate <= :today)")
    List<Donor> findMatchable(@Param("today") LocalDate today);

    /**
     * Narrows Redis GEO hits to donors who are still matchable in the source
     * of truth and free of an active match, which Redis cannot know.
     *
     * @return [user_id, radius_km] rows
     */
    @Query(value = """
            SELECT d.user_id, d.radius_km
            FROM donors d
            WHERE d.user_id IN (:ids)
              AND d.available = true
              AND (d.next_eligible_date IS NULL OR d.next_eligible_date <= CURRENT_DATE)
              AND NOT EXISTS (
                  SELECT 1
                  FROM request_matches rm
                  JOIN requests r ON r.id = rm.request_id
                  WHERE rm.donor_id = d.user_id
                    AND rm.status IN ('NOTIFIED', 'ACCEPTED', 'CONFIRMED')
                    AND r.status IN ('RAISED', 'MATCHED', 'CONFIRMED', 'ESCALATED')
              )
            """, nativeQuery = true)
    List<Object[]> findMatchableAmong(@Param("ids") Collection<Long> ids);

    /** Donors whose 90-day cooldown ends on the given day (eligibility job). */
    List<Donor> findByNextEligibleDate(LocalDate date);

    long countByAvailableTrue();

    /** [bloodGroup, count] rows for the admin dashboard. */
    @Query("SELECT d.bloodGroup, COUNT(d) FROM Donor d GROUP BY d.bloodGroup ORDER BY COUNT(d) DESC")
    List<Object[]> countGroupedByBloodGroup();

    /** [city, count] rows for the admin dashboard. */
    @Query("SELECT d.city, COUNT(d) FROM Donor d GROUP BY d.city ORDER BY COUNT(d) DESC")
    List<Object[]> countGroupedByCity();
}

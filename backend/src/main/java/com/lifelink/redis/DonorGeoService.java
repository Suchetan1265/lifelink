package com.lifelink.redis;

import com.lifelink.common.BloodGroup;
import com.lifelink.donor.Donor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maintains the {@code donors:geo:{bloodGroup}} GEO sets that matching searches
 * (spec §7). Membership means "available and off cooldown right now", so the
 * set is written through on every change that could flip either condition.
 *
 * <p>Postgres stays the source of truth: every method here swallows Redis
 * failures, and {@link DonorGeoBootstrap} rebuilds the sets from the database
 * on startup so a wiped or cold Redis never silently narrows matching.
 */
@Service
@Slf4j
public class DonorGeoService {

    private static final String KEY_PREFIX = "donors:geo:";

    private final StringRedisTemplate redis;

    public DonorGeoService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public static String keyFor(BloodGroup group) {
        return KEY_PREFIX + group.name();
    }

    /** Places the donor in the set for their group, or removes them if they should not be matched. */
    public void index(Donor donor) {
        try {
            // A blood group or location change must not leave a stale entry behind.
            removeFromAllSets(donor.getUserId());
            if (isMatchable(donor)) {
                redis.opsForGeo().add(
                        keyFor(donor.getBloodGroup()),
                        new Point(donor.getLng(), donor.getLat()),
                        String.valueOf(donor.getUserId()));
            }
        } catch (DataAccessException e) {
            log.warn("Could not index donor {} in Redis; matching will fall back to SQL", donor.getUserId());
        }
    }

    /**
     * Replaces the whole index from the database.
     *
     * @return how many donors were indexed, or -1 when Redis is unreachable
     */
    public int rebuild(Collection<Donor> matchable) {
        try {
            for (BloodGroup group : BloodGroup.values()) {
                redis.delete(keyFor(group));
            }
            for (Donor donor : matchable) {
                redis.opsForGeo().add(
                        keyFor(donor.getBloodGroup()),
                        new Point(donor.getLng(), donor.getLat()),
                        String.valueOf(donor.getUserId()));
            }
            return matchable.size();
        } catch (DataAccessException e) {
            return -1;
        }
    }

    public void remove(Long donorId) {
        try {
            removeFromAllSets(donorId);
        } catch (DataAccessException e) {
            log.warn("Could not remove donor {} from Redis", donorId);
        }
    }

    /**
     * Nearest matchable donors of the given groups, closest first.
     *
     * @param radiusKm how far to look; callers still apply each donor's own
     *                 travel radius, which Redis does not know about
     * @return donor id to distance in km, or null when Redis is unreachable so
     *         the caller can fall back to the SQL matcher
     */
    public Map<Long, Double> search(
            double lat, double lng, double radiusKm, Collection<BloodGroup> groups) {
        try {
            List<GeoResult<RedisGeoCommands.GeoLocation<String>>> hits = new ArrayList<>();
            Circle area = new Circle(new Point(lng, lat), new Distance(radiusKm, Metrics.KILOMETERS));

            for (BloodGroup group : groups) {
                GeoResults<RedisGeoCommands.GeoLocation<String>> results =
                        redis.opsForGeo().radius(keyFor(group), area,
                                RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs().includeDistance());
                if (results != null) {
                    hits.addAll(results.getContent());
                }
            }

            return hits.stream()
                    .sorted(java.util.Comparator.comparingDouble(hit -> hit.getDistance().getValue()))
                    .collect(LinkedHashMap::new,
                            (map, hit) -> map.putIfAbsent(
                                    Long.valueOf(hit.getContent().getName()),
                                    hit.getDistance().getValue()),
                            LinkedHashMap::putAll);
        } catch (DataAccessException | NumberFormatException e) {
            log.warn("Redis GEO search unavailable, falling back to the SQL matcher: {}", e.getMessage());
            return null;
        }
    }

    private void removeFromAllSets(Long donorId) {
        String member = String.valueOf(donorId);
        for (BloodGroup group : BloodGroup.values()) {
            redis.opsForZSet().remove(keyFor(group), member);
        }
    }

    /** Mirrors the availability and eligibility filters the SQL matcher applies. */
    private static boolean isMatchable(Donor donor) {
        return donor.isAvailable()
                && (donor.getNextEligibleDate() == null
                        || !donor.getNextEligibleDate().isAfter(LocalDate.now()));
    }
}

package com.lifelink.redis;

import com.lifelink.common.TooManyRequestsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Caps how many requests one hospital can raise per hour (spec §2.B.2, §7).
 *
 * <p>Deliberately fails open: if Redis is down, a hospital with a genuine
 * emergency must still be able to raise a request. Losing the cap for the
 * length of an outage is the cheaper failure.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RequestRateLimiter {

    static final int MAX_REQUESTS_PER_HOUR = 10;

    private static final String KEY_PREFIX = "rl:requests:";
    private static final Duration WINDOW = Duration.ofHours(1);

    private final StringRedisTemplate redis;

    /** @throws TooManyRequestsException when the hospital is over its hourly allowance */
    public void recordRequest(Long hospitalId) {
        Long count;
        try {
            String key = KEY_PREFIX + hospitalId;
            count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, WINDOW);
            }
        } catch (DataAccessException e) {
            log.warn("Rate limiter unavailable, allowing the request through: {}", e.getMessage());
            return;
        }

        if (count != null && count > MAX_REQUESTS_PER_HOUR) {
            throw new TooManyRequestsException(
                    "Limit of " + MAX_REQUESTS_PER_HOUR + " requests per hour reached; try again shortly");
        }
    }
}

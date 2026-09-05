package com.lifelink.redis;

import com.lifelink.donor.Donor;
import com.lifelink.donor.DonorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Rebuilds the donor GEO sets from Postgres on startup, so a cold or flushed
 * Redis cannot silently shrink the pool of donors matching can reach.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DonorGeoBootstrap implements ApplicationRunner {

    private final DonorRepository donorRepository;
    private final DonorGeoService donorGeoService;

    @Override
    @Transactional(readOnly = true)
    public void run(ApplicationArguments args) {
        List<Donor> matchable = donorRepository.findMatchable(LocalDate.now());
        int indexed = donorGeoService.rebuild(matchable);
        if (indexed < 0) {
            log.warn("Redis unreachable at startup; matching uses the SQL fallback until it returns");
        } else {
            log.info("Indexed {} matchable donor(s) into the Redis GEO sets", indexed);
        }
    }
}

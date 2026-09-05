package com.lifelink.donor;

import com.lifelink.notification.NotificationService;
import com.lifelink.redis.DonorGeoService;
import com.lifelink.notification.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Daily eligibility refresh (spec §2.E.4).
 *
 * <p>The {@code available} flag is the donor's own choice and is deliberately
 * left alone: coming off cooldown must not opt someone back in silently. What
 * changes is that they now pass the eligibility filter in matching.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DonorEligibilityService {

    private final DonorRepository donorRepository;
    private final NotificationService notificationService;
    private final DonorGeoService donorGeoService;

    /** @return how many donors became eligible today */
    @Transactional
    public int notifyNewlyEligibleDonors() {
        List<Donor> donors = donorRepository.findByNextEligibleDate(LocalDate.now());
        for (Donor donor : donors) {
            // Back in the GEO sets, but only if they still have availability on.
            donorGeoService.index(donor);
            notificationService.notify(donor.getUserId(), NotificationType.ELIGIBLE_AGAIN,
                    "You can donate again",
                    "Your 90-day wait is over. Turn availability on to start receiving nearby requests.");
        }
        if (!donors.isEmpty()) {
            log.info("Notified {} donor(s) that their cooldown ended", donors.size());
        }
        return donors.size();
    }
}

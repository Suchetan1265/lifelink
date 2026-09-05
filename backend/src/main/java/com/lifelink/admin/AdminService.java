package com.lifelink.admin;

import com.lifelink.admin.dto.PendingVerificationResponse;
import com.lifelink.admin.dto.PlatformStatsResponse;
import com.lifelink.admin.dto.UserSummaryResponse;
import com.lifelink.admin.dto.VerificationDecisionResponse;
import com.lifelink.auth.RefreshTokenRepository;
import com.lifelink.bloodbank.BloodBank;
import com.lifelink.bloodbank.BloodBankRepository;
import com.lifelink.common.BadRequestException;
import com.lifelink.common.BloodGroup;
import com.lifelink.common.ConflictException;
import com.lifelink.common.NotFoundException;
import com.lifelink.donor.DonorRepository;
import com.lifelink.hospital.Hospital;
import com.lifelink.hospital.HospitalRepository;
import com.lifelink.notification.NotificationService;
import com.lifelink.notification.NotificationType;
import com.lifelink.redis.RedisConfig;
import com.lifelink.request.RequestRepository;
import com.lifelink.request.RequestStatus;
import com.lifelink.user.User;
import com.lifelink.user.UserRepository;
import com.lifelink.user.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin operations (spec §2.D): verifying hospitals and blood banks, platform
 * stats, and enabling/disabling accounts.
 *
 * <p>Verification gates the whole request flow — a hospital registers as
 * PENDING and cannot raise requests until it is approved here.
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final HospitalRepository hospitalRepository;
    private final BloodBankRepository bloodBankRepository;
    private final DonorRepository donorRepository;
    private final RequestRepository requestRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public List<PendingVerificationResponse> pendingVerifications(VerificationType type) {
        return switch (type) {
            case HOSPITAL -> hospitalRepository.findByUserStatus(UserStatus.PENDING).stream()
                    .map(PendingVerificationResponse::from)
                    .toList();
            case BLOODBANK -> bloodBankRepository.findByUserStatus(UserStatus.PENDING).stream()
                    .map(PendingVerificationResponse::from)
                    .toList();
        };
    }

    /** Approving also reactivates a previously rejected account. */
    @Transactional
    public VerificationDecisionResponse approve(Long userId) {
        User user = requireUser(userId);

        return switch (user.getRole()) {
            case HOSPITAL -> {
                Hospital hospital = requireHospital(userId);
                if (hospital.isVerified() && user.getStatus() == UserStatus.ACTIVE) {
                    throw new ConflictException("Hospital is already approved");
                }
                hospital.setVerified(true);
                user.setStatus(UserStatus.ACTIVE);
                notifyDecision(userId, hospital.getName(), true, null);
                yield new VerificationDecisionResponse(
                        userId, VerificationType.HOSPITAL, hospital.getName(), true, user.getStatus());
            }
            case BLOOD_BANK -> {
                BloodBank bank = requireBloodBank(userId);
                if (bank.isVerified() && user.getStatus() == UserStatus.ACTIVE) {
                    throw new ConflictException("Blood bank is already approved");
                }
                bank.setVerified(true);
                user.setStatus(UserStatus.ACTIVE);
                notifyDecision(userId, bank.getName(), true, null);
                yield new VerificationDecisionResponse(
                        userId, VerificationType.BLOODBANK, bank.getName(), true, user.getStatus());
            }
            default -> throw new BadRequestException("Only hospitals and blood banks need verification");
        };
    }

    /** Rejecting disables the account and kills its live sessions. */
    @Transactional
    public VerificationDecisionResponse reject(Long userId, String reason) {
        User user = requireUser(userId);

        VerificationDecisionResponse decision = switch (user.getRole()) {
            case HOSPITAL -> {
                Hospital hospital = requireHospital(userId);
                requireNotAlreadyRejected(hospital.isVerified(), user.getStatus());
                hospital.setVerified(false);
                user.setStatus(UserStatus.DISABLED);
                notifyDecision(userId, hospital.getName(), false, reason);
                yield new VerificationDecisionResponse(
                        userId, VerificationType.HOSPITAL, hospital.getName(), false, user.getStatus());
            }
            case BLOOD_BANK -> {
                BloodBank bank = requireBloodBank(userId);
                requireNotAlreadyRejected(bank.isVerified(), user.getStatus());
                bank.setVerified(false);
                user.setStatus(UserStatus.DISABLED);
                notifyDecision(userId, bank.getName(), false, reason);
                yield new VerificationDecisionResponse(
                        userId, VerificationType.BLOODBANK, bank.getName(), false, user.getStatus());
            }
            default -> throw new BadRequestException("Only hospitals and blood banks need verification");
        };
        refreshTokenRepository.revokeAllForUser(userId);
        return decision;
    }

    @Transactional
    public UserSummaryResponse updateUserStatus(Long adminUserId, Long targetUserId, UserStatus status) {
        if (adminUserId.equals(targetUserId)) {
            throw new BadRequestException("You cannot change your own account status");
        }
        if (status == UserStatus.PENDING) {
            throw new BadRequestException("PENDING is set at registration and cleared by verification");
        }
        User user = requireUser(targetUserId);

        user.setStatus(status);
        if (status == UserStatus.DISABLED) {
            refreshTokenRepository.revokeAllForUser(targetUserId);
        }
        return UserSummaryResponse.from(user);
    }

    /** Cached as {@code stats:admin} for five minutes (spec §7). */
    @Cacheable(cacheNames = RedisConfig.STATS_CACHE, key = "'admin'")
    @Transactional(readOnly = true)
    public PlatformStatsResponse stats() {
        Map<String, Long> requestsByStatus = new LinkedHashMap<>();
        for (RequestStatus status : RequestStatus.values()) {
            requestsByStatus.put(status.name(), 0L);
        }
        long totalRequests = 0;
        for (Object[] row : requestRepository.countGroupedByStatus()) {
            long count = ((Number) row[1]).longValue();
            requestsByStatus.put(((RequestStatus) row[0]).name(), count);
            totalRequests += count;
        }
        long fulfilled = requestsByStatus.get(RequestStatus.FULFILLED.name());
        double fulfillmentRate = totalRequests == 0 ? 0.0 : (double) fulfilled / totalRequests;

        return new PlatformStatsResponse(
                totalRequests,
                requestsByStatus,
                Math.round(fulfillmentRate * 10_000) / 10_000.0,
                requestRepository.avgHoursToFulfill(),
                donorRepository.count(),
                donorRepository.countByAvailableTrue(),
                bloodGroupCounts(),
                toCountMap(donorRepository.countGroupedByCity()),
                hospitalRepository.countByVerifiedTrue(),
                bloodBankRepository.countByVerifiedTrue(),
                hospitalRepository.countByUserStatus(UserStatus.PENDING)
                        + bloodBankRepository.countByUserStatus(UserStatus.PENDING));
    }

    /** Keyed by the label ('O-'), matching how BloodGroup appears everywhere else in the API. */
    private Map<String, Long> bloodGroupCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Object[] row : donorRepository.countGroupedByBloodGroup()) {
            counts.put(((BloodGroup) row[0]).label(), ((Number) row[1]).longValue());
        }
        return counts;
    }

    private static Map<String, Long> toCountMap(List<Object[]> rows) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Object[] row : rows) {
            counts.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }
        return counts;
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> NotFoundException.of("User", userId));
    }

    private Hospital requireHospital(Long userId) {
        return hospitalRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("No hospital profile for user " + userId));
    }

    private BloodBank requireBloodBank(Long userId) {
        return bloodBankRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("No blood bank profile for user " + userId));
    }

    private static void requireNotAlreadyRejected(boolean verified, UserStatus status) {
        if (!verified && status == UserStatus.DISABLED) {
            throw new ConflictException("Registration is already rejected");
        }
    }

    private void notifyDecision(Long userId, String name, boolean approved, String reason) {
        String title = approved ? "Registration approved" : "Registration rejected";
        String body = approved
                ? name + " is verified. You now have full access to LifeLink."
                : name + " was not approved."
                        + (reason == null || reason.isBlank() ? "" : " Reason: " + reason);
        notificationService.notify(userId, NotificationType.VERIFICATION_RESULT, title, body);
    }
}

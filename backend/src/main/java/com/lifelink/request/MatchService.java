package com.lifelink.request;

import com.lifelink.common.ConflictException;
import com.lifelink.common.ForbiddenException;
import com.lifelink.common.NotFoundException;
import com.lifelink.donor.dto.DonorMatchResponse;
import com.lifelink.notification.NotificationService;
import com.lifelink.notification.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Donor responses to matches (spec §2.A.5). */
@Service
@RequiredArgsConstructor
public class MatchService {

    private final RequestMatchRepository requestMatchRepository;
    private final RequestLifecycleService lifecycle;
    private final NotificationService notificationService;

    @Transactional
    public DonorMatchResponse accept(Long donorUserId, Long matchId) {
        RequestMatch match = loadOwnedMatch(donorUserId, matchId);
        Request request = match.getRequest();
        if (request.getStatus() != RequestStatus.RAISED && request.getStatus() != RequestStatus.MATCHED) {
            throw new ConflictException("This request is no longer open for responses");
        }

        match.setStatus(MatchStatus.ACCEPTED);
        match.setRespondedAt(Instant.now());
        // First acceptance moves the request RAISED → MATCHED (spec §3)
        if (request.getStatus() == RequestStatus.RAISED) {
            lifecycle.fire(request, RequestEvent.DONOR_ACCEPTED, donorUserId, null);
        }

        notificationService.notify(request.getHospital().getUser().getId(), NotificationType.DONOR_RESPONDED,
                "A donor accepted your request",
                "%s (%s) accepted your request #%d and is willing to come."
                        .formatted(match.getDonor().getFullName(), match.getDonor().getBloodGroup().label(),
                                request.getId()));
        return DonorMatchResponse.from(match);
    }

    @Transactional
    public DonorMatchResponse decline(Long donorUserId, Long matchId) {
        RequestMatch match = loadOwnedMatch(donorUserId, matchId);
        match.setStatus(MatchStatus.DECLINED);
        match.setRespondedAt(Instant.now());

        notificationService.notify(match.getRequest().getHospital().getUser().getId(),
                NotificationType.DONOR_RESPONDED,
                "A donor declined your request",
                match.getDonor().getFullName() + " declined request #" + match.getRequest().getId() + ".");
        return DonorMatchResponse.from(match);
    }

    private RequestMatch loadOwnedMatch(Long donorUserId, Long matchId) {
        RequestMatch match = requestMatchRepository.findById(matchId)
                .orElseThrow(() -> NotFoundException.of("Match", matchId));
        if (!match.getDonor().getUserId().equals(donorUserId)) {
            throw new ForbiddenException("Not your match");
        }
        if (match.getStatus() != MatchStatus.NOTIFIED) {
            throw new ConflictException("You have already responded to this match");
        }
        return match;
    }
}

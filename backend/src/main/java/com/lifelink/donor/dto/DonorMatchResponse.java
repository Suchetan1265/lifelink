package com.lifelink.donor.dto;

import com.lifelink.common.BloodGroup;
import com.lifelink.request.MatchStatus;
import com.lifelink.request.RequestMatch;
import com.lifelink.request.RequestStatus;
import com.lifelink.request.Urgency;

import java.time.Instant;

/** A request this donor was matched to, seen from the donor's side. */
public record DonorMatchResponse(
        Long matchId,
        Long requestId,
        String hospitalName,
        String hospitalAddress,
        BloodGroup bloodGroup,
        Integer units,
        Urgency urgency,
        Instant neededBy,
        Double distanceKm,
        MatchStatus matchStatus,
        RequestStatus requestStatus,
        Instant notifiedAt,
        Instant respondedAt) {

    public static DonorMatchResponse from(RequestMatch match) {
        var request = match.getRequest();
        return new DonorMatchResponse(
                match.getId(),
                request.getId(),
                request.getHospital().getName(),
                request.getHospital().getAddress(),
                request.getBloodGroup(),
                request.getUnits(),
                request.getUrgency(),
                request.getNeededBy(),
                match.getDistanceKm(),
                match.getStatus(),
                request.getStatus(),
                match.getNotifiedAt(),
                match.getRespondedAt());
    }
}

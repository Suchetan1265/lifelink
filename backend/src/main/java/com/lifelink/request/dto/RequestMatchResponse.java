package com.lifelink.request.dto;

import com.lifelink.common.BloodGroup;
import com.lifelink.request.MatchStatus;
import com.lifelink.request.RequestMatch;

import java.time.Instant;

/** A matched donor and their response, seen from the hospital's side. */
public record RequestMatchResponse(
        Long matchId,
        Long donorId,
        String donorName,
        BloodGroup bloodGroup,
        Double distanceKm,
        MatchStatus status,
        Instant notifiedAt,
        Instant respondedAt) {

    public static RequestMatchResponse from(RequestMatch match) {
        return new RequestMatchResponse(
                match.getId(),
                match.getDonor().getUserId(),
                match.getDonor().getFullName(),
                match.getDonor().getBloodGroup(),
                match.getDistanceKm(),
                match.getStatus(),
                match.getNotifiedAt(),
                match.getRespondedAt());
    }
}

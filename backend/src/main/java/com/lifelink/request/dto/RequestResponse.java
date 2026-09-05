package com.lifelink.request.dto;

import com.lifelink.bloodbank.BloodBank;
import com.lifelink.common.BloodGroup;
import com.lifelink.hospital.Hospital;
import com.lifelink.request.Request;
import com.lifelink.request.RequestStatus;
import com.lifelink.request.Urgency;

import java.time.Instant;

public record RequestResponse(
        Long id,
        String hospitalName,
        Double hospitalLat,
        Double hospitalLng,
        BloodGroup bloodGroup,
        Integer units,
        Urgency urgency,
        Instant neededBy,
        RequestStatus status,
        String notes,
        Instant createdAt,
        Instant escalatedAt,
        Instant closedAt,
        Long acceptedBankId,
        String acceptedBankName) {

    public static RequestResponse from(Request request) {
        BloodBank bank = request.getAcceptedBank();
        Hospital hospital = request.getHospital();
        return new RequestResponse(
                request.getId(),
                hospital.getName(),
                hospital.getLat(),
                hospital.getLng(),
                request.getBloodGroup(),
                request.getUnits(),
                request.getUrgency(),
                request.getNeededBy(),
                request.getStatus(),
                request.getNotes(),
                request.getCreatedAt(),
                request.getEscalatedAt(),
                request.getClosedAt(),
                bank == null ? null : bank.getId(),
                bank == null ? null : bank.getName());
    }
}

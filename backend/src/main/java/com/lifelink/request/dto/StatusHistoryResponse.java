package com.lifelink.request.dto;

import com.lifelink.request.RequestStatus;
import com.lifelink.request.RequestStatusHistory;

import java.time.Instant;

public record StatusHistoryResponse(
        RequestStatus fromStatus,
        RequestStatus toStatus,
        Long changedById,
        String reason,
        Instant changedAt) {

    public static StatusHistoryResponse from(RequestStatusHistory history) {
        return new StatusHistoryResponse(
                history.getFromStatus(),
                history.getToStatus(),
                history.getChangedBy() == null ? null : history.getChangedBy().getId(),
                history.getReason(),
                history.getChangedAt());
    }
}

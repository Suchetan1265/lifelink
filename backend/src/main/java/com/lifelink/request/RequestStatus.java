package com.lifelink.request;

import java.util.Set;

public enum RequestStatus {
    RAISED, MATCHED, CONFIRMED, FULFILLED, ESCALATED, EXPIRED, CANCELLED;

    public boolean isTerminal() {
        return Set.of(FULFILLED, EXPIRED, CANCELLED).contains(this);
    }
}

package com.lifelink.request;

import java.time.Duration;

/** Urgency also drives the escalation deadline (spec §2.C.3). */
public enum Urgency {
    CRITICAL(Duration.ofHours(2)),
    HIGH(Duration.ofHours(6)),
    NORMAL(Duration.ofHours(24));

    private final Duration escalationAfter;

    Urgency(Duration escalationAfter) {
        this.escalationAfter = escalationAfter;
    }

    public Duration escalationAfter() {
        return escalationAfter;
    }
}

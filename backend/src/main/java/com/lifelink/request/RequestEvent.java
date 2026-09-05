package com.lifelink.request;

import java.util.EnumSet;
import java.util.Set;

import static com.lifelink.request.RequestStatus.CONFIRMED;
import static com.lifelink.request.RequestStatus.ESCALATED;
import static com.lifelink.request.RequestStatus.EXPIRED;
import static com.lifelink.request.RequestStatus.FULFILLED;
import static com.lifelink.request.RequestStatus.MATCHED;
import static com.lifelink.request.RequestStatus.RAISED;

/** Request lifecycle transition table (spec §3). */
public enum RequestEvent {
    DONOR_ACCEPTED(EnumSet.of(RAISED), MATCHED),
    HOSPITAL_CONFIRMED(EnumSet.of(MATCHED), CONFIRMED),
    BANK_ACCEPTED(EnumSet.of(ESCALATED), CONFIRMED),
    DONATION_RECORDED(EnumSet.of(CONFIRMED), FULFILLED),
    ESCALATE_TIMEOUT(EnumSet.of(RAISED, MATCHED), ESCALATED),
    NEEDED_BY_PASSED(EnumSet.of(RAISED, MATCHED, ESCALATED), EXPIRED),
    CANCEL(EnumSet.of(RAISED, MATCHED, CONFIRMED, ESCALATED), RequestStatus.CANCELLED);

    private final Set<RequestStatus> sources;
    private final RequestStatus target;

    RequestEvent(Set<RequestStatus> sources, RequestStatus target) {
        this.sources = sources;
        this.target = target;
    }

    public boolean canFireFrom(RequestStatus status) {
        return sources.contains(status);
    }

    public RequestStatus target() {
        return target;
    }
}

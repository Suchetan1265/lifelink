package com.lifelink.notification;

public enum NotificationType {
    MATCH_FOUND(true),         // donor: a nearby request needs your blood group
    MATCH_CONFIRMED(true),     // donor: hospital picked you — come donate
    MATCH_COVERED(false),      // donor: thanks, another donor was confirmed
    DONOR_RESPONDED(false),    // hospital: a donor accepted/declined
    REQUEST_ESCALATED(true),   // blood bank: nearby request needs stock
    BANK_ACCEPTED(false),      // hospital: a blood bank is covering the escalated request
    REQUEST_EXPIRED(false),    // hospital: request passed needed-by unfulfilled
    REQUEST_CANCELLED(true),   // donor: the request you responded to was cancelled
    DONATION_RECORDED(false),  // donor: donation saved, eligibility updated
    ELIGIBLE_AGAIN(false),     // donor: 90-day cooldown is over
    VERIFICATION_RESULT(false), // hospital/bank: admin approved or rejected you
    PASSWORD_RESET(false);      // anyone: a link to choose a new password

    private final boolean warrantsSms;

    NotificationType(boolean warrantsSms) {
        this.warrantsSms = warrantsSms;
    }

    /** SMS costs money, so only the types someone must act on quickly use it. */
    public boolean warrantsSms() {
        return warrantsSms;
    }
}

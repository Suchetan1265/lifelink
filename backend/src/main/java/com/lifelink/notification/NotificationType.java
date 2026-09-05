package com.lifelink.notification;

public enum NotificationType {
    MATCH_FOUND,        // donor: a nearby request needs your blood group
    MATCH_CONFIRMED,    // donor: hospital picked you — come donate
    MATCH_COVERED,      // donor: thanks, another donor was confirmed
    DONOR_RESPONDED,    // hospital: a donor accepted/declined
    REQUEST_ESCALATED,  // blood bank: nearby request needs stock
    REQUEST_EXPIRED,    // hospital: request passed needed-by unfulfilled
    REQUEST_CANCELLED,  // donor: the request you responded to was cancelled
    DONATION_RECORDED,  // donor: donation saved, eligibility updated
    ELIGIBLE_AGAIN,     // donor: 90-day cooldown is over
    VERIFICATION_RESULT // hospital/bank: admin approved or rejected you
}

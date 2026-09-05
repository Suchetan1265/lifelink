package com.lifelink.admin.dto;

import jakarta.validation.constraints.Size;

/** Optional body on reject; the reason is passed on to the applicant. */
public record RejectVerificationRequest(@Size(max = 512) String reason) {
}

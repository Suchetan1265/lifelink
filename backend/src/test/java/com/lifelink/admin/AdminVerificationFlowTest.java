package com.lifelink.admin;

import com.lifelink.auth.dto.LoginRequest;
import com.lifelink.common.BloodGroup;
import com.lifelink.request.Urgency;
import com.lifelink.request.dto.CreateRequestRequest;
import com.lifelink.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the gate admin verification puts on the request flow: a hospital
 * registers PENDING, cannot raise requests, and only can once an admin approves.
 */
class AdminVerificationFlowTest extends IntegrationTest {

    @Test
    void hospitalCanOnlyRaiseRequestsAfterAdminApproval() throws Exception {
        String email = uniqueEmail("hospital");
        String hospitalToken = registerHospital(email, "City General Hospital", 12.9716, 77.5946);

        // Pending hospitals are refused (spec §2.B.1).
        mockMvc.perform(post("/api/requests")
                        .header("Authorization", "Bearer " + hospitalToken)
                        .contentType(APPLICATION_JSON)
                        .content(json(sampleRequest())))
                .andExpect(status().isForbidden());

        String adminToken = loginAdmin();
        long hospitalUserId = userId(hospitalToken);

        mockMvc.perform(get("/api/admin/verifications").param("type", "hospital")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email == '" + email + "')]").isNotEmpty());

        mockMvc.perform(post("/api/admin/verifications/{userId}/approve", hospitalUserId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // Same token as before: approval changes the account, not the session.
        mockMvc.perform(post("/api/requests")
                        .header("Authorization", "Bearer " + hospitalToken)
                        .contentType(APPLICATION_JSON)
                        .content(json(sampleRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RAISED"));

        // Approved registrations leave the queue.
        mockMvc.perform(get("/api/admin/verifications").param("type", "hospital")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email == '" + email + "')]").isEmpty());
    }

    @Test
    void approvingTwiceConflicts() throws Exception {
        String hospitalToken = registerHospital(uniqueEmail("hospital"), "Riverside Clinic", 12.97, 77.59);
        long hospitalUserId = userId(hospitalToken);
        String adminToken = loginAdmin();

        mockMvc.perform(post("/api/admin/verifications/{userId}/approve", hospitalUserId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/verifications/{userId}/approve", hospitalUserId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectedHospitalCannotLogInAgain() throws Exception {
        String email = uniqueEmail("hospital");
        String hospitalToken = registerHospital(email, "Unlicensed Clinic", 12.97, 77.59);

        mockMvc.perform(post("/api/admin/verifications/{userId}/reject", userId(hospitalToken))
                        .header("Authorization", "Bearer " + loginAdmin())
                        .contentType(APPLICATION_JSON)
                        .content("{\"reason\":\"License number could not be verified\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(false))
                .andExpect(jsonPath("$.status").value("DISABLED"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(json(new LoginRequest(email, "hospital-password"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void nonAdminsCannotReachTheVerificationQueue() throws Exception {
        String token = registerHospital(uniqueEmail("hospital"), "Snooping Hospital", 12.97, 77.59);

        mockMvc.perform(get("/api/admin/verifications").param("type", "hospital")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void verificationQueueRejectsAnUnknownType() throws Exception {
        mockMvc.perform(get("/api/admin/verifications").param("type", "clinic")
                        .header("Authorization", "Bearer " + loginAdmin()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void statsCountRequestsAndDonors() throws Exception {
        registerDonor(uniqueEmail("donor"), "Stats Donor", BloodGroup.O_NEG, 12.97, 77.59, 15);

        mockMvc.perform(get("/api/admin/stats")
                        .header("Authorization", "Bearer " + loginAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestsByStatus.RAISED").exists())
                .andExpect(jsonPath("$.donorsByBloodGroup['O-']").exists())
                .andExpect(jsonPath("$.fulfillmentRate").exists());
    }

    private static CreateRequestRequest sampleRequest() {
        return new CreateRequestRequest(
                BloodGroup.A_POS, 2, Urgency.HIGH,
                Instant.now().plus(6, ChronoUnit.HOURS), "Scheduled surgery");
    }
}

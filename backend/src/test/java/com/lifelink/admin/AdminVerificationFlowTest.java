package com.lifelink.admin;

import com.lifelink.auth.dto.LoginRequest;
import com.lifelink.auth.dto.RegisterHospitalRequest;
import com.lifelink.common.BloodGroup;
import com.lifelink.request.Urgency;
import com.lifelink.request.dto.CreateRequestRequest;
import com.lifelink.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the gate that admin verification puts on the request flow: a hospital
 * registers PENDING, cannot raise requests, and only can once an admin approves.
 */
class AdminVerificationFlowTest extends IntegrationTest {

    @Test
    void hospitalCanOnlyRaiseRequestsAfterAdminApproval() throws Exception {
        String email = "hospital-" + UUID.randomUUID() + "@example.com";
        String hospitalToken = registerHospital(email, "City General Hospital");

        // Pending hospitals are refused (spec 2.B.1).
        mockMvc.perform(post("/api/requests")
                        .header("Authorization", "Bearer " + hospitalToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isForbidden());

        String adminToken = loginAdmin();

        Long userId = pendingHospitalUserId(adminToken, email);

        mockMvc.perform(post("/api/admin/verifications/{userId}/approve", userId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // Same token as before: approval changes the account, not the session.
        mockMvc.perform(post("/api/requests")
                        .header("Authorization", "Bearer " + hospitalToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
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
        String email = "hospital-" + UUID.randomUUID() + "@example.com";
        registerHospital(email, "Riverside Clinic");
        String adminToken = loginAdmin();
        Long userId = pendingHospitalUserId(adminToken, email);

        mockMvc.perform(post("/api/admin/verifications/{userId}/approve", userId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/verifications/{userId}/approve", userId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectedHospitalCannotLogInAgain() throws Exception {
        String email = "hospital-" + UUID.randomUUID() + "@example.com";
        registerHospital(email, "Unlicensed Clinic");
        String adminToken = loginAdmin();
        Long userId = pendingHospitalUserId(adminToken, email);

        mockMvc.perform(post("/api/admin/verifications/{userId}/reject", userId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"reason\":\"License number could not be verified\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(false))
                .andExpect(jsonPath("$.status").value("DISABLED"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(email, "hospital-password"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void nonAdminsCannotReachTheVerificationQueue() throws Exception {
        String token = registerHospital("hospital-" + UUID.randomUUID() + "@example.com", "Snooping Hospital");

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

    private String registerHospital(String email, String name) throws Exception {
        var body = new RegisterHospitalRequest(
                email, "+91-99999-00000", "hospital-password", name,
                "LIC-" + UUID.randomUUID(), "12 Main Road, Bengaluru", 12.9716, 77.5946);
        String json = mockMvc.perform(post("/api/auth/register/hospital")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("accessToken").asText();
    }

    private String loginAdmin() throws Exception {
        String json = mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("admin@lifelink.test", "admin-test-password"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("accessToken").asText();
    }

    private Long pendingHospitalUserId(String adminToken, String email) throws Exception {
        String json = mockMvc.perform(get("/api/admin/verifications").param("type", "hospital")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (var node : objectMapper.readTree(json)) {
            if (email.equals(node.get("email").asText())) {
                return node.get("userId").asLong();
            }
        }
        throw new AssertionError(email + " is not in the pending verification queue: " + json);
    }

    private static CreateRequestRequest sampleRequest() {
        return new CreateRequestRequest(
                BloodGroup.A_POS, 2, Urgency.HIGH,
                Instant.now().plus(6, ChronoUnit.HOURS), "Scheduled surgery");
    }
}

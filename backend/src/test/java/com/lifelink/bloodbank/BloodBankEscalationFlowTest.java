package com.lifelink.bloodbank;

import com.fasterxml.jackson.databind.JsonNode;
import com.lifelink.bloodbank.dto.InventoryItemRequest;
import com.lifelink.bloodbank.dto.UpdateInventoryRequest;
import com.lifelink.common.BloodGroup;
import com.lifelink.request.RequestMaintenanceService;
import com.lifelink.request.Urgency;
import com.lifelink.request.dto.CreateRequestRequest;
import com.lifelink.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The blood bank half of the lifecycle: a request nobody confirms is escalated
 * by the maintenance job, offered to banks in range, then accepted and
 * fulfilled from stock.
 */
class BloodBankEscalationFlowTest extends IntegrationTest {

    private static final double HOSPITAL_LAT = 12.9716;
    private static final double HOSPITAL_LNG = 77.5946;

    @Autowired
    private RequestMaintenanceService requestMaintenanceService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void escalatedRequestIsOfferedToNearbyBanksThenFulfilledFromStock() throws Exception {
        String hospitalToken = verifiedHospital();
        String bankToken = verifiedBankWithStock(BloodGroup.A_POS, 10, HOSPITAL_LAT, HOSPITAL_LNG);

        long requestId = raiseRequest(hospitalToken, Urgency.CRITICAL, 3);

        // Still inside its window, so no bank is offered it yet.
        requestMaintenanceService.escalateOverdueRequests();
        assertThat(escalationOffer(bankToken, requestId)).isNull();

        // CRITICAL escalates after 2h; age the request past that.
        ageRequest(requestId, 3);
        assertThat(requestMaintenanceService.escalateOverdueRequests()).isPositive();

        JsonNode offer = escalationOffer(bankToken, requestId);
        assertThat(offer).isNotNull();
        assertThat(offer.get("bloodGroup").asText()).isEqualTo("A+");
        assertThat(offer.get("unitsInStock").asInt()).isEqualTo(10);
        assertThat(offer.get("distanceKm").asDouble()).isZero();

        mockMvc.perform(post("/api/escalations/{id}/accept", requestId)
                        .header("Authorization", "Bearer " + bankToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.acceptedBankName").value("Central Blood Bank"));

        mockMvc.perform(post("/api/escalations/{id}/fulfill", requestId)
                        .header("Authorization", "Bearer " + bankToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"units\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FULFILLED"));

        assertThat(stockOf(bankToken, "A+")).isEqualTo(7);

        // The hospital sees the whole trail, ending in the bank handover.
        String history = mockMvc.perform(get("/api/requests/{id}/history", requestId)
                        .header("Authorization", "Bearer " + hospitalToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode trail = objectMapper.readTree(history);
        assertThat(trail.get(trail.size() - 1).get("toStatus").asText()).isEqualTo("FULFILLED");
    }

    @Test
    void bankCannotFulfillARequestItDidNotAccept() throws Exception {
        String hospitalToken = verifiedHospital();
        String acceptingBank = verifiedBankWithStock(BloodGroup.O_NEG, 5, HOSPITAL_LAT, HOSPITAL_LNG);
        String otherBank = verifiedBankWithStock(BloodGroup.O_NEG, 5, HOSPITAL_LAT, HOSPITAL_LNG);

        long requestId = raiseRequest(hospitalToken, Urgency.CRITICAL, 1, BloodGroup.O_NEG);
        ageRequest(requestId, 3);
        requestMaintenanceService.escalateOverdueRequests();

        mockMvc.perform(post("/api/escalations/{id}/accept", requestId)
                        .header("Authorization", "Bearer " + acceptingBank))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/escalations/{id}/fulfill", requestId)
                        .header("Authorization", "Bearer " + otherBank)
                        .contentType(APPLICATION_JSON)
                        .content("{\"units\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aSecondBankCannotAcceptAnAlreadyAcceptedRequest() throws Exception {
        String hospitalToken = verifiedHospital();
        String firstBank = verifiedBankWithStock(BloodGroup.AB_POS, 5, HOSPITAL_LAT, HOSPITAL_LNG);
        String secondBank = verifiedBankWithStock(BloodGroup.AB_POS, 5, HOSPITAL_LAT, HOSPITAL_LNG);

        long requestId = raiseRequest(hospitalToken, Urgency.CRITICAL, 1, BloodGroup.AB_POS);
        ageRequest(requestId, 3);
        requestMaintenanceService.escalateOverdueRequests();

        mockMvc.perform(post("/api/escalations/{id}/accept", requestId)
                        .header("Authorization", "Bearer " + firstBank))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/escalations/{id}/accept", requestId)
                        .header("Authorization", "Bearer " + secondBank))
                .andExpect(status().isConflict());
    }

    @Test
    void fulfillingBeyondStockConflicts() throws Exception {
        String hospitalToken = verifiedHospital();
        String bankToken = verifiedBankWithStock(BloodGroup.B_POS, 1, HOSPITAL_LAT, HOSPITAL_LNG);

        long requestId = raiseRequest(hospitalToken, Urgency.CRITICAL, 4, BloodGroup.B_POS);
        ageRequest(requestId, 3);
        requestMaintenanceService.escalateOverdueRequests();

        mockMvc.perform(post("/api/escalations/{id}/accept", requestId)
                        .header("Authorization", "Bearer " + bankToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/escalations/{id}/fulfill", requestId)
                        .header("Authorization", "Bearer " + bankToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"units\":4}"))
                .andExpect(status().isConflict());

        // A failed fulfillment must not have drawn stock down.
        assertThat(stockOf(bankToken, "B+")).isEqualTo(1);
    }

    @Test
    void banksOutsideTheRadiusNeitherSeeNorCanAcceptTheRequest() throws Exception {
        String hospitalToken = verifiedHospital();
        // Roughly 300 km away, well beyond the 50 km escalation reach.
        String farBank = verifiedBankWithStock(BloodGroup.A_POS, 10, 15.6, 78.5);

        long requestId = raiseRequest(hospitalToken, Urgency.CRITICAL, 2);
        ageRequest(requestId, 3);
        requestMaintenanceService.escalateOverdueRequests();

        assertThat(escalationOffer(farBank, requestId)).isNull();

        mockMvc.perform(post("/api/escalations/{id}/accept", requestId)
                        .header("Authorization", "Bearer " + farBank))
                .andExpect(status().isForbidden());
    }

    @Test
    void unverifiedBankCannotTouchInventory() throws Exception {
        String bankToken = registerBloodBank(uniqueEmail("bank"), "Unverified Bank", HOSPITAL_LAT, HOSPITAL_LNG);

        mockMvc.perform(get("/api/bloodbanks/me/inventory")
                        .header("Authorization", "Bearer " + bankToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void inventoryStartsAtZeroForEveryGroupAndUpsertsInPlace() throws Exception {
        String bankToken = registerBloodBank(uniqueEmail("bank"), "Grid Bank", HOSPITAL_LAT, HOSPITAL_LNG);
        verifyAccount(bankToken);

        String body = mockMvc.perform(get("/api/bloodbanks/me/inventory")
                        .header("Authorization", "Bearer " + bankToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(body)).hasSize(BloodGroup.values().length);
        assertThat(stockOf(bankToken, "O-")).isZero();

        setStock(bankToken, BloodGroup.O_NEG, 4);
        assertThat(stockOf(bankToken, "O-")).isEqualTo(4);

        // Updating one group leaves the others alone.
        setStock(bankToken, BloodGroup.A_POS, 2);
        assertThat(stockOf(bankToken, "O-")).isEqualTo(4);
        assertThat(stockOf(bankToken, "A+")).isEqualTo(2);
    }

    @Test
    void pastDueRequestsExpire() throws Exception {
        String hospitalToken = verifiedHospital();
        long requestId = raiseRequest(hospitalToken, Urgency.NORMAL, 2);

        jdbcTemplate.update("UPDATE requests SET needed_by = now() - interval '1 hour' WHERE id = ?", requestId);
        assertThat(requestMaintenanceService.expirePastDueRequests()).isPositive();

        mockMvc.perform(get("/api/requests/{id}", requestId)
                        .header("Authorization", "Bearer " + hospitalToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXPIRED"));
    }

    /** @return the bank's offer for that request, or null when it is not in the queue */
    private JsonNode escalationOffer(String bankToken, long requestId) throws Exception {
        String body = mockMvc.perform(get("/api/bloodbanks/me/escalations")
                        .header("Authorization", "Bearer " + bankToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (JsonNode offer : objectMapper.readTree(body)) {
            if (offer.get("requestId").asLong() == requestId) {
                return offer;
            }
        }
        return null;
    }

    private int stockOf(String bankToken, String groupLabel) throws Exception {
        String body = mockMvc.perform(get("/api/bloodbanks/me/inventory")
                        .header("Authorization", "Bearer " + bankToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (JsonNode row : objectMapper.readTree(body)) {
            if (groupLabel.equals(row.get("bloodGroup").asText())) {
                return row.get("units").asInt();
            }
        }
        throw new AssertionError(groupLabel + " missing from inventory: " + body);
    }

    private void setStock(String bankToken, BloodGroup group, int units) throws Exception {
        mockMvc.perform(put("/api/bloodbanks/me/inventory")
                        .header("Authorization", "Bearer " + bankToken)
                        .contentType(APPLICATION_JSON)
                        .content(json(new UpdateInventoryRequest(
                                List.of(new InventoryItemRequest(group, units))))))
                .andExpect(status().isOk());
    }

    private String verifiedHospital() throws Exception {
        String token = registerHospital(
                uniqueEmail("hospital"), "City General Hospital", HOSPITAL_LAT, HOSPITAL_LNG);
        verifyAccount(token);
        return token;
    }

    private String verifiedBankWithStock(BloodGroup group, int units, double lat, double lng) throws Exception {
        String token = registerBloodBank(uniqueEmail("bank"), "Central Blood Bank", lat, lng);
        verifyAccount(token);
        setStock(token, group, units);
        return token;
    }

    private long raiseRequest(String hospitalToken, Urgency urgency, int units) throws Exception {
        return raiseRequest(hospitalToken, urgency, units, BloodGroup.A_POS);
    }

    private long raiseRequest(String hospitalToken, Urgency urgency, int units, BloodGroup group)
            throws Exception {
        String response = mockMvc.perform(post("/api/requests")
                        .header("Authorization", "Bearer " + hospitalToken)
                        .contentType(APPLICATION_JSON)
                        .content(json(new CreateRequestRequest(
                                group, units, urgency,
                                Instant.now().plus(12, ChronoUnit.HOURS), "Trauma case"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    /** Backdates creation so the urgency deadline has passed. */
    private void ageRequest(long requestId, int hours) {
        jdbcTemplate.update(
                "UPDATE requests SET created_at = now() - make_interval(hours => ?) WHERE id = ?",
                hours, requestId);
    }
}

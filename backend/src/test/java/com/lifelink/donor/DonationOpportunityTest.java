package com.lifelink.donor;

import com.fasterxml.jackson.databind.JsonNode;
import com.lifelink.common.BloodGroup;
import com.lifelink.request.Urgency;
import com.lifelink.request.dto.CreateRequestRequest;
import com.lifelink.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Donors can browse every open request they could serve and volunteer for one,
 * instead of only seeing what the matching engine pushed at them.
 */
class DonationOpportunityTest extends IntegrationTest {

    /** Each test works in its own patch of the globe; see LateAvailabilityMatchingTest. */
    private static final AtomicInteger LATITUDE_SEQUENCE = new AtomicInteger();
    private static final double LNG = 77.5946;

    private double lat;

    @BeforeEach
    void pickAnIsolatedLocation() {
        lat = -20.0 + LATITUDE_SEQUENCE.getAndIncrement() * 9.0;
    }

    @Test
    void donorSeesEveryOpenRequestTheirGroupCanServe() throws Exception {
        String hospitalToken = verifiedHospital();
        long oPositive = raiseRequest(hospitalToken, BloodGroup.O_POS, Urgency.NORMAL);
        long bPositive = raiseRequest(hospitalToken, BloodGroup.B_POS, Urgency.CRITICAL);
        long oNegative = raiseRequest(hospitalToken, BloodGroup.O_NEG, Urgency.HIGH);

        // An O+ donor can give to O+ and B+, but not to an O- patient.
        String donorToken = registerDonor(uniqueEmail("donor"), "Browser", BloodGroup.O_POS, lat, LNG, 20);

        assertThat(opportunityFor(donorToken, oPositive)).isNotNull();
        assertThat(opportunityFor(donorToken, bPositive)).isNotNull();
        assertThat(opportunityFor(donorToken, oNegative)).isNull();

        // Most urgent first.
        JsonNode all = opportunities(donorToken);
        assertThat(all.get(0).get("urgency").asText()).isEqualTo("CRITICAL");
    }

    @Test
    void browsingWorksBeforeTurningAvailabilityOn() throws Exception {
        String hospitalToken = verifiedHospital();
        long requestId = raiseRequest(hospitalToken, BloodGroup.A_POS, Urgency.HIGH);

        // Registered, never toggled available: browsing is how you decide to.
        String donorToken = registerDonor(uniqueEmail("donor"), "Undecided", BloodGroup.A_POS, lat, LNG, 20);

        assertThat(opportunityFor(donorToken, requestId)).isNotNull();
    }

    @Test
    void volunteeringMovesTheRequestToMatched() throws Exception {
        String hospitalToken = verifiedHospital();
        long requestId = raiseRequest(hospitalToken, BloodGroup.AB_POS, Urgency.HIGH);
        String donorToken = registerDonor(uniqueEmail("donor"), "Willing", BloodGroup.AB_POS, lat, LNG, 20);

        mockMvc.perform(post("/api/donors/me/opportunities/{id}/accept", requestId)
                        .header("Authorization", "Bearer " + donorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.requestStatus").value("MATCHED"));

        // The hospital sees them among its responders.
        mockMvc.perform(get("/api/requests/{id}/matches", requestId)
                        .header("Authorization", "Bearer " + hospitalToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ACCEPTED"))
                .andExpect(jsonPath("$[0].donorName").value("Willing"));
    }

    @Test
    void aDonorCannotCommitToASecondRequest() throws Exception {
        String hospitalToken = verifiedHospital();
        long first = raiseRequest(hospitalToken, BloodGroup.B_NEG, Urgency.HIGH);
        long second = raiseRequest(hospitalToken, BloodGroup.B_NEG, Urgency.HIGH);
        String donorToken = registerDonor(uniqueEmail("donor"), "Committed", BloodGroup.B_NEG, lat, LNG, 20);

        mockMvc.perform(post("/api/donors/me/opportunities/{id}/accept", first)
                        .header("Authorization", "Bearer " + donorToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/donors/me/opportunities/{id}/accept", second)
                        .header("Authorization", "Bearer " + donorToken))
                .andExpect(status().isConflict());
    }

    @Test
    void requestsOutsideTheTravelRadiusAreNotOffered() throws Exception {
        String hospitalToken = verifiedHospital();
        long requestId = raiseRequest(hospitalToken, BloodGroup.O_POS, Urgency.HIGH);
        // ~330 km away, willing to travel 5 km.
        String donorToken = registerDonor(uniqueEmail("donor"), "Far", BloodGroup.O_POS, lat + 3.0, LNG, 5);

        assertThat(opportunityFor(donorToken, requestId)).isNull();

        mockMvc.perform(post("/api/donors/me/opportunities/{id}/accept", requestId)
                        .header("Authorization", "Bearer " + donorToken))
                .andExpect(status().isConflict());
    }

    @Test
    void anIncompatibleGroupCannotVolunteer() throws Exception {
        String hospitalToken = verifiedHospital();
        long requestId = raiseRequest(hospitalToken, BloodGroup.O_NEG, Urgency.HIGH);
        String donorToken = registerDonor(uniqueEmail("donor"), "Wrong Group", BloodGroup.A_POS, lat, LNG, 20);

        mockMvc.perform(post("/api/donors/me/opportunities/{id}/accept", requestId)
                        .header("Authorization", "Bearer " + donorToken))
                .andExpect(status().isConflict());
    }

    private JsonNode opportunities(String donorToken) throws Exception {
        String body = mockMvc.perform(get("/api/donors/me/opportunities")
                        .header("Authorization", "Bearer " + donorToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private JsonNode opportunityFor(String donorToken, long requestId) throws Exception {
        for (JsonNode opportunity : opportunities(donorToken)) {
            if (opportunity.get("requestId").asLong() == requestId) {
                return opportunity;
            }
        }
        return null;
    }

    private String verifiedHospital() throws Exception {
        String token = registerHospital(uniqueEmail("hospital"), "City General Hospital", lat, LNG);
        verifyAccount(token);
        return token;
    }

    private long raiseRequest(String hospitalToken, BloodGroup group, Urgency urgency) throws Exception {
        String response = mockMvc.perform(post("/api/requests")
                        .header("Authorization", "Bearer " + hospitalToken)
                        .contentType(APPLICATION_JSON)
                        .content(json(new CreateRequestRequest(
                                group, 1, urgency,
                                Instant.now().plus(12, ChronoUnit.HOURS), "Surgery"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }
}

package com.lifelink.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.lifelink.common.BloodGroup;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A donor who becomes available after a request was raised still hears about it.
 *
 * <p>Matching runs when a request is created, so without this a donor who signs
 * up or switches availability on a minute later is invisible to a request that
 * is still open and needs exactly their blood group.
 */
class LateAvailabilityMatchingTest extends IntegrationTest {

    /**
     * Test classes share one database, so each test works in its own patch of the
     * globe. Otherwise a donor here could be matched to another test's open
     * request and the assertions would depend on execution order.
     */
    private static final AtomicInteger LATITUDE_SEQUENCE = new AtomicInteger();
    private static final double LNG = 77.5946;

    private double lat;

    @BeforeEach
    void pickAnIsolatedLocation() {
        lat = -60.0 + LATITUDE_SEQUENCE.getAndIncrement() * 12.0;
    }

    @Test
    void donorWhoBecomesAvailableAfterTheRequestStillGetsMatched() throws Exception {
        String hospitalToken = verifiedHospital();

        // Request first, donor second — the order that produced no matches.
        long requestId = raiseRequest(hospitalToken, BloodGroup.O_POS);
        String donorToken = registerDonor(
                uniqueEmail("donor"), "Late Donor", BloodGroup.O_POS, lat, LNG, 15);

        // Registered but not yet available: still nothing.
        assertThat(matchFor(donorToken, requestId)).isNull();

        setAvailability(donorToken, true);

        JsonNode match = matchFor(donorToken, requestId);
        assertThat(match).isNotNull();
        assertThat(match.get("matchStatus").asText()).isEqualTo("NOTIFIED");
        assertThat(match.get("bloodGroup").asText()).isEqualTo("O+");
    }

    @Test
    void theMostUrgentReachableRequestWins() throws Exception {
        String hospitalToken = verifiedHospital();
        raiseRequest(hospitalToken, BloodGroup.O_POS, Urgency.NORMAL);
        long critical = raiseRequest(hospitalToken, BloodGroup.O_POS, Urgency.CRITICAL);

        String donorToken = registerDonor(
                uniqueEmail("donor"), "Triage Donor", BloodGroup.O_NEG, lat, LNG, 15);
        setAvailability(donorToken, true);

        assertThat(matchFor(donorToken, critical)).isNotNull();
    }

    @Test
    void aDonorIsNeverTiedToTwoOpenRequestsAtOnce() throws Exception {
        String hospitalToken = verifiedHospital();
        long first = raiseRequest(hospitalToken, BloodGroup.B_POS);

        String donorToken = registerDonor(
                uniqueEmail("donor"), "Single Donor", BloodGroup.B_POS, lat, LNG, 15);
        setAvailability(donorToken, true);
        assertThat(matchFor(donorToken, first)).isNotNull();

        // Toggling again must not pile a second open request onto them.
        long second = raiseRequest(hospitalToken, BloodGroup.B_POS);
        setAvailability(donorToken, false);
        setAvailability(donorToken, true);
        assertThat(matchFor(donorToken, second)).isNull();
    }

    @Test
    void requestsBeyondTheDonorsOwnRadiusAreIgnored() throws Exception {
        String hospitalToken = verifiedHospital();
        long requestId = raiseRequest(hospitalToken, BloodGroup.AB_POS);

        // ~300 km away, willing to travel 5 km.
        String donorToken = registerDonor(
                uniqueEmail("donor"), "Far Donor", BloodGroup.AB_POS, lat + 3.0, LNG, 5);
        setAvailability(donorToken, true);

        assertThat(matchFor(donorToken, requestId)).isNull();
    }

    @Test
    void anIncompatibleGroupIsNotMatched() throws Exception {
        String hospitalToken = verifiedHospital();
        // O- recipients can only take O-, so an A+ donor is no use.
        long requestId = raiseRequest(hospitalToken, BloodGroup.O_NEG);

        String donorToken = registerDonor(
                uniqueEmail("donor"), "Wrong Group Donor", BloodGroup.A_POS, lat, LNG, 15);
        setAvailability(donorToken, true);

        assertThat(matchFor(donorToken, requestId)).isNull();
    }

    private JsonNode matchFor(String donorToken, long requestId) throws Exception {
        String body = mockMvc.perform(get("/api/donors/me/matches")
                        .header("Authorization", "Bearer " + donorToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (JsonNode match : objectMapper.readTree(body)) {
            if (match.get("requestId").asLong() == requestId) {
                return match;
            }
        }
        return null;
    }

    private void setAvailability(String donorToken, boolean available) throws Exception {
        mockMvc.perform(patch("/api/donors/me/availability")
                        .header("Authorization", "Bearer " + donorToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"available\":" + available + "}"))
                .andExpect(status().isOk());
    }

    private String verifiedHospital() throws Exception {
        String token = registerHospital(uniqueEmail("hospital"), "City General Hospital", lat, LNG);
        verifyAccount(token);
        return token;
    }

    private long raiseRequest(String hospitalToken, BloodGroup group) throws Exception {
        return raiseRequest(hospitalToken, group, Urgency.HIGH);
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

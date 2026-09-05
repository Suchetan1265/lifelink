package com.lifelink.donor;

import com.fasterxml.jackson.databind.JsonNode;
import com.lifelink.common.BloodGroup;
import com.lifelink.request.Urgency;
import com.lifelink.request.dto.CreateRequestRequest;
import com.lifelink.request.dto.FulfillRequest;
import com.lifelink.support.RedisIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The donor journey end to end (spec §2.A): availability, being matched,
 * accepting, being confirmed, and the 90-day cooldown that follows a donation.
 *
 * <p>Runs with Redis so matching goes through the GEO index rather than the
 * SQL fallback.
 */
class DonorJourneyTest extends RedisIntegrationTest {

    /**
     * Each test gets its own patch of the globe. Donors are matched to open
     * requests the moment they become available, so without this a donor here
     * could be claimed by another test's request and never see its own.
     */
    private static final AtomicInteger LATITUDE_SEQUENCE = new AtomicInteger();
    private static final double LNG = 77.5946;

    private double lat;

    @BeforeEach
    void pickAnIsolatedLocation() {
        lat = 30.0 + LATITUDE_SEQUENCE.getAndIncrement() * 8.0;
    }

    @Autowired
    private StringRedisTemplate redis;

    @Test
    void donorIsMatchedConfirmedAndThenLockedOutForNinetyDays() throws Exception {
        String donorToken = registerDonor(uniqueEmail("donor"), "Ada Donor", BloodGroup.O_NEG, lat, LNG, 25);
        long donorId = userId(donorToken);
        setAvailability(donorToken, true);

        mockMvc.perform(get("/api/donors/me/eligibility")
                        .header("Authorization", "Bearer " + donorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(true))
                .andExpect(jsonPath("$.daysRemaining").value(0));

        String hospitalToken = verifiedHospital();
        long requestId = raiseRequest(hospitalToken, BloodGroup.O_NEG);

        // The donor sees the match and accepts, which moves the request to MATCHED.
        JsonNode match = matchFor(donorToken, requestId);
        assertThat(match).isNotNull();
        assertThat(match.get("matchStatus").asText()).isEqualTo("NOTIFIED");

        long matchId = match.get("matchId").asLong();
        mockMvc.perform(post("/api/matches/{id}/accept", matchId)
                        .header("Authorization", "Bearer " + donorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.requestStatus").value("MATCHED"));

        // The hospital picks them, then records the donation.
        mockMvc.perform(post("/api/requests/{requestId}/matches/{matchId}/confirm", requestId, matchId)
                        .header("Authorization", "Bearer " + hospitalToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(post("/api/requests/{id}/fulfill", requestId)
                        .header("Authorization", "Bearer " + hospitalToken)
                        .contentType(APPLICATION_JSON)
                        .content(json(new FulfillRequest(donorId, null, 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FULFILLED"));

        // Cooldown starts, and the donor drops out of the matching index.
        mockMvc.perform(get("/api/donors/me/eligibility")
                        .header("Authorization", "Bearer " + donorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(false))
                .andExpect(jsonPath("$.nextEligibleDate").value(LocalDate.now().plusDays(90).toString()));

        assertThat(redis.opsForZSet().score(
                com.lifelink.redis.DonorGeoService.keyFor(BloodGroup.O_NEG), String.valueOf(donorId)))
                .isNull();

        mockMvc.perform(get("/api/donors/me/donations")
                        .header("Authorization", "Bearer " + donorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].units").value(1));

        // A fresh request must not reach a donor still on cooldown.
        long secondRequest = raiseRequest(hospitalToken, BloodGroup.O_NEG);
        assertThat(matchFor(donorToken, secondRequest)).isNull();
    }

    @Test
    void decliningLeavesTheRequestOpenForOtherDonors() throws Exception {
        String donorToken = registerDonor(uniqueEmail("donor"), "Busy Donor", BloodGroup.A_NEG, lat, LNG, 25);
        setAvailability(donorToken, true);

        String hospitalToken = verifiedHospital();
        long requestId = raiseRequest(hospitalToken, BloodGroup.A_NEG);

        long matchId = matchFor(donorToken, requestId).get("matchId").asLong();
        mockMvc.perform(post("/api/matches/{id}/decline", matchId)
                        .header("Authorization", "Bearer " + donorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchStatus").value("DECLINED"));

        mockMvc.perform(get("/api/requests/{id}", requestId)
                        .header("Authorization", "Bearer " + hospitalToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RAISED"));
    }

    @Test
    void respondingTwiceToTheSameMatchConflicts() throws Exception {
        String donorToken = registerDonor(uniqueEmail("donor"), "Twice Donor", BloodGroup.B_NEG, lat, LNG, 25);
        setAvailability(donorToken, true);

        String hospitalToken = verifiedHospital();
        long requestId = raiseRequest(hospitalToken, BloodGroup.B_NEG);
        long matchId = matchFor(donorToken, requestId).get("matchId").asLong();

        mockMvc.perform(post("/api/matches/{id}/accept", matchId)
                        .header("Authorization", "Bearer " + donorToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/matches/{id}/decline", matchId)
                        .header("Authorization", "Bearer " + donorToken))
                .andExpect(status().isConflict());
    }

    @Test
    void hospitalCannotConfirmADonorWhoNeverAccepted() throws Exception {
        String donorToken = registerDonor(uniqueEmail("donor"), "Silent Donor", BloodGroup.AB_NEG, lat, LNG, 25);
        setAvailability(donorToken, true);

        String hospitalToken = verifiedHospital();
        long requestId = raiseRequest(hospitalToken, BloodGroup.AB_NEG);
        long matchId = matchFor(donorToken, requestId).get("matchId").asLong();

        mockMvc.perform(post("/api/requests/{requestId}/matches/{matchId}/confirm", requestId, matchId)
                        .header("Authorization", "Bearer " + hospitalToken))
                .andExpect(status().isConflict());
    }

    @Test
    void anotherDonorCannotRespondToSomeoneElsesMatch() throws Exception {
        String donorToken = registerDonor(uniqueEmail("donor"), "Owner Donor", BloodGroup.AB_POS, lat, LNG, 25);
        setAvailability(donorToken, true);
        String otherToken = registerDonor(uniqueEmail("donor"), "Other Donor", BloodGroup.O_POS, lat, LNG, 25);

        String hospitalToken = verifiedHospital();
        long requestId = raiseRequest(hospitalToken, BloodGroup.AB_POS);
        long matchId = matchFor(donorToken, requestId).get("matchId").asLong();

        mockMvc.perform(post("/api/matches/{id}/accept", matchId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void unavailableDonorsAreNotMatched() throws Exception {
        // Registered but never toggled available.
        String donorToken = registerDonor(uniqueEmail("donor"), "Offline Donor", BloodGroup.O_POS, lat, LNG, 25);

        String hospitalToken = verifiedHospital();
        long requestId = raiseRequest(hospitalToken, BloodGroup.O_POS);

        assertThat(matchFor(donorToken, requestId)).isNull();
    }

    /** @return the donor's match for that request, or null when they were not matched */
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
        String response = mockMvc.perform(post("/api/requests")
                        .header("Authorization", "Bearer " + hospitalToken)
                        .contentType(APPLICATION_JSON)
                        .content(json(new CreateRequestRequest(
                                group, 1, Urgency.HIGH,
                                Instant.now().plus(10, ChronoUnit.HOURS), "Surgery"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }
}

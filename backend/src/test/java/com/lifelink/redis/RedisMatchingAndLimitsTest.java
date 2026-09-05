package com.lifelink.redis;

import com.fasterxml.jackson.databind.JsonNode;
import com.lifelink.common.BloodGroup;
import com.lifelink.request.Urgency;
import com.lifelink.request.dto.CreateRequestRequest;
import com.lifelink.support.RedisIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Redis-backed paths from spec §7, exercised against a real server: the
 * donor GEO sets that drive matching, and the per-hospital rate limit.
 */
class RedisMatchingAndLimitsTest extends RedisIntegrationTest {

    private static final double HOSPITAL_LAT = 12.9716;
    private static final double HOSPITAL_LNG = 77.5946;

    @Autowired
    private StringRedisTemplate redis;

    @Test
    void availabilityTogglesMembershipOfTheGeoSet() throws Exception {
        String donorToken = registerDonor(
                uniqueEmail("donor"), "Ada Donor", BloodGroup.O_NEG, HOSPITAL_LAT, HOSPITAL_LNG, 20);
        String member = String.valueOf(userId(donorToken));
        String key = DonorGeoService.keyFor(BloodGroup.O_NEG);

        // Donors register unavailable, so nothing is indexed yet.
        assertThat(redis.opsForZSet().score(key, member)).isNull();

        setAvailability(donorToken, true);
        assertThat(redis.opsForZSet().score(key, member)).isNotNull();

        setAvailability(donorToken, false);
        assertThat(redis.opsForZSet().score(key, member)).isNull();
    }

    @Test
    void nearbyAvailableDonorIsMatchedThroughRedis() throws Exception {
        String donorToken = registerDonor(
                uniqueEmail("donor"), "Nearby Donor", BloodGroup.O_NEG, HOSPITAL_LAT, HOSPITAL_LNG, 20);
        setAvailability(donorToken, true);

        String hospitalToken = verifiedHospital();
        // O- donors are compatible with an A+ request.
        raiseRequest(hospitalToken, BloodGroup.A_POS);

        String matches = mockMvc.perform(get("/api/donors/me/matches")
                        .header("Authorization", "Bearer " + donorToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(matches)).isNotEmpty();
    }

    @Test
    void donorBeyondTheirOwnTravelRadiusIsNotMatched() throws Exception {
        // Indexed in Redis, but only willing to travel 5 km from ~300 km away.
        String donorToken = registerDonor(
                uniqueEmail("donor"), "Distant Donor", BloodGroup.O_NEG, 15.6, 78.5, 5);
        setAvailability(donorToken, true);

        String hospitalToken = verifiedHospital();
        raiseRequest(hospitalToken, BloodGroup.O_NEG);

        String matches = mockMvc.perform(get("/api/donors/me/matches")
                        .header("Authorization", "Bearer " + donorToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(matches)).isEmpty();
    }

    @Test
    void hospitalIsRateLimitedAfterTenRequestsInAnHour() throws Exception {
        String hospitalToken = verifiedHospital();

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/requests")
                            .header("Authorization", "Bearer " + hospitalToken)
                            .contentType(APPLICATION_JSON)
                            .content(json(request(BloodGroup.A_POS))))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(post("/api/requests")
                        .header("Authorization", "Bearer " + hospitalToken)
                        .contentType(APPLICATION_JSON)
                        .content(json(request(BloodGroup.A_POS))))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void adminStatsAreServedFromTheStatsAdminKey() throws Exception {
        String adminToken = loginAdmin();
        redis.delete("stats:admin");

        mockMvc.perform(get("/api/admin/stats").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests").exists());

        assertThat(redis.hasKey("stats:admin")).isTrue();

        // The second call is served from Redis and must deserialize back to the record.
        mockMvc.perform(get("/api/admin/stats").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestsByStatus.RAISED").exists());
    }

    @Test
    void donorProfileIsCachedUnderItsOwnKey() throws Exception {
        String donorToken = registerDonor(
                uniqueEmail("donor"), "Cached Donor", BloodGroup.B_POS, HOSPITAL_LAT, HOSPITAL_LNG, 10);
        String cacheKey = "donor:" + userId(donorToken) + ":profile";
        redis.delete(cacheKey);

        mockMvc.perform(get("/api/donors/me").header("Authorization", "Bearer " + donorToken))
                .andExpect(status().isOk());
        assertThat(redis.hasKey(cacheKey)).isTrue();

        // A write refreshes rather than staling the cached copy.
        JsonNode updated = objectMapper.readTree(setAvailability(donorToken, true));
        assertThat(updated.get("available").asBoolean()).isTrue();

        mockMvc.perform(get("/api/donors/me").header("Authorization", "Bearer " + donorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }

    private String setAvailability(String donorToken, boolean available) throws Exception {
        return mockMvc.perform(patch("/api/donors/me/availability")
                        .header("Authorization", "Bearer " + donorToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"available\":" + available + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String verifiedHospital() throws Exception {
        String token = registerHospital(
                uniqueEmail("hospital"), "City General Hospital", HOSPITAL_LAT, HOSPITAL_LNG);
        verifyAccount(token);
        return token;
    }

    private void raiseRequest(String hospitalToken, BloodGroup group) throws Exception {
        mockMvc.perform(post("/api/requests")
                        .header("Authorization", "Bearer " + hospitalToken)
                        .contentType(APPLICATION_JSON)
                        .content(json(request(group))))
                .andExpect(status().isCreated());
    }

    private static CreateRequestRequest request(BloodGroup group) {
        return new CreateRequestRequest(
                group, 2, Urgency.HIGH, Instant.now().plus(8, ChronoUnit.HOURS), "Surgery");
    }
}

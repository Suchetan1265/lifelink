package com.lifelink.dev;

import com.lifelink.bloodbank.BloodBankRepository;
import com.lifelink.donor.DonorRepository;
import com.lifelink.hospital.HospitalRepository;
import com.lifelink.support.EmbeddedPostgresServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The demo dataset has to load, since it is the first thing a new checkout runs.
 *
 * <p>Deliberately not an {@code IntegrationTest}: the seed only runs into an
 * empty schema and this asserts on absolute counts, so it needs a database the
 * other test classes have not written to.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "seed"})
class SeedDataTest {

    @DynamicPropertySource
    static void ownDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> EmbeddedPostgresServer.freshDatabase("lifelink_seed_test"));
        registry.add("spring.datasource.username", () -> EmbeddedPostgresServer.USERNAME);
        registry.add("spring.datasource.password", () -> EmbeddedPostgresServer.PASSWORD);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DonorRepository donorRepository;

    @Autowired
    private HospitalRepository hospitalRepository;

    @Autowired
    private BloodBankRepository bloodBankRepository;

    @Test
    void seedProfileLoadsAWorkingDemoDataset() throws Exception {
        assertThat(hospitalRepository.count()).isEqualTo(1);
        assertThat(bloodBankRepository.count()).isEqualTo(1);
        assertThat(donorRepository.count()).isEqualTo(12);

        // One donor is unavailable and one is still on cooldown, so neither is matchable.
        assertThat(donorRepository.findMatchable(LocalDate.now())).hasSize(10);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"email\":\"hospital@lifelink.local\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());
    }
}

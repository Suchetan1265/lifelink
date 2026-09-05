package com.lifelink.dev;

import com.lifelink.bloodbank.BloodBankRepository;
import com.lifelink.donor.DonorRepository;
import com.lifelink.hospital.HospitalRepository;
import com.lifelink.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The demo dataset has to actually load, since it is the first thing a new checkout runs. */
@ActiveProfiles({"test", "seed"})
class SeedDataTest extends IntegrationTest {

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

        // The seeded accounts are usable.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"email\":\"hospital@lifelink.local\",\"password\":\"password123\"}"))
                .andExpect(status().isOk());
    }
}

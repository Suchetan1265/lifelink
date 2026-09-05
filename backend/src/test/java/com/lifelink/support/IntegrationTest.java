package com.lifelink.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifelink.auth.dto.LoginRequest;
import com.lifelink.auth.dto.RegisterBloodBankRequest;
import com.lifelink.auth.dto.RegisterDonorRequest;
import com.lifelink.auth.dto.RegisterHospitalRequest;
import com.lifelink.common.BloodGroup;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for tests that boot the app against a real PostgreSQL, so the
 * Flyway migrations and native SQL run exactly as they do in production.
 *
 * <p>The server is embedded rather than containerised because the dev machine
 * has no Docker; it starts once per JVM and is shared by every test class.
 * Accounts are created through the real HTTP endpoints, with unique emails so
 * classes stay independent without truncating between tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {

    /** Must match {@code app.admin} in application-test.yml. */
    protected static final String ADMIN_EMAIL = "admin@lifelink.test";
    protected static final String ADMIN_PASSWORD = "admin-test-password";

    private static EmbeddedPostgres postgres;

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        int port = embeddedPostgres().getPort();
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:" + port + "/postgres");
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }

    private static synchronized EmbeddedPostgres embeddedPostgres() {
        if (postgres == null) {
            try {
                postgres = EmbeddedPostgres.builder().start();
            } catch (IOException e) {
                throw new UncheckedIOException("Could not start the embedded PostgreSQL server", e);
            }
        }
        return postgres;
    }

    protected static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    /** @return the new account's access token */
    protected String registerHospital(String email, String name, double lat, double lng) throws Exception {
        return register("/api/auth/register/hospital", new RegisterHospitalRequest(
                email, "+91-99999-00000", "hospital-password", name,
                "LIC-" + UUID.randomUUID(), "12 Main Road, Bengaluru", lat, lng));
    }

    /** @return the new account's access token */
    protected String registerBloodBank(String email, String name, double lat, double lng) throws Exception {
        return register("/api/auth/register/bloodbank", new RegisterBloodBankRequest(
                email, "+91-99999-11111", "bloodbank-password", name,
                "44 Bank Street, Bengaluru", lat, lng));
    }

    /** @return the new account's access token */
    protected String registerDonor(String email, String fullName, BloodGroup group,
            double lat, double lng, int radiusKm) throws Exception {
        return register("/api/auth/register/donor", new RegisterDonorRequest(
                email, "+91-99999-22222", "donor-password", fullName, group,
                lat, lng, "Bengaluru", radiusKm));
    }

    private String register(String path, Object body) throws Exception {
        String response = mockMvc.perform(post(path)
                        .contentType(APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    protected String login(String email, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(json(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    protected String loginAdmin() throws Exception {
        return login(ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    protected long userId(String accessToken) throws Exception {
        String response = mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    /** Runs the admin approval a hospital or blood bank needs before it can act. */
    protected void verifyAccount(String accountToken) throws Exception {
        mockMvc.perform(post("/api/admin/verifications/{userId}/approve", userId(accountToken))
                        .header("Authorization", "Bearer " + loginAdmin()))
                .andExpect(status().isOk());
    }
}

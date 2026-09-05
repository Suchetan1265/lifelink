package com.lifelink.auth;

import com.lifelink.auth.dto.ResetPasswordRequest;
import com.lifelink.common.BloodGroup;
import com.lifelink.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Resetting a forgotten password. The link is single-use, time-limited, and
 * requesting one never reveals whether the address has an account.
 */
class PasswordResetTest extends IntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void aResetLinkLetsSomeoneChooseANewPassword() throws Exception {
        String email = uniqueEmail("donor");
        registerDonor(email, "Forgetful Donor", BloodGroup.O_POS, 12.97, 77.59, 10);

        requestReset(email);
        reset(emailedToken(email), "a-brand-new-password").andExpect(status().isNoContent());

        // The new password works.
        login(email, "a-brand-new-password");

        // The old one does not.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"donor-password\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aLinkWorksOnlyOnce() throws Exception {
        String email = uniqueEmail("donor");
        registerDonor(email, "Once Donor", BloodGroup.A_POS, 12.97, 77.59, 10);
        requestReset(email);
        String token = emailedToken(email);

        reset(token, "first-new-password").andExpect(status().isNoContent());
        reset(token, "second-new-password").andExpect(status().isBadRequest());
    }

    @Test
    void anExpiredLinkIsRefused() throws Exception {
        String email = uniqueEmail("donor");
        registerDonor(email, "Late Donor", BloodGroup.B_POS, 12.97, 77.59, 10);
        requestReset(email);
        String token = emailedToken(email);

        jdbcTemplate.update(
                "UPDATE password_reset_tokens SET expires_at = now() - interval '1 minute' "
                        + "WHERE user_id = (SELECT id FROM users WHERE email = ?)", email);

        reset(token, "too-late-password").andExpect(status().isBadRequest());
    }

    @Test
    void askingAgainInvalidatesTheEarlierLink() throws Exception {
        String email = uniqueEmail("donor");
        registerDonor(email, "Twice Donor", BloodGroup.AB_POS, 12.97, 77.59, 10);

        requestReset(email);
        String first = emailedToken(email);
        requestReset(email);
        String second = emailedToken(email);

        assertThat(second).isNotEqualTo(first);
        reset(first, "should-not-work").andExpect(status().isBadRequest());
        reset(second, "should-work-fine").andExpect(status().isNoContent());
    }

    @Test
    void resettingEndsExistingSessions() throws Exception {
        String email = uniqueEmail("donor");
        registerDonor(email, "Session Donor", BloodGroup.O_NEG, 12.97, 77.59, 10);

        String refreshToken = refreshTokenFor(email, "donor-password");
        requestReset(email);
        reset(emailedToken(email), "replaced-password-now").andExpect(status().isNoContent());

        // Whoever prompted the reset may have held the old session.
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anUnknownAddressIsAcceptedWithoutRevealingAnything() throws Exception {
        // The same 202 a real address gets: this must not confirm who has an account.
        requestReset("definitely-nobody-" + System.nanoTime() + "@example.com");
    }

    @Test
    void aGarbageTokenIsRefused() throws Exception {
        reset("not-a-real-token", "whatever-password").andExpect(status().isBadRequest());
    }

    private void requestReset(String email) throws Exception {
        mockMvc.perform(post("/api/auth/password/forgot")
                        .contentType(APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isAccepted());
    }

    private ResultActions reset(String token, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/password/reset")
                .contentType(APPLICATION_JSON)
                .content(json(new ResetPasswordRequest(token, password))));
    }

    /**
     * The token only ever exists in the message sent to the user — the database
     * keeps a hash — so the test reads it back out of that message.
     */
    private String emailedToken(String email) {
        String body = jdbcTemplate.queryForObject(
                "SELECT n.body FROM notifications n JOIN users u ON u.id = n.user_id "
                        + "WHERE u.email = ? AND n.type = 'PASSWORD_RESET' "
                        + "ORDER BY n.id DESC LIMIT 1",
                String.class, email);
        int marker = body.indexOf("token=");
        assertThat(marker).as("reset link in the notification").isNotNegative();
        return body.substring(marker + "token=".length()).split("\\s")[0];
    }

    private String refreshTokenFor(String email, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("refreshToken").asText();
    }
}

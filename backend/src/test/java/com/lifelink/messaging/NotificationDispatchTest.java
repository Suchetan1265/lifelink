package com.lifelink.messaging;

import com.lifelink.common.BloodGroup;
import com.lifelink.notification.NotificationService;
import com.lifelink.notification.NotificationType;
import com.lifelink.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Notifications are queued only once the work that produced them is committed,
 * so a failed operation cannot email someone about something that did not
 * happen.
 */
class NotificationDispatchTest extends IntegrationTest {

    @MockitoSpyBean
    private NotificationPublisher publisher;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void aCommittedNotificationIsQueued() throws Exception {
        long donorId = userId(registerDonor(
                uniqueEmail("donor"), "Queued Donor", BloodGroup.A_POS, 12.97, 77.59, 10));

        transactionTemplate.executeWithoutResult(status ->
                notificationService.notify(donorId, NotificationType.MATCH_FOUND, "Nearby request", "Body"));

        verify(publisher).publish(argThat(message ->
                message.userId().equals(donorId)
                        && message.type() == NotificationType.MATCH_FOUND
                        && "Nearby request".equals(message.title())));
    }

    @Test
    void aRolledBackNotificationIsNeverQueued() throws Exception {
        long donorId = userId(registerDonor(
                uniqueEmail("donor"), "Rolled Back Donor", BloodGroup.A_POS, 12.97, 77.59, 10));

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            notificationService.notify(donorId, NotificationType.MATCH_FOUND, "Never sent", "Body");
            throw new IllegalStateException("the surrounding work failed");
        })).isInstanceOf(IllegalStateException.class);

        verify(publisher, never()).publish(any());
    }
}

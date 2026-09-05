package com.lifelink.request;

import com.lifelink.common.ConflictException;
import com.lifelink.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Single choke point for request status changes: validates the transition
 * against {@link RequestEvent}, stamps timestamps, and writes the
 * request_status_history audit row (spec §3).
 */
@Service
@RequiredArgsConstructor
public class RequestLifecycleService {

    private final RequestStatusHistoryRepository historyRepository;
    private final UserRepository userRepository;

    /**
     * @param changedByUserId null for system-triggered events (Quartz jobs)
     * @throws ConflictException if the event is not allowed from the current status
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void fire(Request request, RequestEvent event, Long changedByUserId, String reason) {
        RequestStatus from = request.getStatus();
        if (!event.canFireFrom(from)) {
            throw new ConflictException(
                    "Cannot apply " + event + " to request " + request.getId() + " in status " + from);
        }
        RequestStatus to = event.target();
        request.setStatus(to);
        if (to == RequestStatus.ESCALATED) {
            request.setEscalatedAt(Instant.now());
        }
        if (to.isTerminal()) {
            request.setClosedAt(Instant.now());
        }

        RequestStatusHistory history = new RequestStatusHistory();
        history.setRequest(request);
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setChangedBy(changedByUserId == null ? null : userRepository.getReferenceById(changedByUserId));
        history.setReason(reason);
        historyRepository.save(history);
    }

    /** Writes the initial null → RAISED audit row when a request is created. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordRaised(Request request, Long changedByUserId) {
        RequestStatusHistory history = new RequestStatusHistory();
        history.setRequest(request);
        history.setToStatus(RequestStatus.RAISED);
        history.setChangedBy(changedByUserId == null ? null : userRepository.getReferenceById(changedByUserId));
        historyRepository.save(history);
    }
}

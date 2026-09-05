package com.lifelink.request;

import com.lifelink.common.PageResponse;
import com.lifelink.request.dto.CancelRequest;
import com.lifelink.request.dto.CreateRequestRequest;
import com.lifelink.request.dto.FulfillRequest;
import com.lifelink.request.dto.RequestMatchResponse;
import com.lifelink.request.dto.RequestResponse;
import com.lifelink.request.dto.StatusHistoryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/requests")
@PreAuthorize("hasRole('HOSPITAL')")
@RequiredArgsConstructor
public class RequestController {

    private final RequestService requestService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RequestResponse create(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CreateRequestRequest request) {
        return requestService.create(userId, request);
    }

    @GetMapping
    public PageResponse<RequestResponse> list(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return requestService.list(userId, status, page, size);
    }

    @GetMapping("/{id}")
    public RequestResponse detail(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return requestService.detail(userId, id);
    }

    @GetMapping("/{id}/matches")
    public List<RequestMatchResponse> matches(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return requestService.matches(userId, id);
    }

    @PostMapping("/{id}/matches/{matchId}/confirm")
    public RequestResponse confirmMatch(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @PathVariable Long matchId) {
        return requestService.confirmMatch(userId, id, matchId);
    }

    @PostMapping("/{id}/fulfill")
    public RequestResponse fulfill(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody FulfillRequest request) {
        return requestService.fulfill(userId, id, request);
    }

    @PostMapping("/{id}/cancel")
    public RequestResponse cancel(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody CancelRequest request) {
        return requestService.cancel(userId, id, request.reason());
    }

    @GetMapping("/{id}/history")
    public List<StatusHistoryResponse> history(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return requestService.history(userId, id);
    }
}

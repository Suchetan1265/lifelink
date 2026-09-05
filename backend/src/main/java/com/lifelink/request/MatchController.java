package com.lifelink.request;

import com.lifelink.donor.dto.DonorMatchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/matches")
@PreAuthorize("hasRole('DONOR')")
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;

    @PostMapping("/{matchId}/accept")
    public DonorMatchResponse accept(@AuthenticationPrincipal Long userId, @PathVariable Long matchId) {
        return matchService.accept(userId, matchId);
    }

    @PostMapping("/{matchId}/decline")
    public DonorMatchResponse decline(@AuthenticationPrincipal Long userId, @PathVariable Long matchId) {
        return matchService.decline(userId, matchId);
    }
}

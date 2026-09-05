package com.lifelink.request;

import com.lifelink.donor.Donor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "request_matches",
        uniqueConstraints = @UniqueConstraint(columnNames = {"request_id", "donor_id"}))
@Getter
@Setter
@NoArgsConstructor
public class RequestMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private Request request;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "donor_id", nullable = false)
    private Donor donor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MatchStatus status;

    @Column(name = "distance_km")
    private Double distanceKm;

    @Column(name = "notified_at", nullable = false, updatable = false)
    private Instant notifiedAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @PrePersist
    void onCreate() {
        this.notifiedAt = Instant.now();
    }
}

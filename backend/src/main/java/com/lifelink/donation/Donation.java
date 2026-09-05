package com.lifelink.donation;

import com.lifelink.bloodbank.BloodBank;
import com.lifelink.donor.Donor;
import com.lifelink.request.Request;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Exactly one of donor / bloodBank is set (DB CHECK enforces it). */
@Entity
@Table(name = "donations")
@Getter
@Setter
@NoArgsConstructor
public class Donation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private Request request;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "donor_id")
    private Donor donor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blood_bank_id")
    private BloodBank bloodBank;

    @Column(nullable = false)
    private Integer units;

    @Column(name = "donated_at", nullable = false, updatable = false)
    private Instant donatedAt;

    @PrePersist
    void onCreate() {
        this.donatedAt = Instant.now();
    }
}

package com.lifelink.donor;

import com.lifelink.common.BloodGroup;
import com.lifelink.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "donors")
@Getter
@Setter
@NoArgsConstructor
public class Donor {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "blood_group", nullable = false)
    private BloodGroup bloodGroup;

    @Column(nullable = false)
    private Double lat;

    @Column(nullable = false)
    private Double lng;

    @Column(nullable = false)
    private String city;

    @Column(name = "radius_km", nullable = false)
    private Integer radiusKm;

    @Column(nullable = false)
    private boolean available;

    @Column(name = "last_donation_date")
    private LocalDate lastDonationDate;

    @Column(name = "next_eligible_date")
    private LocalDate nextEligibleDate;
}

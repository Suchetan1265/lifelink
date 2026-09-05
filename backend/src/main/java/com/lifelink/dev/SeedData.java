package com.lifelink.dev;

import com.lifelink.bloodbank.BloodBank;
import com.lifelink.bloodbank.BloodBankRepository;
import com.lifelink.bloodbank.BloodInventory;
import com.lifelink.bloodbank.BloodInventoryRepository;
import com.lifelink.common.BloodGroup;
import com.lifelink.donor.Donor;
import com.lifelink.donor.DonorRepository;
import com.lifelink.hospital.Hospital;
import com.lifelink.hospital.HospitalRepository;
import com.lifelink.redis.DonorGeoService;
import com.lifelink.user.Role;
import com.lifelink.user.User;
import com.lifelink.user.UserRepository;
import com.lifelink.user.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Demo data for local work and screenshots. Enable with
 * {@code --spring.profiles.active=seed}; it does nothing if a hospital already
 * exists, so it is safe to leave on.
 *
 * <p>Every account uses the same throwaway password, which is why this only
 * ever runs under an explicit profile.
 */
@Component
@Profile("seed")
@RequiredArgsConstructor
@Slf4j
public class SeedData implements ApplicationRunner {

    private static final String PASSWORD = "password123";
    private static final double CITY_LAT = 12.9716;
    private static final double CITY_LNG = 77.5946;

    private final UserRepository userRepository;
    private final DonorRepository donorRepository;
    private final HospitalRepository hospitalRepository;
    private final BloodBankRepository bloodBankRepository;
    private final BloodInventoryRepository inventoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final DonorGeoService donorGeoService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (hospitalRepository.count() > 0) {
            log.info("Seed data already present, leaving it alone");
            return;
        }

        seedHospital();
        seedBloodBank();
        List<Donor> donors = seedDonors();
        donorGeoService.rebuild(donorRepository.findMatchable(LocalDate.now()));

        log.info("Seeded 1 hospital, 1 blood bank and {} donors — every account's password is '{}'",
                donors.size(), PASSWORD);
    }

    private void seedHospital() {
        User user = createUser("hospital@lifelink.local", "+91-80-1111-0000", Role.HOSPITAL, UserStatus.ACTIVE);
        Hospital hospital = new Hospital();
        hospital.setUser(user);
        hospital.setName("City General Hospital");
        hospital.setLicenseNo("KA-HOSP-2019-4471");
        hospital.setAddress("14 Residency Road, Bengaluru 560025");
        hospital.setLat(CITY_LAT);
        hospital.setLng(CITY_LNG);
        hospital.setVerified(true);
        hospitalRepository.save(hospital);
    }

    private void seedBloodBank() {
        User user = createUser("bloodbank@lifelink.local", "+91-80-2222-0000", Role.BLOOD_BANK, UserStatus.ACTIVE);
        BloodBank bank = new BloodBank();
        bank.setUser(user);
        bank.setName("Karnataka Central Blood Bank");
        bank.setAddress("9 Lalbagh Road, Bengaluru 560027");
        bank.setLat(CITY_LAT + 0.02);
        bank.setLng(CITY_LNG - 0.01);
        bank.setVerified(true);
        bloodBankRepository.save(bank);

        int[] units = {12, 30, 6, 18, 4, 14, 2, 8};
        BloodGroup[] groups = BloodGroup.values();
        for (int i = 0; i < groups.length; i++) {
            BloodInventory row = new BloodInventory();
            row.setBloodBank(bank);
            row.setBloodGroup(groups[i]);
            row.setUnits(units[i]);
            inventoryRepository.save(row);
        }
    }

    private List<Donor> seedDonors() {
        // name, group, km north, km east, available, days until eligible again
        Object[][] specs = {
                {"Ananya Rao", BloodGroup.O_NEG, 1.2, 0.8, true, 0},
                {"Vikram Shetty", BloodGroup.O_NEG, 3.5, -2.1, true, 0},
                {"Priya Menon", BloodGroup.O_POS, 0.6, 1.4, true, 0},
                {"Rahul Iyer", BloodGroup.O_POS, 8.0, 5.0, true, 0},
                {"Meera Nair", BloodGroup.A_POS, 2.2, -1.0, true, 0},
                {"Arjun Desai", BloodGroup.A_NEG, 4.4, 3.3, true, 0},
                {"Sana Khan", BloodGroup.B_POS, 1.9, 2.6, true, 0},
                {"Karthik Reddy", BloodGroup.B_NEG, 6.1, -4.2, true, 0},
                {"Divya Pillai", BloodGroup.AB_POS, 0.9, -0.7, true, 0},
                {"Nikhil Joshi", BloodGroup.AB_NEG, 12.5, 9.0, true, 0},
                {"Farhan Ali", BloodGroup.A_POS, 2.0, 2.0, false, 0},
                {"Lakshmi Krishnan", BloodGroup.O_POS, 1.1, -1.1, true, 45},
        };

        List<Donor> donors = new java.util.ArrayList<>();
        for (int i = 0; i < specs.length; i++) {
            Object[] spec = specs[i];
            String name = (String) spec[0];
            int daysUntilEligible = (int) spec[5];

            User user = createUser(
                    "donor" + (i + 1) + "@lifelink.local", "+91-90000-000" + String.format("%02d", i),
                    Role.DONOR, UserStatus.ACTIVE);

            Donor donor = new Donor();
            donor.setUser(user);
            donor.setFullName(name);
            donor.setBloodGroup((BloodGroup) spec[1]);
            donor.setLat(CITY_LAT + kmToDegreesLat((double) spec[2]));
            donor.setLng(CITY_LNG + kmToDegreesLng((double) spec[3], CITY_LAT));
            donor.setCity("Bengaluru");
            donor.setRadiusKm(15);
            donor.setAvailable((boolean) spec[4]);
            if (daysUntilEligible > 0) {
                donor.setLastDonationDate(LocalDate.now().minusDays(90L - daysUntilEligible));
                donor.setNextEligibleDate(LocalDate.now().plusDays(daysUntilEligible));
            }
            donorRepository.save(donor);
            donors.add(donor);
        }
        return donors;
    }

    private User createUser(String email, String phone, Role role, UserStatus status) {
        User user = new User();
        user.setEmail(email);
        user.setPhone(phone);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole(role);
        user.setStatus(status);
        return userRepository.save(user);
    }

    private static double kmToDegreesLat(double km) {
        return km / 111.0;
    }

    private static double kmToDegreesLng(double km, double atLatitude) {
        return km / (111.0 * Math.cos(Math.toRadians(atLatitude)));
    }
}

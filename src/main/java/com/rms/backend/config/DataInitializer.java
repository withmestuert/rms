package com.rms.backend.config;

import com.rms.backend.properties.entity.Property;
import com.rms.backend.properties.repository.PropertyRepository;
import com.rms.backend.users.entity.User;
import com.rms.backend.users.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds default properties and users into the database on startup,
 * but only if the respective tables are completely empty.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;

    public DataInitializer(PropertyRepository propertyRepository,
                           UserRepository userRepository) {
        this.propertyRepository = propertyRepository;
        this.userRepository = userRepository;
    }

    @Override
    public void run(String... args) {
        log.info("RMS Backend initialized with zero mock data. Manual mode enabled.");
    }

    private void seedProperties() {
        if (propertyRepository.count() > 0) {
            log.info("Properties table already has data — skipping seed.");
            return;
        }

        log.info("Seeding default properties...");

        Property p1 = new Property();
        p1.setName("Greenwood PG - Phase 1");
        p1.setCode("GW-PG-P1");
        p1.setAddress("12 MG Road");
        p1.setCity("Bangalore");
        p1.setState("Karnataka");
        p1.setPincode("560001");
        p1.setPropertyType("PG");
        p1.setTotalFloors(4);
        p1.setTotalRooms(30);
        p1.setContactNumber("+91 98451 00001");
        p1.setContactEmail("gwpg1@example.com");
        p1.setStatus("ACTIVE");
        propertyRepository.save(p1);

        Property p2 = new Property();
        p2.setName("Greenwood Residency - Block A (Boys)");
        p2.setCode("GW-RES-BA");
        p2.setAddress("14 MG Road, Block A");
        p2.setCity("Bangalore");
        p2.setState("Karnataka");
        p2.setPincode("560001");
        p2.setPropertyType("HOSTEL");
        p2.setTotalFloors(5);
        p2.setTotalRooms(50);
        p2.setContactNumber("+91 98451 00002");
        p2.setContactEmail("gwres-a@example.com");
        p2.setStatus("ACTIVE");
        propertyRepository.save(p2);

        Property p3 = new Property();
        p3.setName("Greenwood Residency - Block B (Girls)");
        p3.setCode("GW-RES-BB");
        p3.setAddress("14 MG Road, Block B");
        p3.setCity("Bangalore");
        p3.setState("Karnataka");
        p3.setPincode("560001");
        p3.setPropertyType("HOSTEL");
        p3.setTotalFloors(5);
        p3.setTotalRooms(45);
        p3.setContactNumber("+91 98451 00003");
        p3.setContactEmail("gwres-b@example.com");
        p3.setStatus("ACTIVE");
        propertyRepository.save(p3);

        Property p4 = new Property();
        p4.setName("Skyview Heights PG - Wing 1");
        p4.setCode("SK-PG-W1");
        p4.setAddress("88 Residency Road");
        p4.setCity("Bangalore");
        p4.setState("Karnataka");
        p4.setPincode("560025");
        p4.setPropertyType("PG");
        p4.setTotalFloors(6);
        p4.setTotalRooms(60);
        p4.setContactNumber("+91 98451 00004");
        p4.setContactEmail("skpg1@example.com");
        p4.setStatus("ACTIVE");
        propertyRepository.save(p4);

        log.info("Seeded 4 default properties.");
    }

    private void seedUsers() {
        if (userRepository.count() > 0) {
            log.info("Users table already has data — skipping seed.");
            return;
        }

        log.info("Seeding default users...");

        User admin = new User();
        admin.setUsername("rajesh.sharma");
        admin.setEmail("rajesh.sharma@greenwood.com");
        admin.setFullName("Rajesh Sharma");
        admin.setRole("ADMIN");
        admin.setPhone("+91 98451 10001");
        admin.setStatus("ACTIVE");
        userRepository.save(admin);

        User manager = new User();
        manager.setUsername("priya.nair");
        manager.setEmail("priya.nair@greenwood.com");
        manager.setFullName("Priya Nair");
        manager.setRole("PROPERTY_MANAGER");
        manager.setPhone("+91 98451 10002");
        manager.setStatus("ACTIVE");
        userRepository.save(manager);

        User staff = new User();
        staff.setUsername("ravi.kumar");
        staff.setEmail("ravi.kumar@greenwood.com");
        staff.setFullName("Ravi Kumar");
        staff.setRole("STAFF");
        staff.setPhone("+91 98451 10003");
        staff.setStatus("ACTIVE");
        userRepository.save(staff);

        log.info("Seeded 3 default users (Admin, Property Manager, Staff).");
    }
}

package com.rms.backend.security;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The fixed primary key makes first-root provisioning atomic across application instances. */
@Entity
@Table(name = "root_bootstrap")
public class RootBootstrap {
    @Id
    private Long id = 1L;
}

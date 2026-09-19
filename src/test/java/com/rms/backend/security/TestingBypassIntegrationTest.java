package com.rms.backend.security;

import com.rms.backend.users.entity.User;
import com.rms.backend.users.repository.UserRepository;
import com.rms.backend.properties.entity.Property;
import com.rms.backend.properties.repository.PropertyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:bypass;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "rms.security.enabled=false", "rms.security.test-owner-id=1"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TestingBypassIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PropertyRepository properties;
    @Autowired SecurityProperties configuration;

    @Test void bypassesBothAuthenticationAndPropertyAuthorizationButNotIntegrity() throws Exception {
        User owner = new User(); owner.setUsername("test-owner"); owner.setEmail("test@example.com");
        owner.setFullName("Test"); owner.setRole("OWNER"); owner.setStatus("ACTIVE");
        configuration.setTestOwnerId(users.saveAndFlush(owner).getId());
        Property foreign = new Property(); foreign.setName("Another owner's property"); foreign.setOwnerId(987L);
        Long id = properties.saveAndFlush(foreign).getId();
        mvc.perform(get("/api/properties/"+id)).andExpect(status().isOk())
                .andExpect(header().string("X-RMS-Authentication","disabled-for-testing"));
        mvc.perform(put("/api/properties/"+id).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Changed in test\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/properties").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Test property\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/properties")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(post("/api/system/clean-database")).andExpect(status().isForbidden());
    }
}

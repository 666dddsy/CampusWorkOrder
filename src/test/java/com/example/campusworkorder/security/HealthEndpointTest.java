package com.example.campusworkorder.security;

import com.example.campusworkorder.domain.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class HealthEndpointTest {
    @Autowired
    MockMvc mockMvc;

    private static AppUserPrincipal admin(){
        return new AppUserPrincipal(4L,"admin_001","X", UserRole.ADMIN);
    }

    @Test
    void anonymousHealth_shouldReturn200AndHideDetails()throws Exception{
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.details").doesNotExist())
                .andExpect(jsonPath("$.components").doesNotExist());

    }
    @Test
    void anonymousHealth_shouldReturn401()throws Exception{
        mockMvc.perform(get("/tickets/mine"))
                .andExpect(status().isUnauthorized());
    }
    @Test
    void anonymousHealth_shouldReturn404()throws Exception{
        mockMvc.perform(get("/actuator/env")
                        .with(user(admin())))
                .andExpect(status().isNotFound());
    }
}

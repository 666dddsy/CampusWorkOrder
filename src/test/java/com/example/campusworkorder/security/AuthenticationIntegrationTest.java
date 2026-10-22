package com.example.campusworkorder.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AppUserDetailsService appUserDetailsService;

    private String username;
    private Long userId;

    /** 每条测试自己造一个用户，不依赖 seed 数据 */
    @BeforeEach
    void setUp() {
        username = "auth_it_" + System.nanoTime();
        jdbcTemplate.update(
                "insert into app_user(username, password_hash, role) values (?, ?, ?)",
                username,
                passwordEncoder.encode("it-password-123"),
                "REQUESTER"
        );
        userId = jdbcTemplate.queryForObject(
                "select id from app_user where username = ?", Long.class, username);
    }

    @AfterEach
    void tearDown() {
        if (userId != null) {
            jdbcTemplate.update("delete from app_user where id = ?", userId);
        }
    }

    // ---------- 1. UserDetailsService ----------

    @Test
    void loadUserByUsername_shouldLoadIdRoleAndHash() {
        AppUserPrincipal principal =
                (AppUserPrincipal) appUserDetailsService.loadUserByUsername(username);

        assertEquals(userId, principal.getId());
        assertEquals(username, principal.getUsername());
        assertTrue(passwordEncoder.matches("it-password-123", principal.getPassword()));
        assertFalse(passwordEncoder.matches("not-the-password", principal.getPassword()));
        assertEquals("ROLE_REQUESTER",
                principal.getAuthorities().iterator().next().getAuthority());
        assertTrue(principal.getPassword().startsWith("{bcrypt}"));
    }

    @Test
    void loadUserByUsername_withUnknownUsername_shouldThrow() {
        String wrongUsername="no_such_user_" + System.nanoTime();
        UsernameNotFoundException exception= assertThrows(UsernameNotFoundException.class,
                () -> appUserDetailsService.loadUserByUsername(
                        wrongUsername));
        assertTrue(exception.getMessage().contains(wrongUsername));
    }

    // ---------- 2. 真实 Basic 认证 ----------

    @Test
    void correctPassword_shouldPassAuthentication() throws Exception {
        // 认证通过后进入 Controller；工单不存在 → 404，而不是 401
        mockMvc.perform(get("/tickets/0")
                        .with(httpBasic(username, "it-password-123")))
                .andExpect(status().isNotFound());
    }

    @Test
    void wrongPassword_shouldReturn401() throws Exception {
        mockMvc.perform(get("/tickets/999999")
                        .with(httpBasic(username, "wrong-password")))
                .andExpect(status().isUnauthorized());
    }
}
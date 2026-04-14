package com.example.AuthService.service.impl;

import com.example.AuthService.dto.AuthProfileDto;
import com.example.AuthService.entity.Role;
import com.example.AuthService.entity.User;
import com.example.AuthService.security.OAuth2UserPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests cho AuthProfileServiceImpl – kiểm tra xây dựng profile từ principal.
 */
@ExtendWith(MockitoExtension.class)
class AuthProfileServiceImplTest {

    @InjectMocks
    private AuthProfileServiceImpl authProfileService;

    // ==================== BUILD PROFILE ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Build profile khi principal = null
     * Input: principal = null
     * Expected Output: AuthProfileDto authenticated = false
     * Notes: Kiểm tra nhánh principal == null
     */
    @Test
    @DisplayName("TC-FR-02-001: Principal null → not authenticated")
    void TC_FR_02_001() {
        AuthProfileDto result = authProfileService.buildProfile(null, true);

        assertThat(result.isAuthenticated()).isFalse();
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Build profile từ OAuth2UserPrincipal (Google login)
     * Input: OAuth2UserPrincipal với attributes
     * Expected Output: AuthProfileDto với provider = "google", authenticated = true
     * Notes: Kiểm tra nhánh instanceof OAuth2UserPrincipal
     */
    @Test
    @DisplayName("TC-FR-02-001: OAuth2UserPrincipal → google profile")
    void TC_FR_02_001() {
        Role role = Role.builder().id(1L).name("USER").build();
        User user = User.builder().id(1L).email("user@gmail.com").name("User")
                .role(role).enabled(true).build();

        Map<String, Object> attrs = Map.of(
                "email", "user@gmail.com",
                "name", "Google User",
                "picture", "https://pic.jpg",
                "sub", "google-sub-123"
        );

        OAuth2UserPrincipal principal = new OAuth2UserPrincipal(user, attrs);

        AuthProfileDto result = authProfileService.buildProfile(principal, true);

        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getProvider()).isEqualTo("google");
        assertThat(result.getEmail()).isEqualTo("user@gmail.com");
        assertThat(result.getName()).isEqualTo("Google User");
        assertThat(result.getAuthorities()).contains("ROLE_USER");
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Build profile từ UserDetails (JWT login)
     * Input: UserDetails (User entity)
     * Expected Output: AuthProfileDto với provider = "local/jwt"
     * Notes: Kiểm tra nhánh instanceof UserDetails
     */
    @Test
    @DisplayName("TC-FR-02-001: UserDetails → local/jwt profile")
    void TC_FR_02_001() {
        Role role = Role.builder().id(1L).name("ADMIN").build();
        UserDetails user = User.builder().id(1L).email("admin@test.com")
                .name("Admin").role(role).enabled(true).build();

        AuthProfileDto result = authProfileService.buildProfile(user, true);

        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getProvider()).isEqualTo("local/jwt");
        assertThat(result.getEmail()).isEqualTo("admin@test.com");
        assertThat(result.getAuthorities()).contains("ROLE_ADMIN");
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Build profile không bao gồm authorities khi includeAuthorities = false
     * Input: includeAuthorities = false
     * Expected Output: authorities = null
     * Notes: Kiểm tra nhánh includeAuthorities == false
     */
    @Test
    @DisplayName("TC-FR-02-001: includeAuthorities false → null authorities")
    void TC_FR_02_001() {
        Role role = Role.builder().id(1L).name("USER").build();
        UserDetails user = User.builder().id(1L).email("user@test.com")
                .role(role).enabled(true).build();

        AuthProfileDto result = authProfileService.buildProfile(user, false);

        assertThat(result.getAuthorities()).isNull();
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Build profile từ unknown principal type
     * Input: Object không phải OAuth2User hay UserDetails
     * Expected Output: AuthProfileDto authenticated = true, provider = class name
     * Notes: Kiểm tra nhánh default/fallback
     */
    @Test
    @DisplayName("TC-FR-02-001: Unknown principal → class name provider")
    void TC_FR_02_001() {
        Object unknownPrincipal = "some-string-principal";

        AuthProfileDto result = authProfileService.buildProfile(unknownPrincipal, true);

        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getProvider()).isEqualTo("String");
    }
}

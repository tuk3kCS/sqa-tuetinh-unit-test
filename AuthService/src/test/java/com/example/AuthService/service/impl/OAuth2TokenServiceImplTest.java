package com.example.AuthService.service.impl;

import com.example.AuthService.dto.response.AuthTokenResponse;
import com.example.AuthService.entity.Role;
import com.example.AuthService.entity.User;
import com.example.AuthService.security.jwt.JwtProperties;
import com.example.AuthService.security.jwt.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests cho OAuth2TokenServiceImpl – kiểm tra tạo token response cho OAuth2 login.
 */
@ExtendWith(MockitoExtension.class)
class OAuth2TokenServiceImplTest {

    @Mock private JwtService jwtService;
    @Mock private JwtProperties jwtProps;

    @InjectMocks
    private OAuth2TokenServiceImpl tokenService;

    private User user;

    @BeforeEach
    void setUp() {
        Role role = Role.builder().id(1L).name("USER").build();
        user = User.builder().id(1L).email("user@gmail.com").name("Test")
                .role(role).enabled(true).build();
    }

    // ==================== BUILD TOKEN RESPONSE ====================

    /**
     * Test Case ID: TC_AUTH_OAuth2TokenServiceImpl_buildTokenResponse_001
     * Test Objective: Tạo AuthTokenResponse thành công với attributes đầy đủ
     * Input: UserDetails + attributes chứa email, name, picture, sub
     * Expected Output: AuthTokenResponse chứa đầy đủ thông tin
     * Notes: Happy path – tất cả fields được map đúng
     */
    @Test
    @DisplayName("TC_AUTH_OAuth2TokenServiceImpl_buildTokenResponse_001: Tạo token response thành công")
    void TC_AUTH_OAuth2TokenServiceImpl_buildTokenResponse_001() {
        Map<String, Object> attrs = Map.of(
                "email", "user@gmail.com",
                "name", "Google User",
                "picture", "https://pic.jpg",
                "sub", "google-sub-123"
        );

        when(jwtService.generateAccessToken(user)).thenReturn("access-token");
        when(jwtService.generateRefreshToken("user@gmail.com")).thenReturn("refresh-token");
        when(jwtProps.getAccessExpirationMs()).thenReturn(3600000L);

        AuthTokenResponse result = tokenService.buildTokenResponse(user, attrs);

        assertThat(result.getTokenType()).isEqualTo("Bearer");
        assertThat(result.getAccessToken()).isEqualTo("access-token");
        assertThat(result.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(result.getExpiresIn()).isEqualTo(3600000L);
        assertThat(result.getProvider()).isEqualTo("google");
        assertThat(result.getEmail()).isEqualTo("user@gmail.com");
        assertThat(result.getName()).isEqualTo("Google User");
        assertThat(result.getPicture()).isEqualTo("https://pic.jpg");
        assertThat(result.getSub()).isEqualTo("google-sub-123");
        assertThat(result.getAuthorities()).contains("ROLE_USER");
    }

    /**
     * Test Case ID: TC_AUTH_OAuth2TokenServiceImpl_buildTokenResponse_002
     * Test Objective: Tạo token response khi attributes = null
     * Input: attributes = null
     * Expected Output: AuthTokenResponse với email fallback từ username
     * Notes: Kiểm tra nhánh attributes == null → emptyMap
     */
    @Test
    @DisplayName("TC_AUTH_OAuth2TokenServiceImpl_buildTokenResponse_002: Attributes null → fallback")
    void TC_AUTH_OAuth2TokenServiceImpl_buildTokenResponse_002() {
        when(jwtService.generateAccessToken(user)).thenReturn("access");
        when(jwtService.generateRefreshToken("user@gmail.com")).thenReturn("refresh");
        when(jwtProps.getAccessExpirationMs()).thenReturn(1800000L);

        AuthTokenResponse result = tokenService.buildTokenResponse(user, null);

        assertThat(result.getEmail()).isEqualTo("user@gmail.com");
        assertThat(result.getName()).isNull();
        assertThat(result.getPicture()).isNull();
    }

    /**
     * Test Case ID: TC_AUTH_OAuth2TokenServiceImpl_buildTokenResponse_003
     * Test Objective: Tạo token response khi user không có authorities
     * Input: User với authorities null
     * Expected Output: authorities = empty list
     * Notes: Kiểm tra nhánh getAuthorities == null
     */
    @Test
    @DisplayName("TC_AUTH_OAuth2TokenServiceImpl_buildTokenResponse_003: Authorities null → empty list")
    void TC_AUTH_OAuth2TokenServiceImpl_buildTokenResponse_003() {
        User noRoleUser = User.builder().id(2L).email("norole@gmail.com")
                .name("No Role").role(null).enabled(true).build();

        when(jwtService.generateAccessToken(noRoleUser)).thenReturn("tok");
        when(jwtService.generateRefreshToken("norole@gmail.com")).thenReturn("ref");
        when(jwtProps.getAccessExpirationMs()).thenReturn(1000L);

        AuthTokenResponse result = tokenService.buildTokenResponse(noRoleUser, Collections.emptyMap());

        assertThat(result.getAuthorities()).isEmpty();
    }
}

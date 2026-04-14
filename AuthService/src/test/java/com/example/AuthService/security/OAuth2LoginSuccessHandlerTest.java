package com.example.AuthService.security;

import com.example.AuthService.dto.response.AuthTokenResponse;
import com.example.AuthService.entity.Role;
import com.example.AuthService.entity.User;
import com.example.AuthService.service.OAuth2TokenService;
import com.example.AuthService.service.SocialLoginService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests cho {@link OAuth2LoginSuccessHandler}.
 * Kiểm tra xử lý thành công sau khi OAuth2 login.
 */
@ExtendWith(MockitoExtension.class)
class OAuth2LoginSuccessHandlerTest {

    @Mock
    private OAuth2TokenService tokenService;

    @Mock
    private SocialLoginService socialLoginService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private OAuth2LoginSuccessHandler handler;

    private User createTestUser() {
        Role role = Role.builder().id(1L).name("USER").build();
        return User.builder()
                .id(1L).email("google@test.com").name("Google User")
                .role(role).enabled(true)
                .accountNonExpired(true).accountNonLocked(true).credentialsNonExpired(true)
                .build();
    }

    // ======================== ON AUTHENTICATION SUCCESS ========================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Xử lý thành công khi principal là OAuth2UserPrincipal
     * Input: Authentication với OAuth2UserPrincipal chứa User + attributes
     * Expected Output: HTTP 200, response body chứa token JSON
     * Notes: Happy path - OAuth2 login qua Google
     */
    @Test
    @DisplayName("TC-FR-02-001: OAuth2UserPrincipal thành công")
    void TC_FR_02_001() throws Exception {
        User user = createTestUser();
        Map<String, Object> attributes = Map.of("sub", "google-id-123", "email", "google@test.com");
        OAuth2UserPrincipal principal = new OAuth2UserPrincipal(user, attributes);

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(principal);

        AuthTokenResponse tokenResponse = AuthTokenResponse.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .tokenType("Bearer")
                .email("google@test.com")
                .build();
        when(tokenService.buildTokenResponse(any(), any())).thenReturn(tokenResponse);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, auth);

        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        assertTrue(response.getContentAsString().contains("access-token"));
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Xử lý khi principal là OAuth2User (fallback)
     * Input: Authentication với DefaultOAuth2User
     * Expected Output: HTTP 200, token response
     * Notes: Fallback path - upsert user từ OAuth2User
     */
    @Test
    @DisplayName("TC-FR-02-001: OAuth2User fallback")
    void TC_FR_02_001() throws Exception {
        Map<String, Object> attributes = Map.of(
                "sub", "google-id-456",
                "email", "new@test.com",
                "name", "New User"
        );
        OAuth2User oAuth2User = new DefaultOAuth2User(
                Collections.emptyList(), attributes, "sub");

        User savedUser = createTestUser();
        when(socialLoginService.upsertGoogleUser(oAuth2User)).thenReturn(savedUser);

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(oAuth2User);

        AuthTokenResponse tokenResponse = AuthTokenResponse.builder()
                .accessToken("token-xyz").tokenType("Bearer").build();
        when(tokenService.buildTokenResponse(any(), any())).thenReturn(tokenResponse);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, auth);

        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        verify(socialLoginService).upsertGoogleUser(oAuth2User);
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Xử lý khi principal là UserDetails thông thường
     * Input: Authentication với User entity (UserDetails)
     * Expected Output: HTTP 200, token response
     * Notes: Trường hợp UserDetails bình thường (không phải OAuth2)
     */
    @Test
    @DisplayName("TC-FR-02-001: UserDetails principal")
    void TC_FR_02_001() throws Exception {
        User user = createTestUser();

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(user);

        AuthTokenResponse tokenResponse = AuthTokenResponse.builder()
                .accessToken("token-abc").tokenType("Bearer").build();
        when(tokenService.buildTokenResponse(any(), any())).thenReturn(tokenResponse);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, auth);

        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Xử lý khi principal không phải loại nào hỗ trợ
     * Input: Authentication với principal = String
     * Expected Output: HTTP 401, error message
     * Notes: Unsupported principal type
     */
    @Test
    @DisplayName("TC-FR-02-001: Unsupported principal type")
    void TC_FR_02_001() throws Exception {
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn("unknown-principal");

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, auth);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        assertTrue(response.getContentAsString().contains("Unsupported principal type"));
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Xử lý OAuth2UserPrincipal với attributes null
     * Input: OAuth2UserPrincipal có attributes = null
     * Expected Output: HTTP 200, token response (attributes thay bằng empty map)
     * Notes: Edge case - attributes có thể null
     */
    @Test
    @DisplayName("TC-FR-02-001: Attributes null")
    void TC_FR_02_001() throws Exception {
        User user = createTestUser();
        OAuth2UserPrincipal principal = new OAuth2UserPrincipal(user, null);

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(principal);

        AuthTokenResponse tokenResponse = AuthTokenResponse.builder()
                .accessToken("token").tokenType("Bearer").build();
        when(tokenService.buildTokenResponse(any(), any())).thenReturn(tokenResponse);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, auth);

        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
    }
}

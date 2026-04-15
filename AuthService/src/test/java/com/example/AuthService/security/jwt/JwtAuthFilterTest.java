package com.example.AuthService.security.jwt;

import com.example.AuthService.entity.Role;
import com.example.AuthService.entity.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests cho {@link JwtAuthFilter}.
 * Kiểm tra logic filter JWT cho các request HTTP.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthFilter jwtAuthFilter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    private User createTestUser() {
        Role role = Role.builder().id(1L).name("USER").build();
        return User.builder()
                .id(1L).email("test@example.com").name("Test User")
                .password("encoded").role(role)
                .enabled(true).accountNonExpired(true)
                .accountNonLocked(true).credentialsNonExpired(true)
                .build();
    }

    // ======================== DO FILTER INTERNAL - VALID TOKEN ========================

    /**
     * Test Case ID: TC_AUTH_JwtAuthFilter_doFilterInternal_001
     * Test Objective: Filter xác thực thành công với token hợp lệ
     * Input: Request có header "Authorization: Bearer valid-token"
     * Expected Output: SecurityContext chứa Authentication, chain.doFilter được gọi
     * Notes: Happy path - token hợp lệ → set authentication
     */
    @Test
    @DisplayName("TC_AUTH_JwtAuthFilter_doFilterInternal_001: Token hợp lệ - xác thực thành công")
    void TC_AUTH_JwtAuthFilter_doFilterInternal_001() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/orders");
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        User user = createTestUser();
        when(jwtService.extractUsername("valid-token")).thenReturn("test@example.com");
        when(jwtService.isValid("valid-token")).thenReturn(true);
        when(userDetailsService.loadUserByUsername("test@example.com")).thenReturn(user);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("test@example.com",
                SecurityContextHolder.getContext().getAuthentication().getName());
        verify(filterChain).doFilter(request, response);
    }

    /**
     * Test Case ID: TC_AUTH_JwtAuthFilter_doFilterInternal_002
     * Test Objective: Filter bỏ qua khi không có Authorization header
     * Input: Request không có header Authorization
     * Expected Output: SecurityContext rỗng, chain.doFilter vẫn được gọi
     * Notes: Không có token → bỏ qua filter
     */
    @Test
    @DisplayName("TC_AUTH_JwtAuthFilter_doFilterInternal_002: Không có header Authorization")
    void TC_AUTH_JwtAuthFilter_doFilterInternal_002() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    /**
     * Test Case ID: TC_AUTH_JwtAuthFilter_doFilterInternal_003
     * Test Objective: Filter bỏ qua khi header không bắt đầu bằng "Bearer "
     * Input: Authorization header = "Basic abc123"
     * Expected Output: SecurityContext rỗng
     * Notes: Chỉ xử lý Bearer token
     */
    @Test
    @DisplayName("TC_AUTH_JwtAuthFilter_doFilterInternal_003: Header không phải Bearer")
    void TC_AUTH_JwtAuthFilter_doFilterInternal_003() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/orders");
        request.addHeader("Authorization", "Basic abc123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    /**
     * Test Case ID: TC_AUTH_JwtAuthFilter_doFilterInternal_004
     * Test Objective: Filter bỏ qua khi token không hợp lệ (extractUsername ném exception)
     * Input: Token bị hỏng
     * Expected Output: SecurityContext rỗng, chain.doFilter vẫn gọi
     * Notes: Token không parse được → bỏ qua
     */
    @Test
    @DisplayName("TC_AUTH_JwtAuthFilter_doFilterInternal_004: Token không hợp lệ - extractUsername lỗi")
    void TC_AUTH_JwtAuthFilter_doFilterInternal_004() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/orders");
        request.addHeader("Authorization", "Bearer bad-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtService.extractUsername("bad-token")).thenThrow(new RuntimeException("Invalid token"));

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    /**
     * Test Case ID: TC_AUTH_JwtAuthFilter_doFilterInternal_005
     * Test Objective: Filter bỏ qua khi token hết hạn (isValid = false)
     * Input: Token hết hạn
     * Expected Output: SecurityContext rỗng
     * Notes: Token parse được nhưng đã expired
     */
    @Test
    @DisplayName("TC_AUTH_JwtAuthFilter_doFilterInternal_005: Token hết hạn - isValid false")
    void TC_AUTH_JwtAuthFilter_doFilterInternal_005() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/orders");
        request.addHeader("Authorization", "Bearer expired-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtService.extractUsername("expired-token")).thenReturn("test@example.com");
        when(userDetailsService.loadUserByUsername("test@example.com")).thenReturn(createTestUser());
        when(jwtService.isValid("expired-token")).thenReturn(false);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    // ======================== SHOULD NOT FILTER ========================

    /**
     * Test Case ID: TC_AUTH_JwtAuthFilter_shouldNotFilter_001
     * Test Objective: Bỏ qua filter cho đường dẫn /api/auth/*
     * Input: URI = "/api/auth/login"
     * Expected Output: shouldNotFilter() trả về true
     * Notes: Các endpoint auth không cần JWT
     */
    @Test
    @DisplayName("TC_AUTH_JwtAuthFilter_shouldNotFilter_001: Bỏ qua /api/auth/ paths")
    void TC_AUTH_JwtAuthFilter_shouldNotFilter_001() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/auth/login");

        assertTrue(jwtAuthFilter.shouldNotFilter(request));
    }

    /**
     * Test Case ID: TC_AUTH_JwtAuthFilter_shouldNotFilter_002
     * Test Objective: Bỏ qua filter cho đường dẫn /oauth2/*
     * Input: URI = "/oauth2/authorization/google"
     * Expected Output: shouldNotFilter() trả về true
     * Notes: OAuth2 flow không cần JWT filter
     */
    @Test
    @DisplayName("TC_AUTH_JwtAuthFilter_shouldNotFilter_002: Bỏ qua /oauth2/ paths")
    void TC_AUTH_JwtAuthFilter_shouldNotFilter_002() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/oauth2/authorization/google");

        assertTrue(jwtAuthFilter.shouldNotFilter(request));
    }

    /**
     * Test Case ID: TC_AUTH_JwtAuthFilter_shouldNotFilter_003
     * Test Objective: Không bỏ qua filter cho đường dẫn /api/orders
     * Input: URI = "/api/orders"
     * Expected Output: shouldNotFilter() trả về false
     * Notes: Endpoint cần JWT
     */
    @Test
    @DisplayName("TC_AUTH_JwtAuthFilter_shouldNotFilter_003: Không bỏ qua /api/orders")
    void TC_AUTH_JwtAuthFilter_shouldNotFilter_003() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/orders");

        assertFalse(jwtAuthFilter.shouldNotFilter(request));
    }

    /**
     * Test Case ID: TC_AUTH_JwtAuthFilter_shouldNotFilter_004
     * Test Objective: Bỏ qua filter cho /favicon.ico
     * Input: URI = "/favicon.ico"
     * Expected Output: shouldNotFilter() trả về true
     * Notes: Static resource
     */
    @Test
    @DisplayName("TC_AUTH_JwtAuthFilter_shouldNotFilter_004: Bỏ qua /favicon.ico")
    void TC_AUTH_JwtAuthFilter_shouldNotFilter_004() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/favicon.ico");

        assertTrue(jwtAuthFilter.shouldNotFilter(request));
    }

    /**
     * Test Case ID: TC_AUTH_JwtAuthFilter_shouldNotFilter_005
     * Test Objective: Bỏ qua filter cho /error
     * Input: URI = "/error"
     * Expected Output: shouldNotFilter() trả về true
     * Notes: Error page
     */
    @Test
    @DisplayName("TC_AUTH_JwtAuthFilter_shouldNotFilter_005: Bỏ qua /error")
    void TC_AUTH_JwtAuthFilter_shouldNotFilter_005() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/error");

        assertTrue(jwtAuthFilter.shouldNotFilter(request));
    }
}

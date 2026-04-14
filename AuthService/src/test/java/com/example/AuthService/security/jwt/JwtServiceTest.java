package com.example.AuthService.security.jwt;

import com.example.AuthService.entity.Role;
import com.example.AuthService.entity.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.SecretKey;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests cho {@link JwtService}.
 * Kiểm tra việc tạo và xác thực JWT token.
 */
@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    private JwtService jwtService;
    private JwtProperties jwtProperties;

    private static final String TEST_SECRET = "dGVzdC1zZWNyZXQta2V5LWZvci11bml0LXRlc3RpbmctcHVycG9zZXMtb25seS1taW4tMjU2LWJpdHM=";
    private static final long ACCESS_EXPIRATION_MS = 900000L;
    private static final long REFRESH_EXPIRATION_MS = 1209600000L;

    @BeforeEach
    void setUp() throws Exception {
        jwtProperties = new JwtProperties();
        jwtProperties.setSecret(TEST_SECRET);
        jwtProperties.setAccessExpirationMs(ACCESS_EXPIRATION_MS);
        jwtProperties.setRefreshExpirationMs(REFRESH_EXPIRATION_MS);

        jwtService = new JwtService(jwtProperties);

        // Gọi @PostConstruct initKey() thủ công
        java.lang.reflect.Method initMethod = JwtService.class.getDeclaredMethod("initKey");
        initMethod.setAccessible(true);
        initMethod.invoke(jwtService);
    }

    private User createTestUser() {
        Role role = Role.builder().id(1L).name("USER").build();
        return User.builder()
                .id(1L)
                .email("test@example.com")
                .name("Test User")
                .password("encoded-password")
                .role(role)
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build();
    }

    // ======================== GENERATE ACCESS TOKEN ========================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Tạo access token thành công với thông tin user hợp lệ
     * Input: User(email="test@example.com", role=USER, id=1)
     * Expected Output: Token không null, không rỗng
     * Notes: Happy path - tạo JWT access token
     */
    @Test
    @DisplayName("TC-FR-02-001: Tạo access token thành công")
    void TC_FR_02_001() {
        User user = createTestUser();
        String token = jwtService.generateAccessToken(user);

        assertNotNull(token, "Token không được null");
        assertFalse(token.isEmpty(), "Token không được rỗng");
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Access token chứa đúng username (email) trong subject
     * Input: User(email="test@example.com")
     * Expected Output: extractUsername trả về "test@example.com"
     * Notes: Kiểm tra subject trong JWT payload
     */
    @Test
    @DisplayName("TC-FR-02-001: Access token chứa đúng username")
    void TC_FR_02_001() {
        User user = createTestUser();
        String token = jwtService.generateAccessToken(user);
        String extractedUsername = jwtService.extractUsername(token);

        assertEquals("test@example.com", extractedUsername);
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Access token hợp lệ (chưa hết hạn)
     * Input: User hợp lệ
     * Expected Output: isValid() trả về true
     * Notes: Token vừa tạo phải hợp lệ
     */
    @Test
    @DisplayName("TC-FR-02-001: Access token hợp lệ sau khi tạo")
    void TC_FR_02_001() {
        User user = createTestUser();
        String token = jwtService.generateAccessToken(user);

        assertTrue(jwtService.isValid(token));
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Access token chứa email được lowercase
     * Input: User(email="Test@Example.COM")
     * Expected Output: extractUsername trả về "test@example.com"
     * Notes: Email luôn được chuẩn hóa về lowercase
     */
    @Test
    @DisplayName("TC-FR-02-001: Email trong token được lowercase")
    void TC_FR_02_001() {
        User user = createTestUser();
        user.setEmail("Test@Example.COM");
        String token = jwtService.generateAccessToken(user);

        assertEquals("test@example.com", jwtService.extractUsername(token));
    }

    // ======================== GENERATE REFRESH TOKEN ========================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Tạo refresh token thành công
     * Input: username="test@example.com"
     * Expected Output: Token không null, không rỗng
     * Notes: Happy path
     */
    @Test
    @DisplayName("TC-FR-02-001: Tạo refresh token thành công")
    void TC_FR_02_001() {
        String token = jwtService.generateRefreshToken("test@example.com");

        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Refresh token chứa đúng username
     * Input: username="test@example.com"
     * Expected Output: extractUsername trả về "test@example.com"
     * Notes: Kiểm tra subject
     */
    @Test
    @DisplayName("TC-FR-02-001: Refresh token chứa đúng username")
    void TC_FR_02_001() {
        String token = jwtService.generateRefreshToken("test@example.com");
        String extractedUsername = jwtService.extractUsername(token);

        assertEquals("test@example.com", extractedUsername);
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Refresh token hợp lệ sau khi tạo
     * Input: username="test@example.com"
     * Expected Output: isValid() trả về true
     * Notes: Token mới phải hợp lệ
     */
    @Test
    @DisplayName("TC-FR-02-001: Refresh token hợp lệ")
    void TC_FR_02_001() {
        String token = jwtService.generateRefreshToken("test@example.com");

        assertTrue(jwtService.isValid(token));
    }

    // ======================== EXTRACT USERNAME ========================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Trích xuất username từ access token
     * Input: Access token hợp lệ
     * Expected Output: Email đúng
     * Notes: Happy path
     */
    @Test
    @DisplayName("TC-FR-02-001: Trích xuất username từ access token")
    void TC_FR_02_001() {
        User user = createTestUser();
        String token = jwtService.generateAccessToken(user);

        assertEquals("test@example.com", jwtService.extractUsername(token));
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Trích xuất username từ refresh token
     * Input: Refresh token hợp lệ
     * Expected Output: Username đúng
     * Notes: Cả access và refresh đều lưu subject
     */
    @Test
    @DisplayName("TC-FR-02-001: Trích xuất username từ refresh token")
    void TC_FR_02_001() {
        String token = jwtService.generateRefreshToken("user@test.com");

        assertEquals("user@test.com", jwtService.extractUsername(token));
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Trích xuất username từ token bị sửa đổi gây exception
     * Input: Token bị cắt bớt
     * Expected Output: Exception được ném
     * Notes: Token bị tamper → lỗi parse
     */
    @Test
    @DisplayName("TC-FR-02-001: Token bị sửa đổi ném exception")
    void TC_FR_02_001() {
        assertThrows(Exception.class, () -> jwtService.extractUsername("tampered.invalid.token"));
    }

    // ======================== IS VALID ========================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Kiểm tra token hợp lệ (chưa hết hạn)
     * Input: Token vừa tạo
     * Expected Output: true
     * Notes: Happy path
     */
    @Test
    @DisplayName("TC-FR-02-001: Token hợp lệ trả về true")
    void TC_FR_02_001() {
        User user = createTestUser();
        String token = jwtService.generateAccessToken(user);

        assertTrue(jwtService.isValid(token));
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Kiểm tra token đã hết hạn
     * Input: Token tạo với expiration trong quá khứ
     * Expected Output: Exception khi parse (token hết hạn)
     * Notes: Sử dụng key cùng secret để tạo token hết hạn
     */
    @Test
    @DisplayName("TC-FR-02-001: Token hết hạn ném exception")
    void TC_FR_02_001() {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET));

        String expiredToken = Jwts.builder()
                .subject("test@example.com")
                .issuedAt(Date.from(Instant.now().minusSeconds(7200)))
                .expiration(Date.from(Instant.now().minusSeconds(3600)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        assertThrows(Exception.class, () -> jwtService.isValid(expiredToken));
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Kiểm tra token bị sửa đổi (tampered)
     * Input: Token hợp lệ nhưng bị thay đổi ký tự
     * Expected Output: Exception khi parse
     * Notes: Chữ ký không khớp
     */
    @Test
    @DisplayName("TC-FR-02-001: Token bị tamper ném exception")
    void TC_FR_02_001() {
        User user = createTestUser();
        String validToken = jwtService.generateAccessToken(user);
        String tamperedToken = validToken.substring(0, validToken.length() - 5) + "XXXXX";

        assertThrows(Exception.class, () -> jwtService.isValid(tamperedToken));
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Kiểm tra token rỗng
     * Input: Token = ""
     * Expected Output: Exception
     * Notes: Token rỗng không hợp lệ
     */
    @Test
    @DisplayName("TC-FR-02-001: Token rỗng ném exception")
    void TC_FR_02_001() {
        assertThrows(Exception.class, () -> jwtService.isValid(""));
    }
}

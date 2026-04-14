package com.example.AuthService.service.impl;

import com.example.AuthService.dto.request.*;
import com.example.AuthService.dto.response.TokenResponse;
import com.example.AuthService.entity.Country;
import com.example.AuthService.entity.Role;
import com.example.AuthService.entity.User;
import com.example.AuthService.otp.EmailOtp;
import com.example.AuthService.otp.OtpService;
import com.example.AuthService.otp.OtpType;
import com.example.AuthService.repository.CountryRepository;
import com.example.AuthService.repository.RoleRepository;
import com.example.AuthService.repository.UserRepository;
import com.example.AuthService.security.jwt.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests cho AuthServiceImpl – kiểm tra logic đăng nhập, đăng ký, quên/reset mật khẩu, OTP.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private AuthenticationManager authManager;
    @Mock private UserRepository userRepo;
    @Mock private RoleRepository roleRepo;
    @Mock private CountryRepository countryRepo;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwt;
    @Mock private OtpService otpService;
    @Spy  private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AuthServiceImpl authService;

    private User validUser;
    private Role adminRole;
    private Role userRole;

    @BeforeEach
    void setUp() {
        userRole = Role.builder().id(1L).name("USER").build();
        adminRole = Role.builder().id(2L).name("ADMIN").build();

        validUser = User.builder()
                .id(1L)
                .email("test@example.com")
                .name("Test User")
                .password("encodedPassword")
                .role(userRole)
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build();
    }

    // ==================== LOGIN ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Đăng nhập thành công với email và password hợp lệ
     * Input: email = "test@example.com", password = "password123", clientView = null
     * Expected Output: TokenResponse chứa accessToken và refreshToken
     * Notes: Happy path – xác thực thành công, trả về token
     */
    @Test
    @DisplayName("TC-FR-01-004: Đăng nhập thành công")
    void TC_FR_01_004() {
        when(userRepo.findByEmail("test@example.com")).thenReturn(Optional.of(validUser));
        when(jwt.generateAccessToken(validUser)).thenReturn("access-token");
        when(jwt.generateRefreshToken("test@example.com")).thenReturn("refresh-token");

        TokenResponse result = authService.login("test@example.com", "password123", null);

        assertThat(result.getAccessToken()).isEqualTo("access-token");
        assertThat(result.getRefreshToken()).isEqualTo("refresh-token");
        verify(authManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Đăng nhập thất bại do sai mật khẩu (BadCredentials)
     * Input: email = "test@example.com", password = "wrong", clientView = null
     * Expected Output: ResponseStatusException với status 401 UNAUTHORIZED
     * Notes: Kiểm tra nhánh catch BadCredentialsException
     */
    @Test
    @DisplayName("TC-FR-01-006: Sai mật khẩu → 401")
    void TC_FR_01_006() {
        doThrow(new BadCredentialsException("bad"))
                .when(authManager).authenticate(any());

        assertThatThrownBy(() -> authService.login("test@example.com", "wrong", null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(rse.getReason()).isEqualTo("Bad credentials");
                });
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Đăng nhập thất bại do tài khoản bị vô hiệu hóa
     * Input: email = "disabled@example.com", password = "password", clientView = null
     * Expected Output: ResponseStatusException 401 "User disabled"
     * Notes: Kiểm tra nhánh catch DisabledException
     */
    @Test
    @DisplayName("TC-FR-01-007: Tài khoản bị disabled → 401")
    void TC_FR_01_007() {
        doThrow(new DisabledException("disabled"))
                .when(authManager).authenticate(any());

        assertThatThrownBy(() -> authService.login("disabled@example.com", "password", null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(rse.getReason()).isEqualTo("User disabled");
                });
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Đăng nhập thất bại do tài khoản bị khóa
     * Input: email = "locked@example.com", password = "password", clientView = null
     * Expected Output: ResponseStatusException 401 "User locked"
     * Notes: Kiểm tra nhánh catch LockedException
     */
    @Test
    @DisplayName("TC-FR-01-010: Tài khoản bị locked → 401")
    void TC_FR_01_010() {
        doThrow(new LockedException("locked"))
                .when(authManager).authenticate(any());

        assertThatThrownBy(() -> authService.login("locked@example.com", "password", null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(rse.getReason()).isEqualTo("User locked");
                });
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Đăng nhập ADMIN view thành công cho user có role ADMIN
     * Input: email = "admin@example.com", password = "password", clientView = "ADMIN"
     * Expected Output: TokenResponse hợp lệ
     * Notes: Kiểm tra nhánh clientView == "ADMIN" và role == ADMIN
     */
    @Test
    @DisplayName("TC-FR-01-013: ADMIN view + role ADMIN → thành công")
    void TC_FR_01_013() {
        User adminUser = User.builder().id(2L).email("admin@example.com")
                .role(adminRole).enabled(true).build();

        when(userRepo.findByEmail("admin@example.com")).thenReturn(Optional.of(adminUser));
        when(jwt.generateAccessToken(adminUser)).thenReturn("admin-access");
        when(jwt.generateRefreshToken("admin@example.com")).thenReturn("admin-refresh");

        TokenResponse result = authService.login("admin@example.com", "password", "ADMIN");

        assertThat(result.getAccessToken()).isEqualTo("admin-access");
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Đăng nhập ADMIN view thất bại cho user không phải ADMIN
     * Input: email = "test@example.com", password = "password", clientView = "ADMIN"
     * Expected Output: ResponseStatusException 403 FORBIDDEN
     * Notes: Kiểm tra nhánh clientView == "ADMIN" nhưng role != ADMIN
     */
    @Test
    @DisplayName("TC-FR-01-014: ADMIN view + role USER → 403")
    void TC_FR_01_014() {
        when(userRepo.findByEmail("test@example.com")).thenReturn(Optional.of(validUser));

        assertThatThrownBy(() -> authService.login("test@example.com", "password", "ADMIN"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                });
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Email được chuẩn hóa (trim + lowercase) trước khi xử lý
     * Input: email = " TEST@Example.COM ", password = "password", clientView = null
     * Expected Output: Hệ thống tìm user theo "test@example.com"
     * Notes: Kiểm tra hàm normalize hoạt động đúng
     */
    @Test
    @DisplayName("TC-FR-01-015: Email được normalize trước khi xử lý")
    void TC_FR_01_015() {
        when(userRepo.findByEmail("test@example.com")).thenReturn(Optional.of(validUser));
        when(jwt.generateAccessToken(any())).thenReturn("tok");
        when(jwt.generateRefreshToken(any())).thenReturn("ref");

        authService.login(" TEST@Example.COM ", "password", null);

        verify(userRepo).findByEmail("test@example.com");
    }

    // ==================== REFRESH ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Refresh token thành công
     * Input: refreshToken hợp lệ
     * Expected Output: TokenResponse với accessToken mới, refreshToken giữ nguyên
     * Notes: Happy path
     */
    @Test
    @DisplayName("TC-FR-02-001: Refresh thành công")
    void TC_FR_02_001() {
        when(jwt.isValid("valid-refresh")).thenReturn(true);
        when(jwt.extractUsername("valid-refresh")).thenReturn("test@example.com");
        when(userRepo.findByEmail("test@example.com")).thenReturn(Optional.of(validUser));
        when(jwt.generateAccessToken(validUser)).thenReturn("new-access");

        TokenResponse result = authService.refresh("valid-refresh");

        assertThat(result.getAccessToken()).isEqualTo("new-access");
        assertThat(result.getRefreshToken()).isEqualTo("valid-refresh");
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Refresh thất bại khi token null
     * Input: refreshToken = null
     * Expected Output: ResponseStatusException 400 BAD_REQUEST
     * Notes: Kiểm tra nhánh refreshToken == null
     */
    @Test
    @DisplayName("TC-FR-02-002: Token null → 400")
    void TC_FR_02_002() {
        assertThatThrownBy(() -> authService.refresh(null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Refresh thất bại khi token không hợp lệ
     * Input: refreshToken = "invalid-token"
     * Expected Output: ResponseStatusException 401 UNAUTHORIZED
     * Notes: Kiểm tra nhánh jwt.isValid == false
     */
    @Test
    @DisplayName("TC-FR-02-003: Token không hợp lệ → 401")
    void TC_FR_02_003() {
        when(jwt.isValid("invalid-token")).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh("invalid-token"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                });
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Refresh thất bại khi user không tồn tại trong DB
     * Input: refreshToken hợp lệ nhưng username không có trong DB
     * Expected Output: ResponseStatusException 401 UNAUTHORIZED
     * Notes: Kiểm tra nhánh userRepo.findByEmail trả về empty
     */
    @Test
    @DisplayName("TC-FR-02-004: User không tồn tại → 401")
    void TC_FR_02_004() {
        when(jwt.isValid("valid-refresh")).thenReturn(true);
        when(jwt.extractUsername("valid-refresh")).thenReturn("ghost@example.com");
        when(userRepo.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("valid-refresh"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                });
    }

    // ==================== REGISTER START ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Gửi OTP đăng ký thành công khi email chưa tồn tại
     * Input: RegisterStartRequest với email mới
     * Expected Output: Không throw exception, gọi otpService.sendOtp
     * Notes: Happy path
     */
    @Test
    @DisplayName("TC-FR-02-005: Gửi OTP thành công")
    void TC_FR_02_005() {
        RegisterStartRequest req = new RegisterStartRequest();
        req.setEmail("new@example.com");
        req.setPassword("password123");
        req.setName("New User");

        when(userRepo.existsByEmail("new@example.com")).thenReturn(false);

        authService.registerStart(req);

        verify(otpService).sendOtp(eq("new@example.com"), eq(OtpType.REGISTER), any());
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Gửi OTP thất bại khi email đã tồn tại
     * Input: RegisterStartRequest với email đã đăng ký
     * Expected Output: ResponseStatusException 409 CONFLICT
     * Notes: Kiểm tra nhánh existsByEmail == true
     */
    @Test
    @DisplayName("TC-FR-02-006: Email đã tồn tại → 409")
    void TC_FR_02_006() {
        RegisterStartRequest req = new RegisterStartRequest();
        req.setEmail("existing@example.com");
        req.setPassword("password123");
        req.setName("Existing User");

        when(userRepo.existsByEmail("existing@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.registerStart(req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                });

        verify(otpService, never()).sendOtp(any(), any(), any());
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Payload gửi OTP chứa đầy đủ thông tin đăng ký
     * Input: RegisterStartRequest đầy đủ fields
     * Expected Output: OTP được gửi với payload chứa các field cần thiết
     * Notes: Kiểm tra payload được truyền đúng qua otpService.sendOtp
     */
    @Test
    @DisplayName("TC-FR-02-007: Payload chứa đầy đủ thông tin")
    void TC_FR_02_007() {
        RegisterStartRequest req = new RegisterStartRequest();
        req.setEmail("new@example.com");
        req.setPassword("password123");
        req.setName("Full Name");
        req.setGender("Male");
        req.setPhoneNumber("0123456789");
        req.setDateOfBirth(LocalDate.of(1990, 1, 1));
        req.setCountryId(1L);

        when(userRepo.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");

        authService.registerStart(req);

        verify(otpService).sendOtp(eq("new@example.com"), eq(OtpType.REGISTER), argThat(opt -> {
            assertThat(opt).isPresent();
            Map<String, Object> payload = opt.get();
            assertThat(payload.get("name")).isEqualTo("Full Name");
            assertThat(payload.get("gender")).isEqualTo("Male");
            return true;
        }));
    }

    // ==================== REGISTER VERIFY ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Xác minh OTP đăng ký thành công và tạo user mới
     * Input: OtpVerifyRequest hợp lệ
     * Expected Output: TokenResponse chứa token, user được lưu vào DB
     * Notes: CheckDB – user mới được tạo với role USER
     */
    @Test
    @DisplayName("TC-FR-02-008: Xác minh OTP thành công")
    void TC_FR_02_008() throws Exception {
        OtpVerifyRequest req = new OtpVerifyRequest();
        req.setEmail("new@example.com");
        req.setCode("123456");

        Map<String, Object> payloadMap = Map.of(
                "passwordHash", "encodedPwd",
                "name", "New User",
                "gender", "Male",
                "phoneNumber", "0123456789",
                "dateOfBirth", "1990-01-01",
                "countryId", 1
        );
        String payloadJson = objectMapper.writeValueAsString(payloadMap);

        EmailOtp otp = EmailOtp.builder()
                .email("new@example.com")
                .code("123456")
                .payloadJson(payloadJson)
                .build();

        when(otpService.verify("new@example.com", OtpType.REGISTER, "123456")).thenReturn(otp);
        when(userRepo.existsByEmail("new@example.com")).thenReturn(false);
        when(roleRepo.findByName("USER")).thenReturn(Optional.of(userRole));

        Country country = new Country(1L, "Vietnam", "VN");
        when(countryRepo.findById(1L)).thenReturn(Optional.of(country));
        when(userRepo.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(10L);
            return u;
        });
        when(jwt.generateAccessToken(any(User.class))).thenReturn("access");
        when(jwt.generateRefreshToken(any())).thenReturn("refresh");

        TokenResponse result = authService.registerVerify(req);

        assertThat(result.getAccessToken()).isEqualTo("access");
        assertThat(result.getRefreshToken()).isEqualTo("refresh");
        verify(userRepo).save(any(User.class));
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Xác minh OTP thất bại khi email đã được đăng ký
     * Input: OtpVerifyRequest hợp lệ nhưng email đã tồn tại trong DB
     * Expected Output: ResponseStatusException 409 CONFLICT
     * Notes: Kiểm tra nhánh existsByEmail == true sau verify OTP
     */
    @Test
    @DisplayName("TC-FR-02-010: Email đã tồn tại → 409")
    void TC_FR_02_010() throws Exception {
        OtpVerifyRequest req = new OtpVerifyRequest();
        req.setEmail("existing@example.com");
        req.setCode("123456");

        EmailOtp otp = EmailOtp.builder()
                .payloadJson(objectMapper.writeValueAsString(Map.of("name", "X", "passwordHash", "h")))
                .build();

        when(otpService.verify("existing@example.com", OtpType.REGISTER, "123456")).thenReturn(otp);
        when(userRepo.existsByEmail("existing@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.registerVerify(req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                });
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Xác minh đăng ký khi không truyền countryId (null)
     * Input: OTP payload không có countryId
     * Expected Output: User được tạo thành công với country = null
     * Notes: Kiểm tra nhánh countryIdObj == null
     */
    @Test
    @DisplayName("TC-FR-02-011: Không có countryId → country = null")
    void TC_FR_02_011() throws Exception {
        OtpVerifyRequest req = new OtpVerifyRequest();
        req.setEmail("nocountry@example.com");
        req.setCode("111111");

        Map<String, Object> payload = Map.of("name", "No Country", "passwordHash", "h");
        EmailOtp otp = EmailOtp.builder()
                .payloadJson(objectMapper.writeValueAsString(payload))
                .build();

        when(otpService.verify("nocountry@example.com", OtpType.REGISTER, "111111")).thenReturn(otp);
        when(userRepo.existsByEmail("nocountry@example.com")).thenReturn(false);
        when(roleRepo.findByName("USER")).thenReturn(Optional.of(userRole));
        when(userRepo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwt.generateAccessToken(any())).thenReturn("a");
        when(jwt.generateRefreshToken(any())).thenReturn("r");

        TokenResponse result = authService.registerVerify(req);

        assertThat(result).isNotNull();
        verify(countryRepo, never()).findById(anyLong());
    }

    // ==================== FORGOT PASSWORD ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Gửi OTP reset password thành công khi email tồn tại
     * Input: ForgotPasswordRequest với email đã đăng ký
     * Expected Output: Gọi otpService.sendOtp loại RESET_PASSWORD
     * Notes: Happy path
     */
    @Test
    @DisplayName("TC-FR-02-012: Email tồn tại → gửi OTP thành công")
    void TC_FR_02_012() {
        ForgotPasswordRequest req = new ForgotPasswordRequest();
        req.setEmail("test@example.com");

        when(userRepo.existsByEmail("test@example.com")).thenReturn(true);

        authService.forgotPassword(req);

        verify(otpService).sendOtp(eq("test@example.com"), eq(OtpType.RESET_PASSWORD), eq(Optional.empty()));
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Gửi OTP thất bại khi email không tồn tại
     * Input: ForgotPasswordRequest với email chưa đăng ký
     * Expected Output: ResponseStatusException 404 NOT_FOUND
     * Notes: Kiểm tra nhánh existsByEmail == false
     */
    @Test
    @DisplayName("TC-FR-02-013: Email không tồn tại → 404")
    void TC_FR_02_013() {
        ForgotPasswordRequest req = new ForgotPasswordRequest();
        req.setEmail("ghost@example.com");

        when(userRepo.existsByEmail("ghost@example.com")).thenReturn(false);

        assertThatThrownBy(() -> authService.forgotPassword(req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                });
    }

    // ==================== RESET PASSWORD ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Đặt lại mật khẩu thành công
     * Input: ResetPasswordRequest hợp lệ
     * Expected Output: Mật khẩu user được cập nhật và lưu vào DB
     * Notes: CheckDB – password phải được encode và lưu lại
     */
    @Test
    @DisplayName("TC-FR-02-014: Đổi mật khẩu thành công")
    void TC_FR_02_014() {
        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setEmail("test@example.com");
        req.setCode("654321");
        req.setNewPassword("newPassword123");

        when(userRepo.findByEmail("test@example.com")).thenReturn(Optional.of(validUser));
        when(passwordEncoder.encode("newPassword123")).thenReturn("newEncodedPwd");

        authService.resetPassword(req);

        assertThat(validUser.getPassword()).isEqualTo("newEncodedPwd");
        verify(userRepo).save(validUser);
        verify(otpService).verify("test@example.com", OtpType.RESET_PASSWORD, "654321");
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Đặt lại mật khẩu thất bại khi user không tồn tại
     * Input: ResetPasswordRequest với email không có trong DB
     * Expected Output: ResponseStatusException 404 NOT_FOUND
     * Notes: Kiểm tra nhánh userRepo.findByEmail trả về empty
     */
    @Test
    @DisplayName("TC-FR-02-015: User không tồn tại → 404")
    void TC_FR_02_015() {
        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setEmail("ghost@example.com");
        req.setCode("654321");
        req.setNewPassword("newPwd");

        when(userRepo.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resetPassword(req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                });
    }

    // ==================== RESEND OTP ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Gửi lại OTP đăng ký thành công
     * Input: OtpResendRequest type=REGISTER, email chưa đăng ký
     * Expected Output: otpService.sendOtp được gọi
     * Notes: Kiểm tra nhánh type == REGISTER, email chưa tồn tại
     */
    @Test
    @DisplayName("TC-FR-02-016: Gửi lại OTP REGISTER thành công")
    void TC_FR_02_016() {
        OtpResendRequest req = new OtpResendRequest();
        req.setEmail("new@example.com");
        req.setType(OtpType.REGISTER);

        when(userRepo.existsByEmail("new@example.com")).thenReturn(false);

        authService.resendOtp(req);

        verify(otpService).sendOtp(eq("new@example.com"), eq(OtpType.REGISTER), eq(Optional.empty()));
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Gửi lại OTP REGISTER thất bại khi email đã tồn tại
     * Input: OtpResendRequest type=REGISTER, email đã đăng ký
     * Expected Output: ResponseStatusException 409 CONFLICT
     * Notes: Kiểm tra nhánh REGISTER + existsByEmail == true
     */
    @Test
    @DisplayName("TC-FR-02-022: Resend REGISTER, email đã tồn tại → 409")
    void TC_FR_02_022() {
        OtpResendRequest req = new OtpResendRequest();
        req.setEmail("existing@example.com");
        req.setType(OtpType.REGISTER);

        when(userRepo.existsByEmail("existing@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.resendOtp(req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                });
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Gửi lại OTP RESET_PASSWORD thành công
     * Input: OtpResendRequest type=RESET_PASSWORD, email tồn tại
     * Expected Output: otpService.sendOtp được gọi
     * Notes: Kiểm tra nhánh type == RESET_PASSWORD, email tồn tại
     */
    @Test
    @DisplayName("TC-FR-02-026: Gửi lại OTP RESET_PASSWORD thành công")
    void TC_FR_02_026() {
        OtpResendRequest req = new OtpResendRequest();
        req.setEmail("test@example.com");
        req.setType(OtpType.RESET_PASSWORD);

        when(userRepo.existsByEmail("test@example.com")).thenReturn(true);

        authService.resendOtp(req);

        verify(otpService).sendOtp(eq("test@example.com"), eq(OtpType.RESET_PASSWORD), eq(Optional.empty()));
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Gửi lại OTP RESET_PASSWORD thất bại khi email không tồn tại
     * Input: OtpResendRequest type=RESET_PASSWORD, email không có
     * Expected Output: ResponseStatusException 404 NOT_FOUND
     * Notes: Kiểm tra nhánh RESET_PASSWORD + existsByEmail == false
     */
    @Test
    @DisplayName("TC-FR-02-033: Resend RESET_PASSWORD, email không tồn tại → 404")
    void TC_FR_02_033() {
        OtpResendRequest req = new OtpResendRequest();
        req.setEmail("ghost@example.com");
        req.setType(OtpType.RESET_PASSWORD);

        when(userRepo.existsByEmail("ghost@example.com")).thenReturn(false);

        assertThatThrownBy(() -> authService.resendOtp(req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                });
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Gửi lại OTP thất bại khi OtpType = null
     * Input: OtpResendRequest type=null
     * Expected Output: ResponseStatusException 400 BAD_REQUEST
     * Notes: Kiểm tra nhánh type == null
     */
    @Test
    @DisplayName("TC-FR-02-037: OtpType null → 400")
    void TC_FR_02_037() {
        OtpResendRequest req = new OtpResendRequest();
        req.setEmail("test@example.com");
        req.setType(null);

        assertThatThrownBy(() -> authService.resendOtp(req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    // ==================== GET BY EMAIL OR THROW ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Tìm user theo email thành công
     * Input: email hợp lệ tồn tại trong DB
     * Expected Output: User entity được trả về
     * Notes: Happy path
     */
    @Test
    @DisplayName("TC-FR-02-039: Tìm user thành công")
    void TC_FR_02_039() {
        when(userRepo.findByEmail("test@example.com")).thenReturn(Optional.of(validUser));

        User result = authService.getByEmailOrThrow("test@example.com");

        assertThat(result).isEqualTo(validUser);
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Tìm user thất bại khi email không tồn tại
     * Input: email không có trong DB
     * Expected Output: ResponseStatusException 404
     * Notes: Kiểm tra nhánh orElseThrow
     */
    @Test
    @DisplayName("TC-FR-02-058: Email không tồn tại → 404")
    void TC_FR_02_058() {
        when(userRepo.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getByEmailOrThrow("nonexistent@example.com"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                });
    }
}

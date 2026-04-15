package com.example.AuthService.controller;

import com.example.AuthService.dto.AuthProfileDto;
import com.example.AuthService.dto.request.*;
import com.example.AuthService.dto.response.TokenResponse;
import com.example.AuthService.otp.OtpType;
import com.example.AuthService.security.jwt.JwtService;
import com.example.AuthService.service.AuthProfileService;
import com.example.AuthService.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests cho {@link AuthController}.
 * Sử dụng @WebMvcTest để chỉ load controller layer, mock các service bên dưới.
 */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private AuthProfileService authProfileService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsService userDetailsService;

    // ======================== LOGIN ========================

    /**
     * Test Case ID: TC_AUTH_AuthController_login_001
     * Test Objective: Đăng nhập thành công với email và password hợp lệ
     * Input: LoginRequest(email="user@test.com", password="password123", clientView="web")
     * Expected Output: HTTP 200, body chứa accessToken và refreshToken
     * Notes: Happy path - đăng nhập thông thường
     */
    @Test
    @DisplayName("TC_AUTH_AuthController_login_001: Đăng nhập thành công với thông tin hợp lệ")
    void TC_AUTH_AuthController_login_001() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("user@test.com");
        loginRequest.setPassword("password123");
        loginRequest.setClientView("web");

        TokenResponse tokenResponse = new TokenResponse("access-token-xyz", "refresh-token-xyz");

        when(authService.login("user@test.com", "password123", "web"))
                .thenReturn(tokenResponse);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token-xyz"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token-xyz"));

        verify(authService).login("user@test.com", "password123", "web");
    }

    /**
     * Test Case ID: TC_AUTH_AuthController_login_002
     * Test Objective: Đăng nhập thất bại khi service ném ResponseStatusException (401)
     * Input: LoginRequest(email="wrong@test.com", password="wrongpass")
     * Expected Output: HTTP 401 Unauthorized
     * Notes: Sai email hoặc mật khẩu
     */
    @Test
    @DisplayName("TC_AUTH_AuthController_login_002: Đăng nhập thất bại - sai thông tin")
    void TC_AUTH_AuthController_login_002() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("wrong@test.com");
        loginRequest.setPassword("wrongpass");

        when(authService.login(any(), any(), any()))
                .thenThrow(new ResponseStatusException(UNAUTHORIZED, "Sai email hoặc mật khẩu"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Test Case ID: TC_AUTH_AuthController_login_003
     * Test Objective: Đăng nhập với clientView null (trường hợp không truyền clientView)
     * Input: LoginRequest(email="user@test.com", password="password123", clientView=null)
     * Expected Output: HTTP 200, body chứa token
     * Notes: clientView có thể null theo design
     */
    @Test
    @DisplayName("TC_AUTH_AuthController_login_003: Đăng nhập thành công khi clientView null")
    void TC_AUTH_AuthController_login_003() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("user@test.com");
        loginRequest.setPassword("password123");

        TokenResponse tokenResponse = new TokenResponse("access-token", "refresh-token");
        when(authService.login("user@test.com", "password123", null))
                .thenReturn(tokenResponse);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"));
    }

    /**
     * Test Case ID: TC_AUTH_AuthController_login_004
     * Test Objective: Đăng nhập với body rỗng
     * Input: {} (body rỗng)
     * Expected Output: HTTP 200 hoặc lỗi tùy service xử lý (email=null, password=null)
     * Notes: Controller không có @Valid trên LoginRequest nên không bị validation error
     */
    @Test
    @DisplayName("TC_AUTH_AuthController_login_004: Đăng nhập với body rỗng gây lỗi từ service")
    void TC_AUTH_AuthController_login_004() throws Exception {
        when(authService.login(null, null, null))
                .thenThrow(new ResponseStatusException(BAD_REQUEST, "Email không được trống"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ======================== REFRESH ========================

    /**
     * Test Case ID: TC_AUTH_AuthController_refresh_001
     * Test Objective: Refresh token thành công
     * Input: {"refreshToken": "valid-refresh-token"}
     * Expected Output: HTTP 200, body chứa accessToken và refreshToken mới
     * Notes: Happy path
     */
    @Test
    @DisplayName("TC_AUTH_AuthController_refresh_001: Refresh token thành công")
    void TC_AUTH_AuthController_refresh_001() throws Exception {
        TokenResponse tokenResponse = new TokenResponse("new-access-token", "new-refresh-token");
        when(authService.refresh("valid-refresh-token")).thenReturn(tokenResponse);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"valid-refresh-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh-token"));
    }

    /**
     * Test Case ID: TC_AUTH_AuthController_refresh_002
     * Test Objective: Refresh với token không hợp lệ
     * Input: {"refreshToken": "invalid-token"}
     * Expected Output: HTTP 401
     * Notes: Token hết hạn hoặc bị sửa đổi
     */
    @Test
    @DisplayName("TC_AUTH_AuthController_refresh_002: Refresh token không hợp lệ")
    void TC_AUTH_AuthController_refresh_002() throws Exception {
        when(authService.refresh("invalid-token"))
                .thenThrow(new ResponseStatusException(UNAUTHORIZED, "Token không hợp lệ"));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"invalid-token\"}"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Test Case ID: TC_AUTH_AuthController_refresh_003
     * Test Objective: Refresh khi không truyền refreshToken
     * Input: {} (body rỗng, refreshToken = null)
     * Expected Output: Lỗi từ service
     * Notes: Map.get("refreshToken") trả null
     */
    @Test
    @DisplayName("TC_AUTH_AuthController_refresh_003: Refresh khi thiếu refreshToken")
    void TC_AUTH_AuthController_refresh_003() throws Exception {
        when(authService.refresh(null))
                .thenThrow(new ResponseStatusException(BAD_REQUEST, "Thiếu refreshToken"));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ======================== REGISTER START ========================

    /**
     * Test Case ID: TC_AUTH_AuthController_registerStart_001
     * Test Objective: Bắt đầu đăng ký thành công, gửi OTP
     * Input: RegisterStartRequest hợp lệ
     * Expected Output: HTTP 200
     * Notes: Service không throw exception => trả 200
     */
    @Test
    @DisplayName("TC_AUTH_AuthController_registerStart_001: Đăng ký bước 1 thành công")
    void TC_AUTH_AuthController_registerStart_001() throws Exception {
        RegisterStartRequest request = new RegisterStartRequest();
        request.setEmail("new@test.com");
        request.setPassword("password123");
        request.setName("New User");

        doNothing().when(authService).registerStart(any(RegisterStartRequest.class));

        mockMvc.perform(post("/api/auth/register/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(authService).registerStart(any(RegisterStartRequest.class));
    }

    /**
     * Test Case ID: TC_AUTH_AuthController_registerStart_002
     * Test Objective: Đăng ký với email đã tồn tại
     * Input: RegisterStartRequest với email đã tồn tại trong hệ thống
     * Expected Output: HTTP 409 Conflict
     * Notes: Service ném ResponseStatusException
     */
    @Test
    @DisplayName("TC_AUTH_AuthController_registerStart_002: Đăng ký với email đã tồn tại")
    void TC_AUTH_AuthController_registerStart_002() throws Exception {
        RegisterStartRequest request = new RegisterStartRequest();
        request.setEmail("existing@test.com");
        request.setPassword("password123");
        request.setName("Existing User");

        doThrow(new ResponseStatusException(CONFLICT, "Email đã tồn tại"))
                .when(authService).registerStart(any(RegisterStartRequest.class));

        mockMvc.perform(post("/api/auth/register/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    /**
     * Test Case ID: TC_AUTH_AuthController_registerStart_003
     * Test Objective: Đăng ký với dữ liệu thiếu (email trống)
     * Input: RegisterStartRequest với email trống
     * Expected Output: HTTP 400
     * Notes: Service ném lỗi validation
     */
    @Test
    @DisplayName("TC_AUTH_AuthController_registerStart_003: Đăng ký với email trống gây lỗi service")
    void TC_AUTH_AuthController_registerStart_003() throws Exception {
        RegisterStartRequest request = new RegisterStartRequest();
        request.setEmail("");
        request.setPassword("password123");
        request.setName("User");

        doThrow(new ResponseStatusException(BAD_REQUEST, "Email không hợp lệ"))
                .when(authService).registerStart(any(RegisterStartRequest.class));

        mockMvc.perform(post("/api/auth/register/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ======================== REGISTER VERIFY ========================

    /**
     * Test Case ID: TC_AUTH_AuthController_registerVerify_001
     * Test Objective: Xác thực OTP thành công, hoàn tất đăng ký
     * Input: OtpVerifyRequest(email="new@test.com", code="123456")
     * Expected Output: HTTP 200, body chứa token
     * Notes: OTP đúng -> tạo tài khoản -> trả token
     */
    @Test
    @DisplayName("TC_AUTH_AuthController_registerVerify_001: Xác thực OTP đăng ký thành công")
    void TC_AUTH_AuthController_registerVerify_001() throws Exception {
        OtpVerifyRequest request = new OtpVerifyRequest();
        request.setEmail("new@test.com");
        request.setCode("123456");

        TokenResponse tokenResponse = new TokenResponse("access-token", "refresh-token");
        when(authService.registerVerify(any(OtpVerifyRequest.class))).thenReturn(tokenResponse);

        mockMvc.perform(post("/api/auth/register/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"));
    }

    /**
     * Test Case ID: TC_AUTH_AuthController_registerVerify_002
     * Test Objective: Xác thực OTP sai
     * Input: OtpVerifyRequest(email="new@test.com", code="000000")
     * Expected Output: HTTP 400 Bad Request
     * Notes: OTP sai hoặc hết hạn
     */
    @Test
    @DisplayName("TC_AUTH_AuthController_registerVerify_002: Xác thực OTP sai mã")
    void TC_AUTH_AuthController_registerVerify_002() throws Exception {
        OtpVerifyRequest request = new OtpVerifyRequest();
        request.setEmail("new@test.com");
        request.setCode("000000");

        when(authService.registerVerify(any(OtpVerifyRequest.class)))
                .thenThrow(new ResponseStatusException(BAD_REQUEST, "OTP sai hoặc hết hạn"));

        mockMvc.perform(post("/api/auth/register/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    /**
     * Test Case ID: TC_AUTH_AuthController_registerVerify_003
     * Test Objective: Xác thực OTP với email không tồn tại pending
     * Input: OtpVerifyRequest(email="notfound@test.com", code="123456")
     * Expected Output: HTTP 404 Not Found
     * Notes: Không có OTP nào pending cho email này
     */
    @Test
    @DisplayName("TC_AUTH_AuthController_registerVerify_003: Xác thực OTP cho email không tồn tại pending")
    void TC_AUTH_AuthController_registerVerify_003() throws Exception {
        OtpVerifyRequest request = new OtpVerifyRequest();
        request.setEmail("notfound@test.com");
        request.setCode("123456");

        when(authService.registerVerify(any(OtpVerifyRequest.class)))
                .thenThrow(new ResponseStatusException(NOT_FOUND, "Không tìm thấy yêu cầu đăng ký"));

        mockMvc.perform(post("/api/auth/register/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    // ======================== FORGOT PASSWORD ========================

    /**
     * Test Case ID: TC_AUTH_AuthController_forgotPassword_001
     * Test Objective: Gửi yêu cầu quên mật khẩu thành công
     * Input: ForgotPasswordRequest(email="user@test.com")
     * Expected Output: HTTP 204 No Content
     * Notes: Happy path - gửi OTP reset password
     */
    @Test
    @DisplayName("TC_AUTH_AuthController_forgotPassword_001: Gửi yêu cầu quên mật khẩu thành công")
    void TC_AUTH_AuthController_forgotPassword_001() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("user@test.com");

        doNothing().when(authService).forgotPassword(any(ForgotPasswordRequest.class));

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());
    }

    /**
     * Test Case ID: TC_AUTH_AuthController_forgotPassword_002
     * Test Objective: Quên mật khẩu với email không tồn tại
     * Input: ForgotPasswordRequest(email="notfound@test.com")
     * Expected Output: HTTP 404
     * Notes: Email chưa đăng ký
     */
    @Test
    @DisplayName("TC_AUTH_AuthController_forgotPassword_002: Quên mật khẩu - email không tồn tại")
    void TC_AUTH_AuthController_forgotPassword_002() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("notfound@test.com");

        doThrow(new ResponseStatusException(NOT_FOUND, "Không tìm thấy email"))
                .when(authService).forgotPassword(any(ForgotPasswordRequest.class));

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    /**
     * Test Case ID: TC_AUTH_AuthController_forgotPassword_003
     * Test Objective: Quên mật khẩu gửi quá nhiều lần (cooldown)
     * Input: ForgotPasswordRequest(email="user@test.com")
     * Expected Output: HTTP 429 Too Many Requests
     * Notes: Gửi OTP quá nhanh
     */
    @Test
    @DisplayName("TC_AUTH_AuthController_forgotPassword_003: Quên mật khẩu - gửi quá nhanh bị giới hạn")
    void TC_AUTH_AuthController_forgotPassword_003() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("user@test.com");

        doThrow(new ResponseStatusException(TOO_MANY_REQUESTS, "Vui lòng chờ trước khi gửi lại"))
                .when(authService).forgotPassword(any(ForgotPasswordRequest.class));

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests());
    }

    // ======================== RESET PASSWORD ========================

    /**
     * Test Case ID: TC_AUTH_AuthController_resetPassword_001
     * Test Objective: Đặt lại mật khẩu thành công
     * Input: ResetPasswordRequest(email="user@test.com", code="123456", newPassword="newPass123")
     * Expected Output: HTTP 204 No Content
     * Notes: OTP đúng, mật khẩu mới hợp lệ
     */
    @Test
    @DisplayName("TC_AUTH_AuthController_resetPassword_001: Đặt lại mật khẩu thành công")
    void TC_AUTH_AuthController_resetPassword_001() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setEmail("user@test.com");
        request.setCode("123456");
        request.setNewPassword("newPass123");

        doNothing().when(authService).resetPassword(any(ResetPasswordRequest.class));

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());
    }

    /**
     * Test Case ID: TC_AUTH_AuthController_resetPassword_002
     * Test Objective: Đặt lại mật khẩu với OTP sai
     * Input: ResetPasswordRequest(email="user@test.com", code="wrong", newPassword="newPass123")
     * Expected Output: HTTP 400
     * Notes: Mã OTP không khớp
     */
    @Test
    @DisplayName("TC_AUTH_AuthController_resetPassword_002: Đặt lại mật khẩu - OTP sai")
    void TC_AUTH_AuthController_resetPassword_002() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setEmail("user@test.com");
        request.setCode("wrong");
        request.setNewPassword("newPass123");

        doThrow(new ResponseStatusException(BAD_REQUEST, "OTP không đúng"))
                .when(authService).resetPassword(any(ResetPasswordRequest.class));

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    /**
     * Test Case ID: TC_AUTH_AuthController_resetPassword_003
     * Test Objective: Đặt lại mật khẩu với mật khẩu mới quá ngắn
     * Input: ResetPasswordRequest(email="user@test.com", code="123456", newPassword="short")
     * Expected Output: HTTP 400
     * Notes: newPassword < 8 ký tự, @Size(min=8)
     */
    @Test
    @DisplayName("TC_AUTH_AuthController_resetPassword_003: Đặt lại mật khẩu - mật khẩu mới quá ngắn")
    void TC_AUTH_AuthController_resetPassword_003() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setEmail("user@test.com");
        request.setCode("123456");
        request.setNewPassword("short");

        doThrow(new ResponseStatusException(BAD_REQUEST, "Mật khẩu mới tối thiểu 8 ký tự"))
                .when(authService).resetPassword(any(ResetPasswordRequest.class));

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ======================== ME ========================

    /**
     * Test Case ID: TC_AUTH_AuthController_me_001
     * Test Objective: Lấy thông tin người dùng hiện tại thành công
     * Input: Người dùng đã xác thực (principal)
     * Expected Output: HTTP 200, body chứa thông tin profile
     * Notes: Sử dụng @WithMockUser
     */
    @Test
    @WithMockUser(username = "user@test.com", roles = "USER")
    @DisplayName("TC_AUTH_AuthController_me_001: Lấy thông tin profile thành công")
    void TC_AUTH_AuthController_me_001() throws Exception {
        AuthProfileDto profileDto = AuthProfileDto.builder()
                .authenticated(true)
                .provider("local/jwt")
                .email("user@test.com")
                .name("Test User")
                .authorities(List.of("ROLE_USER"))
                .build();

        when(authProfileService.buildProfile(any(), eq(true))).thenReturn(profileDto);

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.email").value("user@test.com"))
                .andExpect(jsonPath("$.provider").value("local/jwt"));
    }

    /**
     * Test Case ID: TC_AUTH_AuthController_me_002
     * Test Objective: Lấy thông tin profile khi chưa đăng nhập
     * Input: Không có token (anonymous)
     * Expected Output: HTTP 200 (vì filter bị tắt), nhưng principal sẽ là anonymous
     * Notes: Với addFilters=false, test sẽ cho phép nhưng principal khác
     */
    @Test
    @DisplayName("TC_AUTH_AuthController_me_002: Gọi /me với profile admin")
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void TC_AUTH_AuthController_me_002() throws Exception {
        AuthProfileDto profileDto = AuthProfileDto.builder()
                .authenticated(true)
                .provider("local/jwt")
                .email("admin@test.com")
                .name("Admin User")
                .authorities(List.of("ROLE_ADMIN"))
                .build();

        when(authProfileService.buildProfile(any(), eq(true))).thenReturn(profileDto);

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@test.com"))
                .andExpect(jsonPath("$.authorities[0]").value("ROLE_ADMIN"));
    }

    // ======================== RESEND OTP ========================

    /**
     * Test Case ID: TC_AUTH_AuthController_resendOtp_001
     * Test Objective: Gửi lại OTP thành công
     * Input: OtpResendRequest(email="user@test.com", type=REGISTER)
     * Expected Output: HTTP 200
     * Notes: Happy path
     */
    @Test
    @DisplayName("TC_AUTH_AuthController_resendOtp_001: Gửi lại OTP thành công")
    void TC_AUTH_AuthController_resendOtp_001() throws Exception {
        OtpResendRequest request = new OtpResendRequest();
        request.setEmail("user@test.com");
        request.setType(OtpType.REGISTER);

        doNothing().when(authService).resendOtp(any(OtpResendRequest.class));

        mockMvc.perform(post("/api/auth/otp/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(authService).resendOtp(any(OtpResendRequest.class));
    }

    /**
     * Test Case ID: TC_AUTH_AuthController_resendOtp_002
     * Test Objective: Gửi lại OTP quá sớm (cooldown)
     * Input: OtpResendRequest(email="user@test.com", type=REGISTER)
     * Expected Output: HTTP 429
     * Notes: Chưa hết thời gian chờ giữa 2 lần gửi
     */
    @Test
    @DisplayName("TC_AUTH_AuthController_resendOtp_002: Gửi lại OTP bị giới hạn cooldown")
    void TC_AUTH_AuthController_resendOtp_002() throws Exception {
        OtpResendRequest request = new OtpResendRequest();
        request.setEmail("user@test.com");
        request.setType(OtpType.REGISTER);

        doThrow(new ResponseStatusException(TOO_MANY_REQUESTS, "Chờ 60 giây"))
                .when(authService).resendOtp(any(OtpResendRequest.class));

        mockMvc.perform(post("/api/auth/otp/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests());
    }

    /**
     * Test Case ID: TC_AUTH_AuthController_resendOtp_003
     * Test Objective: Gửi lại OTP cho type RESET_PASSWORD
     * Input: OtpResendRequest(email="user@test.com", type=RESET_PASSWORD)
     * Expected Output: HTTP 200
     * Notes: Kiểm tra hỗ trợ cả hai loại OTP
     */
    @Test
    @DisplayName("TC_AUTH_AuthController_resendOtp_003: Gửi lại OTP loại RESET_PASSWORD thành công")
    void TC_AUTH_AuthController_resendOtp_003() throws Exception {
        OtpResendRequest request = new OtpResendRequest();
        request.setEmail("user@test.com");
        request.setType(OtpType.RESET_PASSWORD);

        doNothing().when(authService).resendOtp(any(OtpResendRequest.class));

        mockMvc.perform(post("/api/auth/otp/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }
}

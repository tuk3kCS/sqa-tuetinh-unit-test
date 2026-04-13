package com.example.AuthService.otp;

import com.example.AuthService.mail.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests cho OtpService – kiểm tra gửi OTP, xác minh OTP, giới hạn lần thử.
 */
@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    @Mock private EmailOtpRepository repo;
    @Mock private OtpProperties props;
    @Mock private EmailService emailService;

    @InjectMocks
    private OtpService otpService;

    @BeforeEach
    void setUp() {
        lenient().when(props.getRegisterTtlSeconds()).thenReturn(300);
        lenient().when(props.getResetTtlSeconds()).thenReturn(300);
        lenient().when(props.getMaxAttempts()).thenReturn(5);
    }

    // ==================== SEND OTP ====================

    /**
     * Test Case ID: TC_AUTH_OtpService_sendOtp_001
     * Test Objective: Gửi OTP mới thành công cho đăng ký
     * Input: email, type = REGISTER, payload hợp lệ
     * Expected Output: OTP mới được lưu vào DB, email được gửi
     * Notes: CheckDB – EmailOtp mới xuất hiện, emailService.send được gọi
     */
    @Test
    @DisplayName("TC_AUTH_OtpService_sendOtp_001: Gửi OTP REGISTER thành công")
    void TC_AUTH_OtpService_sendOtp_001() {
        when(repo.findFirstByEmailAndTypeAndUsedFalseAndExpiresAtAfterOrderByIdDesc(
                eq("test@example.com"), eq(OtpType.REGISTER), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());
        when(repo.save(any(EmailOtp.class))).thenAnswer(inv -> inv.getArgument(0));

        otpService.sendOtp("test@example.com", OtpType.REGISTER,
                Optional.of(Map.of("name", "Test")));

        verify(repo).save(any(EmailOtp.class));
        verify(emailService).send(eq("test@example.com"), anyString(), anyString());
    }

    /**
     * Test Case ID: TC_AUTH_OtpService_sendOtp_002
     * Test Objective: Gửi OTP cho reset password
     * Input: email, type = RESET_PASSWORD, payload empty
     * Expected Output: OTP mới, subject = "Mã đặt lại mật khẩu"
     * Notes: Kiểm tra nhánh type == RESET_PASSWORD → subject đúng
     */
    @Test
    @DisplayName("TC_AUTH_OtpService_sendOtp_002: Gửi OTP RESET_PASSWORD thành công")
    void TC_AUTH_OtpService_sendOtp_002() {
        when(repo.findFirstByEmailAndTypeAndUsedFalseAndExpiresAtAfterOrderByIdDesc(
                anyString(), eq(OtpType.RESET_PASSWORD), any()))
                .thenReturn(Optional.empty());
        when(repo.save(any(EmailOtp.class))).thenAnswer(inv -> inv.getArgument(0));

        otpService.sendOtp("user@test.com", OtpType.RESET_PASSWORD, Optional.empty());

        verify(emailService).send(eq("user@test.com"), eq("Mã đặt lại mật khẩu"), anyString());
    }

    /**
     * Test Case ID: TC_AUTH_OtpService_sendOtp_003
     * Test Objective: Gửi OTP khi đã có OTP cũ chưa dùng → vẫn tạo OTP mới
     * Input: Có OTP cũ trong DB
     * Expected Output: OTP mới vẫn được tạo
     * Notes: Logic hiện tại không block dù có OTP cũ
     */
    @Test
    @DisplayName("TC_AUTH_OtpService_sendOtp_003: Có OTP cũ → vẫn tạo mới")
    void TC_AUTH_OtpService_sendOtp_003() {
        EmailOtp existingOtp = EmailOtp.builder()
                .email("test@example.com").type(OtpType.REGISTER).code("111111")
                .createdAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(2))
                .expiresAt(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(3))
                .used(false).attempts(0).build();

        when(repo.findFirstByEmailAndTypeAndUsedFalseAndExpiresAtAfterOrderByIdDesc(
                anyString(), any(), any())).thenReturn(Optional.of(existingOtp));
        when(repo.save(any(EmailOtp.class))).thenAnswer(inv -> inv.getArgument(0));

        otpService.sendOtp("test@example.com", OtpType.REGISTER, Optional.empty());

        verify(repo).save(any(EmailOtp.class));
    }

    // ==================== VERIFY OTP ====================

    /**
     * Test Case ID: TC_AUTH_OtpService_verify_001
     * Test Objective: Xác minh OTP thành công
     * Input: email, type, code đúng
     * Expected Output: EmailOtp entity với used = true
     * Notes: CheckDB – otp.used = true sau verify
     */
    @Test
    @DisplayName("TC_AUTH_OtpService_verify_001: Xác minh OTP đúng → thành công")
    void TC_AUTH_OtpService_verify_001() {
        EmailOtp otp = EmailOtp.builder()
                .email("test@example.com").type(OtpType.REGISTER).code("123456")
                .expiresAt(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5))
                .used(false).attempts(0).build();

        when(repo.findFirstByEmailAndTypeAndUsedFalseAndExpiresAtAfterOrderByIdDesc(
                eq("test@example.com"), eq(OtpType.REGISTER), any()))
                .thenReturn(Optional.of(otp));
        when(repo.save(any(EmailOtp.class))).thenAnswer(inv -> inv.getArgument(0));

        EmailOtp result = otpService.verify("test@example.com", OtpType.REGISTER, "123456");

        assertThat(result.isUsed()).isTrue();
        verify(repo).save(otp);
    }

    /**
     * Test Case ID: TC_AUTH_OtpService_verify_002
     * Test Objective: Xác minh OTP thất bại khi đã hết hạn hoặc không tồn tại
     * Input: email + type không tìm được OTP hợp lệ
     * Expected Output: IllegalStateException "OTP không tồn tại hoặc đã hết hạn"
     * Notes: Kiểm tra nhánh repo trả về empty
     */
    @Test
    @DisplayName("TC_AUTH_OtpService_verify_002: OTP hết hạn → exception")
    void TC_AUTH_OtpService_verify_002() {
        when(repo.findFirstByEmailAndTypeAndUsedFalseAndExpiresAtAfterOrderByIdDesc(
                anyString(), any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> otpService.verify("test@example.com", OtpType.REGISTER, "000000"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OTP không tồn tại");
    }

    /**
     * Test Case ID: TC_AUTH_OtpService_verify_003
     * Test Objective: Xác minh OTP thất bại khi mã sai
     * Input: code không khớp
     * Expected Output: IllegalArgumentException "OTP không đúng", attempts tăng
     * Notes: CheckDB – attempts tăng 1
     */
    @Test
    @DisplayName("TC_AUTH_OtpService_verify_003: Mã OTP sai → exception, attempts tăng")
    void TC_AUTH_OtpService_verify_003() {
        EmailOtp otp = EmailOtp.builder()
                .email("test@example.com").type(OtpType.REGISTER).code("123456")
                .expiresAt(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5))
                .used(false).attempts(0).build();

        when(repo.findFirstByEmailAndTypeAndUsedFalseAndExpiresAtAfterOrderByIdDesc(
                anyString(), any(), any())).thenReturn(Optional.of(otp));
        when(repo.save(any(EmailOtp.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> otpService.verify("test@example.com", OtpType.REGISTER, "wrong"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OTP không đúng");

        assertThat(otp.getAttempts()).isEqualTo(1);
    }

    /**
     * Test Case ID: TC_AUTH_OtpService_verify_004
     * Test Objective: OTP bị khóa khi vượt quá max attempts
     * Input: attempts = maxAttempts - 1, sai mã lần cuối
     * Expected Output: IllegalArgumentException, otp.used = true (bị khóa)
     * Notes: CheckDB – otp.used = true sau khi quá lần thử
     */
    @Test
    @DisplayName("TC_AUTH_OtpService_verify_004: Quá max attempts → khóa OTP")
    void TC_AUTH_OtpService_verify_004() {
        EmailOtp otp = EmailOtp.builder()
                .email("test@example.com").type(OtpType.REGISTER).code("123456")
                .expiresAt(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(5))
                .used(false).attempts(4).build();

        when(repo.findFirstByEmailAndTypeAndUsedFalseAndExpiresAtAfterOrderByIdDesc(
                anyString(), any(), any())).thenReturn(Optional.of(otp));
        when(repo.save(any(EmailOtp.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> otpService.verify("test@example.com", OtpType.REGISTER, "wrong"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(otp.getAttempts()).isEqualTo(5);
        assertThat(otp.isUsed()).isTrue();
    }
}

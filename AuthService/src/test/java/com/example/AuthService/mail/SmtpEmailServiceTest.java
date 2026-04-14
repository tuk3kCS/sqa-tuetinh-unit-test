package com.example.AuthService.mail;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests cho SmtpEmailService – kiểm tra gửi email qua SMTP.
 */
@ExtendWith(MockitoExtension.class)
class SmtpEmailServiceTest {

    @Mock private JavaMailSender mailSender;
    @Mock private MimeMessage mimeMessage;

    @InjectMocks
    private SmtpEmailService smtpEmailService;

    // ==================== SEND ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Gửi email thành công
     * Input: to, subject, html hợp lệ
     * Expected Output: mailSender.send được gọi
     * Notes: Happy path – email được gửi qua SMTP
     */
    @Test
    @DisplayName("TC-FR-02-001: Gửi email thành công")
    void TC_FR_02_001() {
        ReflectionTestUtils.setField(smtpEmailService, "fromAddress", "noreply@test.com");
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        smtpEmailService.send("user@test.com", "Test Subject", "<p>Hello</p>");

        verify(mailSender).send(mimeMessage);
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Gửi email thất bại → RuntimeException
     * Input: mailSender.send throw exception
     * Expected Output: RuntimeException "Failed to send email"
     * Notes: Kiểm tra nhánh catch Exception
     */
    @Test
    @DisplayName("TC-FR-02-001: Gửi thất bại → RuntimeException")
    void TC_FR_02_001() {
        ReflectionTestUtils.setField(smtpEmailService, "fromAddress", "noreply@test.com");
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new RuntimeException("SMTP error")).when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> smtpEmailService.send("user@test.com", "Subject", "<p>Hi</p>"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to send email");
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Gửi email thất bại khi createMimeMessage lỗi
     * Input: mailSender.createMimeMessage throw exception
     * Expected Output: RuntimeException "Failed to send email"
     * Notes: Kiểm tra lỗi ở bước tạo MimeMessage
     */
    @Test
    @DisplayName("TC-FR-02-001: createMimeMessage lỗi → RuntimeException")
    void TC_FR_02_001() {
        ReflectionTestUtils.setField(smtpEmailService, "fromAddress", "noreply@test.com");
        when(mailSender.createMimeMessage()).thenThrow(new RuntimeException("Connection refused"));

        assertThatThrownBy(() -> smtpEmailService.send("user@test.com", "Subject", "<p>Hi</p>"))
                .isInstanceOf(RuntimeException.class);
    }
}

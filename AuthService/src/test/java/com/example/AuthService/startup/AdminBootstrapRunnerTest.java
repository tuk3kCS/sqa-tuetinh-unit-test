package com.example.AuthService.startup;

import com.example.AuthService.entity.Role;
import com.example.AuthService.entity.User;
import com.example.AuthService.repository.RoleRepository;
import com.example.AuthService.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests cho {@link AdminBootstrapRunner}.
 * Kiểm tra logic khởi tạo admin user khi ứng dụng start.
 */
@ExtendWith(MockitoExtension.class)
class AdminBootstrapRunnerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AdminBootstrapRunner runner;

    private void setFields(boolean enabled, String email, String password, String name) {
        ReflectionTestUtils.setField(runner, "enabled", enabled);
        ReflectionTestUtils.setField(runner, "email", email);
        ReflectionTestUtils.setField(runner, "rawPassword", password);
        ReflectionTestUtils.setField(runner, "displayName", name);
    }

    // ======================== RUN ========================

    /**
     * Test Case ID: TC_AUTH_AdminBootstrapRunner_run_001
     * Test Objective: Tạo admin user thành công khi enabled=true và password hợp lệ
     * Input: enabled=true, email="admin@test.com", password="admin123", name="Admin"
     * Expected Output: userRepository.save() được gọi 1 lần
     * Notes: Happy path - admin chưa tồn tại, tạo mới
     */
    @Test
    @DisplayName("TC_AUTH_AdminBootstrapRunner_run_001: Tạo admin thành công")
    void TC_AUTH_AdminBootstrapRunner_run_001() throws Exception {
        setFields(true, "admin@test.com", "admin123", "Admin");

        when(userRepository.existsByEmail("admin@test.com")).thenReturn(false);
        Role adminRole = Role.builder().id(3L).name("ADMIN").build();
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(adminRole));
        when(passwordEncoder.encode("admin123")).thenReturn("encoded-password");

        runner.run();

        verify(userRepository).save(any(User.class));
    }

    /**
     * Test Case ID: TC_AUTH_AdminBootstrapRunner_run_002
     * Test Objective: Bỏ qua khi enabled=false
     * Input: enabled=false
     * Expected Output: Không gọi bất kỳ repository nào
     * Notes: Feature bị tắt
     */
    @Test
    @DisplayName("TC_AUTH_AdminBootstrapRunner_run_002: Bỏ qua khi disabled")
    void TC_AUTH_AdminBootstrapRunner_run_002() throws Exception {
        setFields(false, "admin@test.com", "admin123", "Admin");

        runner.run();

        verifyNoInteractions(userRepository);
        verifyNoInteractions(roleRepository);
    }

    /**
     * Test Case ID: TC_AUTH_AdminBootstrapRunner_run_003
     * Test Objective: Bỏ qua khi password rỗng
     * Input: enabled=true, password="" (rỗng)
     * Expected Output: Không gọi save
     * Notes: Không có mật khẩu → bỏ qua
     */
    @Test
    @DisplayName("TC_AUTH_AdminBootstrapRunner_run_003: Bỏ qua khi password rỗng")
    void TC_AUTH_AdminBootstrapRunner_run_003() throws Exception {
        setFields(true, "admin@test.com", "", "Admin");

        runner.run();

        verify(userRepository, never()).save(any());
    }

    /**
     * Test Case ID: TC_AUTH_AdminBootstrapRunner_run_004
     * Test Objective: Bỏ qua khi admin email đã tồn tại
     * Input: enabled=true, email đã tồn tại trong DB
     * Expected Output: Không gọi save
     * Notes: Admin đã được tạo trước đó
     */
    @Test
    @DisplayName("TC_AUTH_AdminBootstrapRunner_run_004: Bỏ qua khi admin đã tồn tại")
    void TC_AUTH_AdminBootstrapRunner_run_004() throws Exception {
        setFields(true, "admin@test.com", "admin123", "Admin");

        when(userRepository.existsByEmail("admin@test.com")).thenReturn(true);

        runner.run();

        verify(userRepository, never()).save(any());
    }

    /**
     * Test Case ID: TC_AUTH_AdminBootstrapRunner_run_005
     * Test Objective: Bỏ qua khi password null
     * Input: enabled=true, password=null
     * Expected Output: Không gọi save
     * Notes: Password null → bỏ qua
     */
    @Test
    @DisplayName("TC_AUTH_AdminBootstrapRunner_run_005: Bỏ qua khi password null")
    void TC_AUTH_AdminBootstrapRunner_run_005() throws Exception {
        setFields(true, "admin@test.com", null, "Admin");

        runner.run();

        verify(userRepository, never()).save(any());
    }

    /**
     * Test Case ID: TC_AUTH_AdminBootstrapRunner_run_006
     * Test Objective: Ném IllegalStateException khi ADMIN role không tồn tại
     * Input: enabled=true, password hợp lệ, ADMIN role chưa seed
     * Expected Output: IllegalStateException
     * Notes: RoleSeeder chưa chạy
     */
    @Test
    @DisplayName("TC_AUTH_AdminBootstrapRunner_run_006: Lỗi khi ADMIN role chưa tồn tại")
    void TC_AUTH_AdminBootstrapRunner_run_006() {
        setFields(true, "admin@test.com", "admin123", "Admin");

        when(userRepository.existsByEmail("admin@test.com")).thenReturn(false);
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> runner.run());
    }

    /**
     * Test Case ID: TC_AUTH_AdminBootstrapRunner_run_007
     * Test Objective: Email được chuẩn hóa lowercase và trim trước khi kiểm tra
     * Input: email="  Admin@Test.COM  "
     * Expected Output: Kiểm tra và lưu với email "admin@test.com"
     * Notes: Chuẩn hóa email
     */
    @Test
    @DisplayName("TC_AUTH_AdminBootstrapRunner_run_007: Email được chuẩn hóa")
    void TC_AUTH_AdminBootstrapRunner_run_007() throws Exception {
        setFields(true, "  Admin@Test.COM  ", "admin123", "Admin");

        when(userRepository.existsByEmail("admin@test.com")).thenReturn(false);
        Role adminRole = Role.builder().id(3L).name("ADMIN").build();
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(adminRole));
        when(passwordEncoder.encode("admin123")).thenReturn("encoded");

        runner.run();

        verify(userRepository).existsByEmail("admin@test.com");
        verify(userRepository).save(any(User.class));
    }
}

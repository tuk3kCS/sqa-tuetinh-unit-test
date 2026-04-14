package com.example.AuthService.security;

import com.example.AuthService.entity.Role;
import com.example.AuthService.entity.User;
import com.example.AuthService.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests cho {@link CustomUserDetailsService}.
 * Kiểm tra việc load user từ DB bằng email.
 */
@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    // ======================== LOAD USER BY USERNAME ========================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Load user thành công khi email tồn tại
     * Input: username="test@example.com" (tồn tại trong DB)
     * Expected Output: UserDetails với email đúng
     * Notes: Happy path - user tìm thấy
     */
    @Test
    @DisplayName("TC-FR-02-001: Load user thành công")
    void TC_FR_02_001() {
        Role role = Role.builder().id(1L).name("USER").build();
        User user = User.builder()
                .id(1L).email("test@example.com").name("Test")
                .password("encoded").role(role).enabled(true)
                .build();

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        UserDetails result = customUserDetailsService.loadUserByUsername("test@example.com");

        assertNotNull(result);
        assertEquals("test@example.com", result.getUsername());
        verify(userRepository).findByEmail("test@example.com");
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Ném UsernameNotFoundException khi email không tồn tại
     * Input: username="notfound@example.com"
     * Expected Output: UsernameNotFoundException
     * Notes: Email không tìm thấy trong DB
     */
    @Test
    @DisplayName("TC-FR-02-001: User không tồn tại")
    void TC_FR_02_001() {
        when(userRepository.findByEmail("notfound@example.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> customUserDetailsService.loadUserByUsername("notfound@example.com"));
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Email được chuẩn hóa lowercase khi tìm kiếm
     * Input: username="Test@Example.COM"
     * Expected Output: Gọi findByEmail với "test@example.com"
     * Notes: Chuẩn hóa email trước khi query
     */
    @Test
    @DisplayName("TC-FR-02-001: Email lowercase khi tìm kiếm")
    void TC_FR_02_001() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> customUserDetailsService.loadUserByUsername("Test@Example.COM"));

        verify(userRepository).findByEmail("test@example.com");
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: User trả về có đúng authorities (role)
     * Input: User có role ADMIN
     * Expected Output: Authorities chứa "ROLE_ADMIN"
     * Notes: Kiểm tra mapping role → authority
     */
    @Test
    @DisplayName("TC-FR-02-001: User có đúng authorities")
    void TC_FR_02_001() {
        Role adminRole = Role.builder().id(3L).name("ADMIN").build();
        User admin = User.builder()
                .id(1L).email("admin@test.com").name("Admin")
                .password("encoded").role(adminRole).enabled(true)
                .build();

        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));

        UserDetails result = customUserDetailsService.loadUserByUsername("admin@test.com");

        assertTrue(result.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: User bị vô hiệu hóa vẫn load được
     * Input: User có enabled=false
     * Expected Output: UserDetails với isEnabled()=false
     * Notes: Service chỉ load, không kiểm tra enabled
     */
    @Test
    @DisplayName("TC-FR-02-001: User bị disable vẫn load được")
    void TC_FR_02_001() {
        Role role = Role.builder().id(1L).name("USER").build();
        User disabledUser = User.builder()
                .id(1L).email("disabled@test.com").name("Disabled")
                .password("encoded").role(role).enabled(false)
                .build();

        when(userRepository.findByEmail("disabled@test.com")).thenReturn(Optional.of(disabledUser));

        UserDetails result = customUserDetailsService.loadUserByUsername("disabled@test.com");

        assertFalse(result.isEnabled());
    }
}

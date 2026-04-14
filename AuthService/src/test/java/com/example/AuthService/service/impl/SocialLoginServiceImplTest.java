package com.example.AuthService.service.impl;

import com.example.AuthService.entity.Role;
import com.example.AuthService.entity.User;
import com.example.AuthService.repository.RoleRepository;
import com.example.AuthService.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests cho SocialLoginServiceImpl – kiểm tra upsert user từ Google OAuth2.
 */
@ExtendWith(MockitoExtension.class)
class SocialLoginServiceImplTest {

    @Mock private UserRepository userRepo;
    @Mock private RoleRepository roleRepo;

    @InjectMocks
    private SocialLoginServiceImpl socialLoginService;

    private Role userRole;

    @BeforeEach
    void setUp() {
        userRole = Role.builder().id(1L).name("USER").build();
    }

    private OAuth2User mockOAuth2User(String sub, String email, String name, String picture) {
        OAuth2User oauth = mock(OAuth2User.class);
        lenient().when(oauth.getAttribute("sub")).thenReturn(sub);
        lenient().when(oauth.getAttribute("email")).thenReturn(email);
        lenient().when(oauth.getAttribute("name")).thenReturn(name);
        lenient().when(oauth.getAttribute("picture")).thenReturn(picture);
        return oauth;
    }

    // ==================== UPSERT GOOGLE USER ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Tạo user mới khi chưa tồn tại (cả googleAccountId và email đều mới)
     * Input: OAuth2User với sub, email, name, picture mới
     * Expected Output: User mới được tạo với role USER
     * Notes: CheckDB – user mới với googleAccountId, email, name, photoUrl
     */
    @Test
    @DisplayName("TC-FR-02-001: User mới → tạo mới")
    void TC_FR_02_001() {
        OAuth2User oauth = mockOAuth2User("google-sub-123", "new@gmail.com", "New User", "https://pic.jpg");

        when(userRepo.findByGoogleAccountId("google-sub-123")).thenReturn(Optional.empty());
        when(userRepo.findByEmail("new@gmail.com")).thenReturn(Optional.empty());
        when(roleRepo.findByName("USER")).thenReturn(Optional.of(userRole));
        when(userRepo.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });

        User result = socialLoginService.upsertGoogleUser(oauth);

        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo("new@gmail.com");
        verify(userRepo).save(any(User.class));
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Cập nhật user đã tồn tại theo googleAccountId
     * Input: OAuth2User với sub đã tồn tại trong DB
     * Expected Output: User được cập nhật name, email, photoUrl
     * Notes: CheckDB – thông tin user được cập nhật
     */
    @Test
    @DisplayName("TC-FR-02-001: User tồn tại → cập nhật")
    void TC_FR_02_001() {
        User existingUser = User.builder().id(1L).email("old@gmail.com")
                .name("Old Name").googleAccountId("google-sub-123").build();
        OAuth2User oauth = mockOAuth2User("google-sub-123", "updated@gmail.com", "Updated Name", "https://new-pic.jpg");

        when(userRepo.findByGoogleAccountId("google-sub-123")).thenReturn(Optional.of(existingUser));
        when(userRepo.save(existingUser)).thenReturn(existingUser);

        User result = socialLoginService.upsertGoogleUser(oauth);

        assertThat(result.getEmail()).isEqualTo("updated@gmail.com");
        assertThat(result.getName()).isEqualTo("Updated Name");
        assertThat(result.getPhotoUrl()).isEqualTo("https://new-pic.jpg");
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Hợp nhất tài khoản khi tìm được user theo email (fallback)
     * Input: Google sub mới nhưng email đã tồn tại
     * Expected Output: User hiện có được gán googleAccountId mới
     * Notes: Kiểm tra nhánh fallback findByEmail
     */
    @Test
    @DisplayName("TC-FR-02-001: Hợp nhất theo email")
    void TC_FR_02_001() {
        User existingUser = User.builder().id(1L).email("user@gmail.com")
                .name("Existing").googleAccountId(null).build();
        OAuth2User oauth = mockOAuth2User("new-sub-456", "user@gmail.com", "Existing Updated", null);

        when(userRepo.findByGoogleAccountId("new-sub-456")).thenReturn(Optional.empty());
        when(userRepo.findByEmail("user@gmail.com")).thenReturn(Optional.of(existingUser));
        when(userRepo.save(existingUser)).thenReturn(existingUser);

        User result = socialLoginService.upsertGoogleUser(oauth);

        assertThat(result.getGoogleAccountId()).isEqualTo("new-sub-456");
        assertThat(result.getName()).isEqualTo("Existing Updated");
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Thất bại khi Google sub (account id) bị thiếu
     * Input: OAuth2User không có sub attribute
     * Expected Output: IllegalStateException "Google 'sub' (account id) is missing"
     * Notes: Kiểm tra nhánh sub == null/blank
     */
    @Test
    @DisplayName("TC-FR-02-001: Sub null → exception")
    void TC_FR_02_001() {
        OAuth2User oauth = mockOAuth2User(null, "user@gmail.com", "Name", null);

        assertThatThrownBy(() -> socialLoginService.upsertGoogleUser(oauth))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Google 'sub'");
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Tạo user mới thất bại khi role USER không tồn tại
     * Input: Sub và email mới, roleRepo trả về empty
     * Expected Output: IllegalStateException "Default role 'USER' not found"
     * Notes: Kiểm tra nhánh roleRepo.findByName trả về empty
     */
    @Test
    @DisplayName("TC-FR-02-001: Role USER không tồn tại → exception")
    void TC_FR_02_001() {
        OAuth2User oauth = mockOAuth2User("sub-789", "new@gmail.com", "New", null);

        when(userRepo.findByGoogleAccountId("sub-789")).thenReturn(Optional.empty());
        when(userRepo.findByEmail("new@gmail.com")).thenReturn(Optional.empty());
        when(roleRepo.findByName("USER")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> socialLoginService.upsertGoogleUser(oauth))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Default role 'USER' not found");
    }
}

package com.example.AuthService.service.impl;

import com.example.AuthService.dto.request.UserUpdateRequestDTO;
import com.example.AuthService.dto.response.UserProfileResponse;
import com.example.AuthService.dto.response.UserResponseDTO;
import com.example.AuthService.entity.Country;
import com.example.AuthService.entity.Role;
import com.example.AuthService.entity.User;
import com.example.AuthService.repository.CountryRepository;
import com.example.AuthService.repository.RoleRepository;
import com.example.AuthService.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests cho UserServiceImpl – kiểm tra CRUD user, profile, phân trang.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private CountryRepository countryRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    private User user;
    private Role userRole;
    private Country country;

    @BeforeEach
    void setUp() {
        userRole = Role.builder().id(1L).name("USER").build();
        country = new Country(1L, "Vietnam", "VN");

        user = User.builder()
                .id(1L).email("user@test.com").name("Test User")
                .password("encoded").role(userRole).country(country)
                .gender("Male").phoneNumber("0123456789")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .enabled(true).accountNonExpired(true)
                .accountNonLocked(true).credentialsNonExpired(true)
                .build();
    }

    // ==================== GET USER PROFILE BY EMAIL ====================

    /**
     * Test Case ID: TC_AUTH_UserServiceImpl_getUserProfileByEmail_001
     * Test Objective: Lấy profile user theo email thành công
     * Input: email hợp lệ
     * Expected Output: UserProfileResponse chứa thông tin đầy đủ
     * Notes: Happy path
     */
    @Test
    @DisplayName("TC_AUTH_UserServiceImpl_getUserProfileByEmail_001: Lấy profile thành công")
    void TC_AUTH_UserServiceImpl_getUserProfileByEmail_001() {
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        UserProfileResponse result = userService.getUserProfileByEmail("user@test.com");

        assertThat(result.getEmail()).isEqualTo("user@test.com");
        assertThat(result.getName()).isEqualTo("Test User");
        assertThat(result.getRoleName()).isEqualTo("USER");
        assertThat(result.getCountryName()).isEqualTo("Vietnam");
    }

    /**
     * Test Case ID: TC_AUTH_UserServiceImpl_getUserProfileByEmail_002
     * Test Objective: Lấy profile thất bại khi email không tồn tại
     * Input: email không có trong DB
     * Expected Output: RuntimeException "User not found"
     * Notes: Kiểm tra nhánh findByEmail trả về empty
     */
    @Test
    @DisplayName("TC_AUTH_UserServiceImpl_getUserProfileByEmail_002: Email không tồn tại → exception")
    void TC_AUTH_UserServiceImpl_getUserProfileByEmail_002() {
        when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserProfileByEmail("ghost@test.com"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("User not found");
    }

    /**
     * Test Case ID: TC_AUTH_UserServiceImpl_getUserProfileByEmail_003
     * Test Objective: Profile với role = null, country = null
     * Input: User không có role và country
     * Expected Output: roleName = null, countryName = null
     * Notes: Kiểm tra nhánh role == null, country == null
     */
    @Test
    @DisplayName("TC_AUTH_UserServiceImpl_getUserProfileByEmail_003: Role/Country null")
    void TC_AUTH_UserServiceImpl_getUserProfileByEmail_003() {
        User noRoleUser = User.builder().id(2L).email("norole@test.com")
                .name("No Role").role(null).country(null).build();
        when(userRepository.findByEmail("norole@test.com")).thenReturn(Optional.of(noRoleUser));

        UserProfileResponse result = userService.getUserProfileByEmail("norole@test.com");

        assertThat(result.getRoleName()).isNull();
        assertThat(result.getCountryName()).isNull();
    }

    // ==================== GET ALL USERS ====================

    /**
     * Test Case ID: TC_AUTH_UserServiceImpl_getAllUsers_001
     * Test Objective: Lấy danh sách users phân trang thành công
     * Input: page=0, size=10, không filter
     * Expected Output: List<UserResponseDTO> có phần tử
     * Notes: Happy path
     */
    @Test
    @DisplayName("TC_AUTH_UserServiceImpl_getAllUsers_001: Lấy danh sách users thành công")
    void TC_AUTH_UserServiceImpl_getAllUsers_001() {
        Page<User> page = new PageImpl<>(List.of(user));
        when(userRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        List<UserResponseDTO> result = userService.getAllUsers(0, 10, null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEmail()).isEqualTo("user@test.com");
    }

    // ==================== GET USER BY ID ====================

    /**
     * Test Case ID: TC_AUTH_UserServiceImpl_getUserById_001
     * Test Objective: Lấy user theo id thành công
     * Input: id = 1
     * Expected Output: UserResponseDTO
     * Notes: Happy path
     */
    @Test
    @DisplayName("TC_AUTH_UserServiceImpl_getUserById_001: Lấy user theo id thành công")
    void TC_AUTH_UserServiceImpl_getUserById_001() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserResponseDTO result = userService.getUserById(1L);

        assertThat(result.getId()).isEqualTo(1L);
    }

    /**
     * Test Case ID: TC_AUTH_UserServiceImpl_getUserById_002
     * Test Objective: Lấy user thất bại khi id không tồn tại
     * Input: id = 999
     * Expected Output: RuntimeException "User not found"
     * Notes: Kiểm tra nhánh findById trả về empty
     */
    @Test
    @DisplayName("TC_AUTH_UserServiceImpl_getUserById_002: User không tồn tại → exception")
    void TC_AUTH_UserServiceImpl_getUserById_002() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("User not found");
    }

    // ==================== UPDATE USER ====================

    /**
     * Test Case ID: TC_AUTH_UserServiceImpl_updateUser_001
     * Test Objective: Cập nhật user đầy đủ thông tin thành công
     * Input: UserUpdateRequestDTO với tất cả fields
     * Expected Output: UserResponseDTO cập nhật
     * Notes: CheckDB – tất cả fields được cập nhật
     */
    @Test
    @DisplayName("TC_AUTH_UserServiceImpl_updateUser_001: Cập nhật đầy đủ thành công")
    void TC_AUTH_UserServiceImpl_updateUser_001() {
        UserUpdateRequestDTO req = new UserUpdateRequestDTO();
        req.setName("Updated Name");
        req.setGender("Female");
        req.setPhoneNumber("0987654321");
        req.setPassword("newPassword");
        req.setEnabled(false);
        req.setAccountNonLocked(false);
        req.setDateOfBirth(LocalDate.of(1995, 5, 5));

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newPassword")).thenReturn("newEncoded");
        when(userRepository.save(user)).thenReturn(user);

        UserResponseDTO result = userService.updateUser(1L, req);

        assertThat(user.getName()).isEqualTo("Updated Name");
        assertThat(user.getGender()).isEqualTo("Female");
        assertThat(user.getPassword()).isEqualTo("newEncoded");
    }

    /**
     * Test Case ID: TC_AUTH_UserServiceImpl_updateUser_002
     * Test Objective: Cập nhật email thất bại khi email mới đã tồn tại
     * Input: email mới trùng với user khác
     * Expected Output: RuntimeException "Email already exists"
     * Notes: Kiểm tra nhánh existsByEmail == true
     */
    @Test
    @DisplayName("TC_AUTH_UserServiceImpl_updateUser_002: Email mới đã tồn tại → exception")
    void TC_AUTH_UserServiceImpl_updateUser_002() {
        UserUpdateRequestDTO req = new UserUpdateRequestDTO();
        req.setEmail("existing@test.com");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmail("existing@test.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.updateUser(1L, req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Email already exists");
    }

    /**
     * Test Case ID: TC_AUTH_UserServiceImpl_updateUser_003
     * Test Objective: Cập nhật role và country thành công
     * Input: roleId = 2, countryId = 1
     * Expected Output: User có role và country mới
     * Notes: CheckDB – role và country được cập nhật
     */
    @Test
    @DisplayName("TC_AUTH_UserServiceImpl_updateUser_003: Cập nhật role/country thành công")
    void TC_AUTH_UserServiceImpl_updateUser_003() {
        Role adminRole = Role.builder().id(2L).name("ADMIN").build();
        UserUpdateRequestDTO req = new UserUpdateRequestDTO();
        req.setRoleId(2L);
        req.setCountryId(1L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(roleRepository.findById(2L)).thenReturn(Optional.of(adminRole));
        when(countryRepository.findById(1L)).thenReturn(Optional.of(country));
        when(userRepository.save(user)).thenReturn(user);

        userService.updateUser(1L, req);

        assertThat(user.getRole().getName()).isEqualTo("ADMIN");
    }

    /**
     * Test Case ID: TC_AUTH_UserServiceImpl_updateUser_004
     * Test Objective: Cập nhật với password blank → không đổi password
     * Input: password = "   "
     * Expected Output: Password giữ nguyên
     * Notes: Kiểm tra nhánh password.isBlank()
     */
    @Test
    @DisplayName("TC_AUTH_UserServiceImpl_updateUser_004: Password blank → không đổi")
    void TC_AUTH_UserServiceImpl_updateUser_004() {
        String originalPwd = user.getPassword();
        UserUpdateRequestDTO req = new UserUpdateRequestDTO();
        req.setPassword("   ");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        userService.updateUser(1L, req);

        assertThat(user.getPassword()).isEqualTo(originalPwd);
        verify(passwordEncoder, never()).encode(any());
    }

    // ==================== DELETE USER ====================

    /**
     * Test Case ID: TC_AUTH_UserServiceImpl_deleteUser_001
     * Test Objective: Xóa user thành công
     * Input: id hợp lệ
     * Expected Output: user được xóa
     * Notes: CheckDB – user bị xóa khỏi DB
     */
    @Test
    @DisplayName("TC_AUTH_UserServiceImpl_deleteUser_001: Xóa user thành công")
    void TC_AUTH_UserServiceImpl_deleteUser_001() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.deleteUser(1L);

        verify(userRepository).delete(user);
    }

    /**
     * Test Case ID: TC_AUTH_UserServiceImpl_deleteUser_002
     * Test Objective: Xóa user thất bại khi không tồn tại
     * Input: id = 999
     * Expected Output: RuntimeException "User not found"
     * Notes: Kiểm tra nhánh findById trả về empty
     */
    @Test
    @DisplayName("TC_AUTH_UserServiceImpl_deleteUser_002: User không tồn tại → exception")
    void TC_AUTH_UserServiceImpl_deleteUser_002() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deleteUser(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("User not found");
    }

    // ==================== CREATE USER ====================

    /**
     * Test Case ID: TC_AUTH_UserServiceImpl_createUser_001
     * Test Objective: Tạo user mới thành công
     * Input: UserUpdateRequestDTO với email và password
     * Expected Output: UserResponseDTO
     * Notes: CheckDB – user mới được lưu vào DB, password encoded
     */
    @Test
    @DisplayName("TC_AUTH_UserServiceImpl_createUser_001: Tạo user thành công")
    void TC_AUTH_UserServiceImpl_createUser_001() {
        UserUpdateRequestDTO req = new UserUpdateRequestDTO();
        req.setEmail("new@test.com");
        req.setPassword("password123");
        req.setName("New User");

        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encodedPwd");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(10L);
            return u;
        });

        UserResponseDTO result = userService.createUser(req);

        assertThat(result.getEmail()).isEqualTo("new@test.com");
        verify(passwordEncoder).encode("password123");
    }

    /**
     * Test Case ID: TC_AUTH_UserServiceImpl_createUser_002
     * Test Objective: Tạo user thất bại khi email đã tồn tại
     * Input: email trùng trong DB
     * Expected Output: RuntimeException "User with email already exists"
     * Notes: Kiểm tra nhánh existsByEmail == true
     */
    @Test
    @DisplayName("TC_AUTH_UserServiceImpl_createUser_002: Email đã tồn tại → exception")
    void TC_AUTH_UserServiceImpl_createUser_002() {
        UserUpdateRequestDTO req = new UserUpdateRequestDTO();
        req.setEmail("existing@test.com");
        req.setPassword("pwd");

        when(userRepository.existsByEmail("existing@test.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("User with email already exists");
    }

    /**
     * Test Case ID: TC_AUTH_UserServiceImpl_createUser_003
     * Test Objective: Tạo user thất bại khi email hoặc password null
     * Input: email = null
     * Expected Output: RuntimeException "Email and password are required"
     * Notes: Kiểm tra nhánh email == null || password == null
     */
    @Test
    @DisplayName("TC_AUTH_UserServiceImpl_createUser_003: Email/Password null → exception")
    void TC_AUTH_UserServiceImpl_createUser_003() {
        UserUpdateRequestDTO req = new UserUpdateRequestDTO();
        req.setEmail(null);
        req.setPassword(null);

        assertThatThrownBy(() -> userService.createUser(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Email and password are required");
    }

    // ==================== UPDATE MY PROFILE ====================

    /**
     * Test Case ID: TC_AUTH_UserServiceImpl_updateMyProfile_001
     * Test Objective: Cập nhật profile cá nhân thành công
     * Input: email, UserUpdateRequestDTO hợp lệ
     * Expected Output: UserProfileResponse cập nhật
     * Notes: CheckDB – thông tin profile được cập nhật
     */
    @Test
    @DisplayName("TC_AUTH_UserServiceImpl_updateMyProfile_001: Cập nhật profile thành công")
    void TC_AUTH_UserServiceImpl_updateMyProfile_001() {
        UserUpdateRequestDTO req = new UserUpdateRequestDTO();
        req.setName("New Name");
        req.setGender("Female");
        req.setPhoneNumber("0999999999");
        req.setPassword("newPwd");
        req.setDateOfBirth(LocalDate.of(1992, 3, 3));

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newPwd")).thenReturn("newEncoded");
        when(userRepository.save(user)).thenReturn(user);

        UserProfileResponse result = userService.updateMyProfile("user@test.com", req);

        assertThat(result.getName()).isEqualTo("New Name");
        assertThat(user.getPassword()).isEqualTo("newEncoded");
    }

    /**
     * Test Case ID: TC_AUTH_UserServiceImpl_updateMyProfile_002
     * Test Objective: Cập nhật profile không đổi password (password null)
     * Input: password = null
     * Expected Output: Password giữ nguyên
     * Notes: Kiểm tra nhánh password == null
     */
    @Test
    @DisplayName("TC_AUTH_UserServiceImpl_updateMyProfile_002: Password null → không đổi")
    void TC_AUTH_UserServiceImpl_updateMyProfile_002() {
        String originalPwd = user.getPassword();
        UserUpdateRequestDTO req = new UserUpdateRequestDTO();
        req.setName("Same Name");

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        userService.updateMyProfile("user@test.com", req);

        assertThat(user.getPassword()).isEqualTo(originalPwd);
    }

    /**
     * Test Case ID: TC_AUTH_UserServiceImpl_updateMyProfile_003
     * Test Objective: Cập nhật profile thất bại khi user không tồn tại
     * Input: email không có trong DB
     * Expected Output: RuntimeException "User not found"
     * Notes: Kiểm tra nhánh findByEmail trả về empty
     */
    @Test
    @DisplayName("TC_AUTH_UserServiceImpl_updateMyProfile_003: User không tồn tại → exception")
    void TC_AUTH_UserServiceImpl_updateMyProfile_003() {
        when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateMyProfile("ghost@test.com", new UserUpdateRequestDTO()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("User not found");
    }
}

package com.example.AuthService.controller;

import com.example.AuthService.dto.request.UserUpdateRequestDTO;
import com.example.AuthService.dto.response.UserResponseDTO;
import com.example.AuthService.security.jwt.JwtService;
import com.example.AuthService.security.OAuth2LoginSuccessHandler;
import com.example.AuthService.service.SocialLoginService;
import org.springframework.security.core.userdetails.UserDetailsService;
import com.example.AuthService.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests cho {@link AdminUserController}.
 * Kiểm tra các endpoint quản lý người dùng bởi admin.
 */
@WebMvcTest(AdminUserController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private SocialLoginService socialLoginService;

    @MockitoBean
    private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    @MockitoBean
    private UserDetailsService userDetailsService;

    // ======================== GET ALL USERS ========================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Admin lấy danh sách user thành công
     * Input: page=0, size=20, không filter
     * Expected Output: HTTP 200, danh sách user phân trang
     * Notes: Happy path
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC-FR-02-001: Lấy danh sách user thành công")
    void TC_FR_02_001() throws Exception {
        when(userService.getAllUsers(0, 20, null, null, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Admin lấy danh sách user với keyword filter
     * Input: keyword="test", page=0, size=20
     * Expected Output: HTTP 200
     * Notes: Tìm kiếm theo tên/email
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC-FR-02-001: Lấy danh sách user với keyword")
    void TC_FR_02_001() throws Exception {
        when(userService.getAllUsers(0, 20, "test", null, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/users")
                        .param("keyword", "test"))
                .andExpect(status().isOk());
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Admin lấy danh sách user với filter roleId và enabled
     * Input: roleId=1, enabled=true
     * Expected Output: HTTP 200
     * Notes: Lọc theo vai trò và trạng thái kích hoạt
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC-FR-02-001: Lấy user với filter roleId và enabled")
    void TC_FR_02_001() throws Exception {
        when(userService.getAllUsers(0, 20, null, 1L, true)).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/users")
                        .param("roleId", "1")
                        .param("enabled", "true"))
                .andExpect(status().isOk());
    }

    // ======================== GET USER BY ID ========================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Admin lấy thông tin user theo ID thành công
     * Input: id=1
     * Expected Output: HTTP 200, thông tin user
     * Notes: Happy path
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC-FR-02-001: Lấy user theo ID thành công")
    void TC_FR_02_001() throws Exception {
        UserResponseDTO user = UserResponseDTO.builder()
                .id(1L).email("user@test.com").name("Test User").roleName("USER").build();

        when(userService.getUserById(1L)).thenReturn(user);

        mockMvc.perform(get("/api/admin/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@test.com"));
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Admin lấy user không tồn tại
     * Input: id=999
     * Expected Output: HTTP 404
     * Notes: ID không tồn tại
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC-FR-02-001: Lấy user - không tồn tại")
    void TC_FR_02_001() throws Exception {
        when(userService.getUserById(999L))
                .thenThrow(new ResponseStatusException(NOT_FOUND, "Không tìm thấy user"));

        mockMvc.perform(get("/api/admin/users/999"))
                .andExpect(status().isNotFound());
    }

    // ======================== UPDATE USER ========================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Admin cập nhật thông tin user thành công
     * Input: id=1, UserUpdateRequestDTO(name="Updated Name")
     * Expected Output: HTTP 200, user đã cập nhật
     * Notes: Happy path
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC-FR-02-001: Cập nhật user thành công")
    void TC_FR_02_001() throws Exception {
        UserUpdateRequestDTO request = new UserUpdateRequestDTO();
        request.setName("Updated Name");

        UserResponseDTO response = UserResponseDTO.builder()
                .id(1L).name("Updated Name").email("user@test.com").build();

        when(userService.updateUser(eq(1L), any())).thenReturn(response);

        mockMvc.perform(put("/api/admin/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"));
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Admin cập nhật user không tồn tại
     * Input: id=999
     * Expected Output: HTTP 404
     * Notes: User không tìm thấy
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC-FR-02-001: Cập nhật user - không tồn tại")
    void TC_FR_02_001() throws Exception {
        UserUpdateRequestDTO request = new UserUpdateRequestDTO();
        request.setName("Name");

        when(userService.updateUser(eq(999L), any()))
                .thenThrow(new ResponseStatusException(NOT_FOUND, "Không tìm thấy user"));

        mockMvc.perform(put("/api/admin/users/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Admin cập nhật role cho user
     * Input: id=1, UserUpdateRequestDTO(roleId=2)
     * Expected Output: HTTP 200
     * Notes: Đổi role từ USER sang MODERATOR
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC-FR-02-001: Cập nhật role cho user")
    void TC_FR_02_001() throws Exception {
        UserUpdateRequestDTO request = new UserUpdateRequestDTO();
        request.setRoleId(2L);

        UserResponseDTO response = UserResponseDTO.builder()
                .id(1L).roleName("MODERATOR").build();

        when(userService.updateUser(eq(1L), any())).thenReturn(response);

        mockMvc.perform(put("/api/admin/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleName").value("MODERATOR"));
    }

    // ======================== DELETE USER ========================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Admin xóa user thành công
     * Input: id=1
     * Expected Output: HTTP 200, message "User deleted"
     * Notes: Happy path
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC-FR-02-001: Xóa user thành công")
    void TC_FR_02_001() throws Exception {
        doNothing().when(userService).deleteUser(1L);

        mockMvc.perform(delete("/api/admin/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User deleted"));
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Admin xóa user không tồn tại
     * Input: id=999
     * Expected Output: HTTP 404
     * Notes: User không tìm thấy
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC-FR-02-001: Xóa user - không tồn tại")
    void TC_FR_02_001() throws Exception {
        doThrow(new ResponseStatusException(NOT_FOUND, "Không tìm thấy user"))
                .when(userService).deleteUser(999L);

        mockMvc.perform(delete("/api/admin/users/999"))
                .andExpect(status().isNotFound());
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Admin xóa user có đơn hàng liên kết
     * Input: id=1 (có đơn hàng)
     * Expected Output: HTTP 500
     * Notes: Vi phạm ràng buộc FK
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC-FR-02-001: Xóa user có đơn hàng liên kết")
    void TC_FR_02_001() throws Exception {
        doThrow(new RuntimeException("Không thể xóa user có đơn hàng"))
                .when(userService).deleteUser(1L);

        mockMvc.perform(delete("/api/admin/users/1"))
                .andExpect(status().isInternalServerError());
    }

    // ======================== CREATE USER ========================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Admin tạo user mới thành công
     * Input: UserUpdateRequestDTO(email="new@test.com", name="New User", password="pass123")
     * Expected Output: HTTP 201 Created
     * Notes: Happy path
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC-FR-02-001: Tạo user mới thành công")
    void TC_FR_02_001() throws Exception {
        UserUpdateRequestDTO request = new UserUpdateRequestDTO();
        request.setEmail("new@test.com");
        request.setName("New User");
        request.setPassword("password123");

        UserResponseDTO response = UserResponseDTO.builder()
                .id(1L).email("new@test.com").name("New User").roleName("USER").build();

        when(userService.createUser(any())).thenReturn(response);

        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("new@test.com"));
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Admin tạo user với email đã tồn tại
     * Input: UserUpdateRequestDTO(email="existing@test.com")
     * Expected Output: HTTP 409 Conflict
     * Notes: Email trùng
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC-FR-02-001: Tạo user - email đã tồn tại")
    void TC_FR_02_001() throws Exception {
        UserUpdateRequestDTO request = new UserUpdateRequestDTO();
        request.setEmail("existing@test.com");
        request.setName("User");
        request.setPassword("password123");

        when(userService.createUser(any()))
                .thenThrow(new ResponseStatusException(CONFLICT, "Email đã tồn tại"));

        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Admin tạo user với role chỉ định
     * Input: UserUpdateRequestDTO(email="mod@test.com", roleId=2)
     * Expected Output: HTTP 201, roleName="MODERATOR"
     * Notes: Tạo user với vai trò cụ thể
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC-FR-02-001: Tạo user với role MODERATOR")
    void TC_FR_02_001() throws Exception {
        UserUpdateRequestDTO request = new UserUpdateRequestDTO();
        request.setEmail("mod@test.com");
        request.setName("Moderator");
        request.setPassword("password123");
        request.setRoleId(2L);

        UserResponseDTO response = UserResponseDTO.builder()
                .id(2L).email("mod@test.com").name("Moderator").roleName("MODERATOR").build();

        when(userService.createUser(any())).thenReturn(response);

        mockMvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roleName").value("MODERATOR"));
    }
}

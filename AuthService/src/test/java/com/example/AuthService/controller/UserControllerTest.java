package com.example.AuthService.controller;

import com.example.AuthService.dto.request.UserUpdateRequestDTO;
import com.example.AuthService.dto.response.UserProfileResponse;
import com.example.AuthService.security.jwt.JwtService;
import com.example.AuthService.service.UserService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests cho {@link UserController}.
 * Kiểm tra các endpoint profile người dùng.
 */
@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsService userDetailsService;

    // ======================== GET USER PROFILE ========================

    /**
     * Test Case ID: TC_AUTH_UserController_getUserProfile_001
     * Test Objective: Lấy profile người dùng hiện tại thành công
     * Input: User đã đăng nhập với email user@test.com
     * Expected Output: HTTP 200, body chứa thông tin profile
     * Notes: Happy path
     */
    @Test
    @WithMockUser(username = "user@test.com")
    @DisplayName("TC_AUTH_UserController_getUserProfile_001: Lấy profile thành công")
    void TC_AUTH_UserController_getUserProfile_001() throws Exception {
        UserProfileResponse profile = UserProfileResponse.builder()
                .email("user@test.com")
                .name("Test User")
                .roleName("USER")
                .build();

        when(userService.getUserProfileByEmail("user@test.com")).thenReturn(profile);

        mockMvc.perform(get("/api/users/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@test.com"))
                .andExpect(jsonPath("$.name").value("Test User"));
    }

    /**
     * Test Case ID: TC_AUTH_UserController_getUserProfile_002
     * Test Objective: Lấy profile khi user không tồn tại trong DB
     * Input: User đăng nhập nhưng email không tìm thấy trong DB
     * Expected Output: HTTP 404
     * Notes: Trường hợp dữ liệu không nhất quán
     */
    @Test
    @WithMockUser(username = "ghost@test.com")
    @DisplayName("TC_AUTH_UserController_getUserProfile_002: Lấy profile - user không tồn tại")
    void TC_AUTH_UserController_getUserProfile_002() throws Exception {
        when(userService.getUserProfileByEmail("ghost@test.com"))
                .thenThrow(new ResponseStatusException(NOT_FOUND, "Không tìm thấy user"));

        mockMvc.perform(get("/api/users/profile"))
                .andExpect(status().isNotFound());
    }

    /**
     * Test Case ID: TC_AUTH_UserController_getUserProfile_003
     * Test Objective: Lấy profile với đầy đủ thông tin
     * Input: User có đầy đủ thông tin cá nhân
     * Expected Output: HTTP 200, body chứa tất cả trường
     * Notes: Kiểm tra tất cả trường của profile
     */
    @Test
    @WithMockUser(username = "full@test.com")
    @DisplayName("TC_AUTH_UserController_getUserProfile_003: Lấy profile đầy đủ thông tin")
    void TC_AUTH_UserController_getUserProfile_003() throws Exception {
        UserProfileResponse profile = UserProfileResponse.builder()
                .email("full@test.com")
                .name("Full User")
                .gender("Male")
                .phoneNumber("0901234567")
                .roleName("USER")
                .countryName("Vietnam")
                .build();

        when(userService.getUserProfileByEmail("full@test.com")).thenReturn(profile);

        mockMvc.perform(get("/api/users/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gender").value("Male"))
                .andExpect(jsonPath("$.phoneNumber").value("0901234567"))
                .andExpect(jsonPath("$.countryName").value("Vietnam"));
    }

    // ======================== UPDATE MY PROFILE ========================

    /**
     * Test Case ID: TC_AUTH_UserController_updateMyProfile_001
     * Test Objective: Cập nhật profile thành công
     * Input: UserUpdateRequestDTO(name="New Name", phoneNumber="0909999999")
     * Expected Output: HTTP 200, body chứa thông tin đã cập nhật
     * Notes: Happy path
     */
    @Test
    @WithMockUser(username = "user@test.com")
    @DisplayName("TC_AUTH_UserController_updateMyProfile_001: Cập nhật profile thành công")
    void TC_AUTH_UserController_updateMyProfile_001() throws Exception {
        UserUpdateRequestDTO request = new UserUpdateRequestDTO();
        request.setName("New Name");
        request.setPhoneNumber("0909999999");

        UserProfileResponse updatedProfile = UserProfileResponse.builder()
                .email("user@test.com")
                .name("New Name")
                .phoneNumber("0909999999")
                .build();

        when(userService.updateMyProfile(eq("user@test.com"), any(UserUpdateRequestDTO.class)))
                .thenReturn(updatedProfile);

        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"))
                .andExpect(jsonPath("$.phoneNumber").value("0909999999"));
    }

    /**
     * Test Case ID: TC_AUTH_UserController_updateMyProfile_002
     * Test Objective: Cập nhật profile với email trùng
     * Input: UserUpdateRequestDTO(email="existing@test.com")
     * Expected Output: HTTP 409 Conflict
     * Notes: Email đã thuộc tài khoản khác
     */
    @Test
    @WithMockUser(username = "user@test.com")
    @DisplayName("TC_AUTH_UserController_updateMyProfile_002: Cập nhật profile - email trùng")
    void TC_AUTH_UserController_updateMyProfile_002() throws Exception {
        UserUpdateRequestDTO request = new UserUpdateRequestDTO();
        request.setEmail("existing@test.com");

        when(userService.updateMyProfile(eq("user@test.com"), any()))
                .thenThrow(new ResponseStatusException(CONFLICT, "Email đã tồn tại"));

        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    /**
     * Test Case ID: TC_AUTH_UserController_updateMyProfile_003
     * Test Objective: Cập nhật profile với body rỗng (không thay đổi gì)
     * Input: UserUpdateRequestDTO rỗng
     * Expected Output: HTTP 200, profile không thay đổi
     * Notes: Không truyền field nào thì giữ nguyên
     */
    @Test
    @WithMockUser(username = "user@test.com")
    @DisplayName("TC_AUTH_UserController_updateMyProfile_003: Cập nhật profile - body rỗng")
    void TC_AUTH_UserController_updateMyProfile_003() throws Exception {
        UserUpdateRequestDTO request = new UserUpdateRequestDTO();

        UserProfileResponse profile = UserProfileResponse.builder()
                .email("user@test.com").name("Unchanged").build();

        when(userService.updateMyProfile(eq("user@test.com"), any())).thenReturn(profile);

        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Unchanged"));
    }
}

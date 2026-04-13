package com.example.AuthService.controller;

import com.example.AuthService.dto.request.PrescriptionRequest;
import com.example.AuthService.entity.Prescription;
import com.example.AuthService.entity.User;
import com.example.AuthService.repository.UserRepository;
import com.example.AuthService.security.jwt.JwtService;
import com.example.AuthService.security.OAuth2LoginSuccessHandler;
import com.example.AuthService.service.PrescriptionService;
import org.springframework.security.core.userdetails.UserDetailsService;
import com.example.AuthService.service.SocialLoginService;
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

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests cho {@link PrescriptionController}.
 * Kiểm tra các endpoint quản lý đơn thuốc.
 */
@WebMvcTest(PrescriptionController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class PrescriptionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PrescriptionService prescriptionService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private SocialLoginService socialLoginService;

    @MockitoBean
    private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    @MockitoBean
    private UserDetailsService userDetailsService;

    private User createMockUser() {
        User user = new User();
        user.setId(1L);
        user.setEmail("user@test.com");
        user.setName("Test User");
        return user;
    }

    // ======================== CREATE PRESCRIPTION ========================

    /**
     * Test Case ID: TC_AUTH_PrescriptionController_createPrescription_001
     * Test Objective: Tạo đơn thuốc thành công
     * Input: PrescriptionRequest hợp lệ, user đã đăng nhập
     * Expected Output: HTTP 200, thông báo tạo thành công kèm ID
     * Notes: Happy path
     */
    @Test
    @WithMockUser(username = "user@test.com")
    @DisplayName("TC_AUTH_PrescriptionController_createPrescription_001: Tạo đơn thuốc thành công")
    void TC_AUTH_PrescriptionController_createPrescription_001() throws Exception {
        User user = createMockUser();
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        Prescription saved = new Prescription();
        saved.setId(10L);
        when(prescriptionService.createPrescription(any(PrescriptionRequest.class), eq(user)))
                .thenReturn(saved);

        PrescriptionRequest request = PrescriptionRequest.builder()
                .name("Đơn thuốc viêm họng")
                .hospital("BV Đại học Y Dược")
                .doctorName("BS Nguyễn Văn A")
                .drugs(List.of())
                .build();

        mockMvc.perform(post("/api/prescriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("10")));
    }

    /**
     * Test Case ID: TC_AUTH_PrescriptionController_createPrescription_002
     * Test Objective: Tạo đơn thuốc khi user không tìm thấy
     * Input: PrescriptionRequest hợp lệ, user không tồn tại trong DB
     * Expected Output: HTTP 500
     * Notes: JWT hợp lệ nhưng user đã bị xóa
     */
    @Test
    @WithMockUser(username = "ghost@test.com")
    @DisplayName("TC_AUTH_PrescriptionController_createPrescription_002: Tạo đơn thuốc - user không tồn tại")
    void TC_AUTH_PrescriptionController_createPrescription_002() throws Exception {
        when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

        PrescriptionRequest request = PrescriptionRequest.builder()
                .name("Đơn thuốc").drugs(List.of()).build();

        mockMvc.perform(post("/api/prescriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());
    }

    /**
     * Test Case ID: TC_AUTH_PrescriptionController_createPrescription_003
     * Test Objective: Tạo đơn thuốc khi service ném lỗi
     * Input: PrescriptionRequest (thuốc không tồn tại)
     * Expected Output: HTTP 500
     * Notes: Service ném RuntimeException
     */
    @Test
    @WithMockUser(username = "user@test.com")
    @DisplayName("TC_AUTH_PrescriptionController_createPrescription_003: Tạo đơn thuốc - thuốc không tồn tại")
    void TC_AUTH_PrescriptionController_createPrescription_003() throws Exception {
        User user = createMockUser();
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(prescriptionService.createPrescription(any(), eq(user)))
                .thenThrow(new RuntimeException("Thuốc không tồn tại"));

        PrescriptionRequest request = PrescriptionRequest.builder()
                .name("Test").drugs(List.of()).build();

        mockMvc.perform(post("/api/prescriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());
    }

    // ======================== DELETE PRESCRIPTION ========================

    /**
     * Test Case ID: TC_AUTH_PrescriptionController_deletePrescription_001
     * Test Objective: Xóa đơn thuốc thành công
     * Input: id=1, user đã đăng nhập
     * Expected Output: HTTP 200, thông báo xóa thành công
     * Notes: Happy path
     */
    @Test
    @WithMockUser(username = "user@test.com")
    @DisplayName("TC_AUTH_PrescriptionController_deletePrescription_001: Xóa đơn thuốc thành công")
    void TC_AUTH_PrescriptionController_deletePrescription_001() throws Exception {
        User user = createMockUser();
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        doNothing().when(prescriptionService).deletePrescription(1L, user);

        mockMvc.perform(delete("/api/prescriptions/1"))
                .andExpect(status().isOk());
    }

    /**
     * Test Case ID: TC_AUTH_PrescriptionController_deletePrescription_002
     * Test Objective: Xóa đơn thuốc không tồn tại
     * Input: id=999
     * Expected Output: HTTP 500
     * Notes: Đơn thuốc không tìm thấy
     */
    @Test
    @WithMockUser(username = "user@test.com")
    @DisplayName("TC_AUTH_PrescriptionController_deletePrescription_002: Xóa đơn thuốc - không tồn tại")
    void TC_AUTH_PrescriptionController_deletePrescription_002() throws Exception {
        User user = createMockUser();
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        doThrow(new RuntimeException("Không tìm thấy đơn thuốc"))
                .when(prescriptionService).deletePrescription(999L, user);

        mockMvc.perform(delete("/api/prescriptions/999"))
                .andExpect(status().isInternalServerError());
    }

    /**
     * Test Case ID: TC_AUTH_PrescriptionController_deletePrescription_003
     * Test Objective: Xóa đơn thuốc của người khác
     * Input: id=1 (của user khác)
     * Expected Output: HTTP 500
     * Notes: User không phải chủ đơn thuốc
     */
    @Test
    @WithMockUser(username = "user@test.com")
    @DisplayName("TC_AUTH_PrescriptionController_deletePrescription_003: Xóa đơn thuốc của người khác")
    void TC_AUTH_PrescriptionController_deletePrescription_003() throws Exception {
        User user = createMockUser();
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        doThrow(new RuntimeException("Không có quyền xóa đơn thuốc này"))
                .when(prescriptionService).deletePrescription(1L, user);

        mockMvc.perform(delete("/api/prescriptions/1"))
                .andExpect(status().isInternalServerError());
    }

    // ======================== GET PRESCRIPTIONS BY STATUS ========================

    /**
     * Test Case ID: TC_AUTH_PrescriptionController_getPrescriptionsByStatus_001
     * Test Objective: Lấy danh sách đơn thuốc theo trạng thái thành công
     * Input: status=1 (active), user đã đăng nhập
     * Expected Output: HTTP 200, danh sách đơn thuốc
     * Notes: Happy path - lấy đơn thuốc đang hoạt động
     */
    @Test
    @WithMockUser(username = "user@test.com")
    @DisplayName("TC_AUTH_PrescriptionController_getPrescriptionsByStatus_001: Lấy đơn thuốc active")
    void TC_AUTH_PrescriptionController_getPrescriptionsByStatus_001() throws Exception {
        User user = createMockUser();
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(prescriptionService.getPrescriptionsByStatus(user, 1)).thenReturn(List.of());

        mockMvc.perform(get("/api/prescriptions/status/1"))
                .andExpect(status().isOk());
    }

    /**
     * Test Case ID: TC_AUTH_PrescriptionController_getPrescriptionsByStatus_002
     * Test Objective: Lấy đơn thuốc với status=0 (inactive)
     * Input: status=0
     * Expected Output: HTTP 200, danh sách đơn thuốc đã dừng
     * Notes: Lấy các đơn thuốc đã ngừng sử dụng
     */
    @Test
    @WithMockUser(username = "user@test.com")
    @DisplayName("TC_AUTH_PrescriptionController_getPrescriptionsByStatus_002: Lấy đơn thuốc inactive")
    void TC_AUTH_PrescriptionController_getPrescriptionsByStatus_002() throws Exception {
        User user = createMockUser();
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(prescriptionService.getPrescriptionsByStatus(user, 0)).thenReturn(List.of());

        mockMvc.perform(get("/api/prescriptions/status/0"))
                .andExpect(status().isOk());
    }

    /**
     * Test Case ID: TC_AUTH_PrescriptionController_getPrescriptionsByStatus_003
     * Test Objective: Lấy đơn thuốc khi không có đơn nào
     * Input: status=1, user chưa có đơn thuốc nào
     * Expected Output: HTTP 200, danh sách rỗng
     * Notes: Edge case - user mới chưa có đơn
     */
    @Test
    @WithMockUser(username = "user@test.com")
    @DisplayName("TC_AUTH_PrescriptionController_getPrescriptionsByStatus_003: Không có đơn thuốc nào")
    void TC_AUTH_PrescriptionController_getPrescriptionsByStatus_003() throws Exception {
        User user = createMockUser();
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(prescriptionService.getPrescriptionsByStatus(user, 1)).thenReturn(List.of());

        mockMvc.perform(get("/api/prescriptions/status/1"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    // ======================== GET SCHEDULES BY DATE ========================

    /**
     * Test Case ID: TC_AUTH_PrescriptionController_getSchedulesByDate_001
     * Test Objective: Lấy danh sách liều uống theo ngày thành công
     * Input: date=2025-01-15, user đã đăng nhập
     * Expected Output: HTTP 200, danh sách schedule
     * Notes: Happy path
     */
    @Test
    @WithMockUser(username = "user@test.com")
    @DisplayName("TC_AUTH_PrescriptionController_getSchedulesByDate_001: Lấy lịch uống thuốc theo ngày")
    void TC_AUTH_PrescriptionController_getSchedulesByDate_001() throws Exception {
        User user = createMockUser();
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(prescriptionService.getSchedulesByDate(any(), eq(user))).thenReturn(List.of());

        mockMvc.perform(get("/api/prescriptions/schedules")
                        .param("date", "2025-01-15"))
                .andExpect(status().isOk());
    }

    /**
     * Test Case ID: TC_AUTH_PrescriptionController_getSchedulesByDate_002
     * Test Objective: Lấy lịch uống thuốc ngày không có lịch
     * Input: date=2030-01-01 (ngày trong tương lai xa)
     * Expected Output: HTTP 200, danh sách rỗng
     * Notes: Không có đơn thuốc nào áp dụng cho ngày này
     */
    @Test
    @WithMockUser(username = "user@test.com")
    @DisplayName("TC_AUTH_PrescriptionController_getSchedulesByDate_002: Ngày không có lịch uống")
    void TC_AUTH_PrescriptionController_getSchedulesByDate_002() throws Exception {
        User user = createMockUser();
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(prescriptionService.getSchedulesByDate(any(), eq(user))).thenReturn(List.of());

        mockMvc.perform(get("/api/prescriptions/schedules")
                        .param("date", "2030-01-01"))
                .andExpect(status().isOk());
    }

    /**
     * Test Case ID: TC_AUTH_PrescriptionController_getSchedulesByDate_003
     * Test Objective: Lấy lịch uống thuốc khi user không tồn tại
     * Input: date=2025-01-15, user không tìm thấy
     * Expected Output: HTTP 500
     * Notes: JWT hợp lệ nhưng user đã bị xóa
     */
    @Test
    @WithMockUser(username = "ghost@test.com")
    @DisplayName("TC_AUTH_PrescriptionController_getSchedulesByDate_003: Lấy lịch - user không tồn tại")
    void TC_AUTH_PrescriptionController_getSchedulesByDate_003() throws Exception {
        when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/prescriptions/schedules")
                        .param("date", "2025-01-15"))
                .andExpect(status().isInternalServerError());
    }
}

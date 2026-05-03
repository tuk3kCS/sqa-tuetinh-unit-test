package com.example.AuthService.controller;

import com.example.AuthService.dto.response.DrugResponse;
import com.example.AuthService.entity.Drug;
import com.example.AuthService.security.jwt.JwtService;
import com.example.AuthService.service.DrugService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests cho {@link DrugController}.
 * Kiểm tra các endpoint quản lý thuốc (CRUD, search, suggest).
 */
@WebMvcTest(DrugController.class)
@AutoConfigureMockMvc
class DrugControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DrugService drugService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsService userDetailsService;

    // ======================== LIST ========================

    /**
     * Test Case ID: TC_AUTH_DrugController_list_001
     * Test Objective: Lấy danh sách thuốc thành công (không filter)
     * Input: User đã đăng nhập, không có tham số filter
     * Expected Output: HTTP 200, page thuốc
     * Notes: Happy path - lấy tất cả thuốc phân trang
     */
    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("TC_AUTH_DrugController_list_001: Lấy danh sách thuốc thành công")
    void TC_AUTH_DrugController_list_001() throws Exception {
        DrugResponse drug = DrugResponse.builder()
                .id(1L).name("Paracetamol").price(BigDecimal.valueOf(15000)).build();
        Page<DrugResponse> page = new PageImpl<>(List.of(drug));

        when(drugService.getDrugs(any(), any(), eq(false))).thenReturn(page);

        mockMvc.perform(get("/api/drugs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("Paracetamol"));
    }

    /**
     * Test Case ID: TC_AUTH_DrugController_list_002
     * Test Objective: Lấy danh sách thuốc với filter tìm kiếm
     * Input: q="para", minPrice=10000, maxPrice=50000
     * Expected Output: HTTP 200
     * Notes: Sử dụng các tham số lọc
     */
    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("TC_AUTH_DrugController_list_002: Lấy danh sách thuốc với filter")
    void TC_AUTH_DrugController_list_002() throws Exception {
        Page<DrugResponse> page = new PageImpl<>(List.of());
        when(drugService.getDrugs(any(), any(), anyBoolean())).thenReturn(page);

        mockMvc.perform(get("/api/drugs")
                        .param("q", "para")
                        .param("minPrice", "10000")
                        .param("maxPrice", "50000"))
                .andExpect(status().isOk());
    }

    /**
     * Test Case ID: TC_AUTH_DrugController_list_003
     * Test Objective: Admin thấy cả thuốc inactive
     * Input: Admin đã đăng nhập
     * Expected Output: HTTP 200, isAdmin=true khi gọi service
     * Notes: Admin có quyền xem thuốc đã ngừng bán
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC_AUTH_DrugController_list_003: Admin xem danh sách thuốc (bao gồm inactive)")
    void TC_AUTH_DrugController_list_003() throws Exception {
        Page<DrugResponse> page = new PageImpl<>(List.of());
        when(drugService.getDrugs(any(), any(), eq(true))).thenReturn(page);

        mockMvc.perform(get("/api/drugs"))
                .andExpect(status().isOk());
    }

    // ======================== SUGGEST ========================

    /**
     * Test Case ID: TC_AUTH_DrugController_suggest_001
     * Test Objective: Gợi ý autocomplete thành công
     * Input: q="para", limit=10
     * Expected Output: HTTP 200, danh sách tên thuốc gợi ý
     * Notes: Happy path
     */
    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("TC_AUTH_DrugController_suggest_001: Gợi ý tên thuốc thành công")
    void TC_AUTH_DrugController_suggest_001() throws Exception {
        when(drugService.suggestNames("para", 10))
                .thenReturn(List.of("Paracetamol", "Paracetamol Extra"));

        mockMvc.perform(get("/api/drugs/suggest")
                        .param("q", "para"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0]").value("Paracetamol"));
    }

    /**
     * Test Case ID: TC_AUTH_DrugController_suggest_002
     * Test Objective: Gợi ý khi không có kết quả
     * Input: q="xyznotfound"
     * Expected Output: HTTP 200, danh sách rỗng
     * Notes: Không có thuốc nào khớp
     */
    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("TC_AUTH_DrugController_suggest_002: Gợi ý - không tìm thấy kết quả")
    void TC_AUTH_DrugController_suggest_002() throws Exception {
        when(drugService.suggestNames("xyznotfound", 10)).thenReturn(List.of());

        mockMvc.perform(get("/api/drugs/suggest")
                        .param("q", "xyznotfound"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    /**
     * Test Case ID: TC_AUTH_DrugController_suggest_003
     * Test Objective: Gợi ý với limit tùy chỉnh
     * Input: q="para", limit=3
     * Expected Output: HTTP 200, tối đa 3 kết quả
     * Notes: Kiểm tra limit hoạt động
     */
    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("TC_AUTH_DrugController_suggest_003: Gợi ý với limit tùy chỉnh")
    void TC_AUTH_DrugController_suggest_003() throws Exception {
        when(drugService.suggestNames("para", 3))
                .thenReturn(List.of("Paracetamol", "Paracetamol Extra", "Paramol"));

        mockMvc.perform(get("/api/drugs/suggest")
                        .param("q", "para")
                        .param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    // ======================== GET DRUG ========================

    /**
     * Test Case ID: TC_AUTH_DrugController_getDrug_001
     * Test Objective: Lấy chi tiết thuốc theo ID thành công
     * Input: id=1
     * Expected Output: HTTP 200, thông tin thuốc
     * Notes: Happy path
     */
    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("TC_AUTH_DrugController_getDrug_001: Lấy chi tiết thuốc thành công")
    void TC_AUTH_DrugController_getDrug_001() throws Exception {
        Drug drug = Drug.builder()
                .id(1L).name("Paracetamol")
                .price(BigDecimal.valueOf(15000))
                .importPrice(BigDecimal.valueOf(10000))
                .build();
        when(drugService.getDrugById(1L)).thenReturn(drug);

        mockMvc.perform(get("/api/drugs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Paracetamol"));
    }

    /**
     * Test Case ID: TC_AUTH_DrugController_getDrug_002
     * Test Objective: Lấy thuốc không tồn tại
     * Input: id=999
     * Expected Output: HTTP 500 (RuntimeException)
     * Notes: ID không tồn tại trong DB
     */
    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("TC_AUTH_DrugController_getDrug_002: Lấy thuốc không tồn tại")
    void TC_AUTH_DrugController_getDrug_002() throws Exception {
        when(drugService.getDrugById(999L))
                .thenThrow(new RuntimeException("Không tìm thấy thuốc"));

        mockMvc.perform(get("/api/drugs/999"))
                .andExpect(status().isInternalServerError());
    }

    // ======================== CREATE DRUG WITH IMAGE ========================

    /**
     * Test Case ID: TC_AUTH_DrugController_createDrugWithImage_001
     * Test Objective: Tạo thuốc mới kèm ảnh thành công
     * Input: drug JSON + image file
     * Expected Output: HTTP 200, thông tin thuốc đã tạo
     * Notes: Happy path - Admin tạo thuốc
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC_AUTH_DrugController_createDrugWithImage_001: Tạo thuốc mới kèm ảnh thành công")
    void TC_AUTH_DrugController_createDrugWithImage_001() throws Exception {
        Drug createdDrug = Drug.builder()
                .id(1L).name("New Drug")
                .price(BigDecimal.valueOf(20000))
                .importPrice(BigDecimal.valueOf(15000))
                .image("http://cloudinary.com/image.jpg")
                .build();
        when(drugService.createDrugWithImage(any(Drug.class), any())).thenReturn(createdDrug);

        String drugJson = "{\"name\":\"New Drug\",\"price\":20000,\"importPrice\":15000}";
        MockMultipartFile drugPart = new MockMultipartFile("drug", "", "application/json", drugJson.getBytes());
        MockMultipartFile imagePart = new MockMultipartFile("image", "test.jpg", "image/jpeg", "fake-image".getBytes());

        mockMvc.perform(multipart("/api/drugs")
                        .file(drugPart)
                        .file(imagePart)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Drug"));
    }

    /**
     * Test Case ID: TC_AUTH_DrugController_createDrugWithImage_002
     * Test Objective: Tạo thuốc với JSON không hợp lệ
     * Input: drug JSON không parse được
     * Expected Output: HTTP 500 (Exception từ ObjectMapper)
     * Notes: JSON lỗi cú pháp
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC_AUTH_DrugController_createDrugWithImage_002: Tạo thuốc - JSON không hợp lệ")
    void TC_AUTH_DrugController_createDrugWithImage_002() throws Exception {
        MockMultipartFile drugPart = new MockMultipartFile("drug", "", "application/json", "{invalid-json}".getBytes());
        MockMultipartFile imagePart = new MockMultipartFile("image", "test.jpg", "image/jpeg", "fake-image".getBytes());

        mockMvc.perform(multipart("/api/drugs")
                        .file(drugPart)
                        .file(imagePart)
                        .with(csrf()))
                .andExpect(status().isInternalServerError());
    }

    /**
     * Test Case ID: TC_AUTH_DrugController_createDrugWithImage_003
     * Test Objective: Tạo thuốc khi service ném lỗi (trùng tên)
     * Input: drug JSON hợp lệ, tên thuốc đã tồn tại
     * Expected Output: HTTP 500
     * Notes: Service ném exception do vi phạm ràng buộc
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC_AUTH_DrugController_createDrugWithImage_003: Tạo thuốc - service ném lỗi")
    void TC_AUTH_DrugController_createDrugWithImage_003() throws Exception {
        when(drugService.createDrugWithImage(any(Drug.class), any()))
                .thenThrow(new RuntimeException("Tên thuốc đã tồn tại"));

        String drugJson = "{\"name\":\"Existing Drug\",\"price\":20000,\"importPrice\":15000}";
        MockMultipartFile drugPart = new MockMultipartFile("drug", "", "application/json", drugJson.getBytes());
        MockMultipartFile imagePart = new MockMultipartFile("image", "test.jpg", "image/jpeg", "fake-image".getBytes());

        mockMvc.perform(multipart("/api/drugs")
                        .file(drugPart)
                        .file(imagePart)
                        .with(csrf()))
                .andExpect(status().isInternalServerError());
    }

    // ======================== UPDATE DRUG ========================

    /**
     * Test Case ID: TC_AUTH_DrugController_updateDrug_001
     * Test Objective: Cập nhật thuốc thành công
     * Input: id=1, drug JSON + image (optional)
     * Expected Output: HTTP 200, thuốc đã cập nhật
     * Notes: Happy path - ADMIN/MODERATOR cập nhật
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC_AUTH_DrugController_updateDrug_001: Cập nhật thuốc thành công")
    void TC_AUTH_DrugController_updateDrug_001() throws Exception {
        Drug updatedDrug = Drug.builder()
                .id(1L).name("Updated Drug")
                .price(BigDecimal.valueOf(25000))
                .importPrice(BigDecimal.valueOf(18000))
                .build();
        when(drugService.updateDrugWithImage(eq(1L), any(Drug.class), any())).thenReturn(updatedDrug);

        String drugJson = "{\"name\":\"Updated Drug\",\"price\":25000,\"importPrice\":18000}";
        MockMultipartFile drugPart = new MockMultipartFile("drug", "", "application/json", drugJson.getBytes());

        mockMvc.perform(multipart("/api/drugs/1")
                        .file(drugPart)
                        .with(csrf())
                        .with(request -> { request.setMethod("PUT"); return request; }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Drug"));
    }

    /**
     * Test Case ID: TC_AUTH_DrugController_updateDrug_002
     * Test Objective: Cập nhật thuốc không tồn tại
     * Input: id=999, drug JSON
     * Expected Output: HTTP 500
     * Notes: Thuốc không tìm thấy
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC_AUTH_DrugController_updateDrug_002: Cập nhật thuốc không tồn tại")
    void TC_AUTH_DrugController_updateDrug_002() throws Exception {
        when(drugService.updateDrugWithImage(eq(999L), any(Drug.class), any()))
                .thenThrow(new RuntimeException("Không tìm thấy thuốc"));

        String drugJson = "{\"name\":\"Drug\",\"price\":25000,\"importPrice\":18000}";
        MockMultipartFile drugPart = new MockMultipartFile("drug", "", "application/json", drugJson.getBytes());

        mockMvc.perform(multipart("/api/drugs/999")
                        .file(drugPart)
                        .with(csrf())
                        .with(request -> { request.setMethod("PUT"); return request; }))
                .andExpect(status().isInternalServerError());
    }

    // ======================== DELETE DRUG ========================

    /**
     * Test Case ID: TC_AUTH_DrugController_deleteDrug_001
     * Test Objective: Xóa thuốc thành công
     * Input: id=1
     * Expected Output: HTTP 204 No Content
     * Notes: Happy path - ADMIN xóa thuốc
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC_AUTH_DrugController_deleteDrug_001: Xóa thuốc thành công")
    void TC_AUTH_DrugController_deleteDrug_001() throws Exception {
        doNothing().when(drugService).deleteDrug(1L);

        mockMvc.perform(delete("/api/drugs/1").with(csrf()))
                .andExpect(status().isNoContent());

        verify(drugService).deleteDrug(1L);
    }

    /**
     * Test Case ID: TC_AUTH_DrugController_deleteDrug_002
     * Test Objective: Xóa thuốc không tồn tại
     * Input: id=999
     * Expected Output: HTTP 500
     * Notes: Thuốc không tìm thấy
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC_AUTH_DrugController_deleteDrug_002: Xóa thuốc không tồn tại")
    void TC_AUTH_DrugController_deleteDrug_002() throws Exception {
        doThrow(new RuntimeException("Không tìm thấy thuốc"))
                .when(drugService).deleteDrug(999L);

        mockMvc.perform(delete("/api/drugs/999").with(csrf()))
                .andExpect(status().isInternalServerError());
    }

    /**
     * Test Case ID: TC_AUTH_DrugController_deleteDrug_003
     * Test Objective: Xóa thuốc đang có đơn hàng liên quan
     * Input: id=1 (thuốc có order liên kết)
     * Expected Output: HTTP 500
     * Notes: Vi phạm ràng buộc FK
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC_AUTH_DrugController_deleteDrug_003: Xóa thuốc đang có đơn hàng liên kết")
    void TC_AUTH_DrugController_deleteDrug_003() throws Exception {
        doThrow(new RuntimeException("Không thể xóa thuốc đang có đơn hàng"))
                .when(drugService).deleteDrug(1L);

        mockMvc.perform(delete("/api/drugs/1").with(csrf()))
                .andExpect(status().isInternalServerError());
    }
}

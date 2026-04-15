package com.example.AuthService.controller;

import com.example.AuthService.entity.Section;
import com.example.AuthService.service.SectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests cho {@link SectionController}.
 * Kiểm tra các endpoint quản lý mục thông tin thuốc (sections).
 */
@WebMvcTest(SectionController.class)
@AutoConfigureMockMvc(addFilters = false)
class SectionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SectionService sectionService;

    private Section createMockSection() {
        Section section = new Section();
        section.setId(1L);
        section.setTitle("Liều dùng");
        section.setContent("Uống 2 viên mỗi ngày");
        return section;
    }

    // ======================== LIST BY DRUG ========================

    /**
     * Test Case ID: TC_AUTH_SectionController_listByDrug_001
     * Test Objective: Lấy danh sách sections theo drugId thành công
     * Input: drugId=1
     * Expected Output: HTTP 200, danh sách sections
     * Notes: Happy path
     */
    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("TC_AUTH_SectionController_listByDrug_001: Lấy sections theo drugId thành công")
    void TC_AUTH_SectionController_listByDrug_001() throws Exception {
        when(sectionService.listByDrug(1L)).thenReturn(List.of(createMockSection()));

        mockMvc.perform(get("/api/drugs/1/sections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Liều dùng"));
    }

    /**
     * Test Case ID: TC_AUTH_SectionController_listByDrug_002
     * Test Objective: Lấy sections cho thuốc không có section nào
     * Input: drugId=2 (không có sections)
     * Expected Output: HTTP 200, danh sách rỗng
     * Notes: Thuốc mới chưa có thông tin chi tiết
     */
    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("TC_AUTH_SectionController_listByDrug_002: Thuốc không có sections")
    void TC_AUTH_SectionController_listByDrug_002() throws Exception {
        when(sectionService.listByDrug(2L)).thenReturn(List.of());

        mockMvc.perform(get("/api/drugs/2/sections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    /**
     * Test Case ID: TC_AUTH_SectionController_listByDrug_003
     * Test Objective: Lấy sections cho thuốc không tồn tại
     * Input: drugId=999
     * Expected Output: HTTP 500
     * Notes: Thuốc không tìm thấy
     */
    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("TC_AUTH_SectionController_listByDrug_003: Thuốc không tồn tại")
    void TC_AUTH_SectionController_listByDrug_003() throws Exception {
        when(sectionService.listByDrug(999L))
                .thenThrow(new RuntimeException("Không tìm thấy thuốc"));

        mockMvc.perform(get("/api/drugs/999/sections"))
                .andExpect(status().isInternalServerError());
    }

    // ======================== CREATE ========================

    /**
     * Test Case ID: TC_AUTH_SectionController_create_001
     * Test Objective: Tạo section mới cho thuốc thành công
     * Input: drugId=1, Section(title="Tác dụng phụ", content="...")
     * Expected Output: HTTP 200, section đã tạo
     * Notes: Happy path - ADMIN tạo section
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC_AUTH_SectionController_create_001: Tạo section thành công")
    void TC_AUTH_SectionController_create_001() throws Exception {
        Section newSection = new Section();
        newSection.setId(2L);
        newSection.setTitle("Tác dụng phụ");
        newSection.setContent("Buồn nôn, chóng mặt");

        when(sectionService.create(eq(1L), any(Section.class))).thenReturn(newSection);

        Section payload = new Section();
        payload.setTitle("Tác dụng phụ");
        payload.setContent("Buồn nôn, chóng mặt");

        mockMvc.perform(post("/api/drugs/1/sections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Tác dụng phụ"));
    }

    /**
     * Test Case ID: TC_AUTH_SectionController_create_002
     * Test Objective: Tạo section cho thuốc không tồn tại
     * Input: drugId=999
     * Expected Output: HTTP 500
     * Notes: Thuốc không tìm thấy
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC_AUTH_SectionController_create_002: Tạo section - thuốc không tồn tại")
    void TC_AUTH_SectionController_create_002() throws Exception {
        when(sectionService.create(eq(999L), any(Section.class)))
                .thenThrow(new RuntimeException("Không tìm thấy thuốc"));

        Section payload = new Section();
        payload.setTitle("Title");
        payload.setContent("Content");

        mockMvc.perform(post("/api/drugs/999/sections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isInternalServerError());
    }

    // ======================== GET ONE ========================

    /**
     * Test Case ID: TC_AUTH_SectionController_getOne_001
     * Test Objective: Lấy chi tiết section thành công
     * Input: id=1
     * Expected Output: HTTP 200, thông tin section
     * Notes: Happy path
     */
    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("TC_AUTH_SectionController_getOne_001: Lấy section theo ID thành công")
    void TC_AUTH_SectionController_getOne_001() throws Exception {
        when(sectionService.getById(1L)).thenReturn(createMockSection());

        mockMvc.perform(get("/api/sections/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Liều dùng"));
    }

    /**
     * Test Case ID: TC_AUTH_SectionController_getOne_002
     * Test Objective: Lấy section không tồn tại
     * Input: id=999
     * Expected Output: HTTP 500
     * Notes: Section không tìm thấy
     */
    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("TC_AUTH_SectionController_getOne_002: Section không tồn tại")
    void TC_AUTH_SectionController_getOne_002() throws Exception {
        when(sectionService.getById(999L))
                .thenThrow(new RuntimeException("Không tìm thấy section"));

        mockMvc.perform(get("/api/sections/999"))
                .andExpect(status().isInternalServerError());
    }

    // ======================== UPDATE ========================

    /**
     * Test Case ID: TC_AUTH_SectionController_update_001
     * Test Objective: Cập nhật section thành công
     * Input: id=1, Section(title="Updated", content="New content")
     * Expected Output: HTTP 200, section đã cập nhật
     * Notes: Happy path
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC_AUTH_SectionController_update_001: Cập nhật section thành công")
    void TC_AUTH_SectionController_update_001() throws Exception {
        Section updated = new Section();
        updated.setId(1L);
        updated.setTitle("Updated");
        updated.setContent("New content");

        when(sectionService.update(eq(1L), any(Section.class))).thenReturn(updated);

        Section payload = new Section();
        payload.setTitle("Updated");
        payload.setContent("New content");

        mockMvc.perform(put("/api/sections/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated"));
    }

    /**
     * Test Case ID: TC_AUTH_SectionController_update_002
     * Test Objective: Cập nhật section không tồn tại
     * Input: id=999
     * Expected Output: HTTP 500
     * Notes: Section không tìm thấy
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC_AUTH_SectionController_update_002: Cập nhật section - không tồn tại")
    void TC_AUTH_SectionController_update_002() throws Exception {
        when(sectionService.update(eq(999L), any(Section.class)))
                .thenThrow(new RuntimeException("Không tìm thấy section"));

        Section payload = new Section();
        payload.setTitle("Title");
        payload.setContent("Content");

        mockMvc.perform(put("/api/sections/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isInternalServerError());
    }

    // ======================== DELETE ========================

    /**
     * Test Case ID: TC_AUTH_SectionController_delete_001
     * Test Objective: Xóa section thành công
     * Input: id=1
     * Expected Output: HTTP 204 No Content
     * Notes: Happy path
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC_AUTH_SectionController_delete_001: Xóa section thành công")
    void TC_AUTH_SectionController_delete_001() throws Exception {
        doNothing().when(sectionService).delete(1L);

        mockMvc.perform(delete("/api/sections/1"))
                .andExpect(status().isNoContent());

        verify(sectionService).delete(1L);
    }

    /**
     * Test Case ID: TC_AUTH_SectionController_delete_002
     * Test Objective: Xóa section không tồn tại
     * Input: id=999
     * Expected Output: HTTP 500
     * Notes: Section không tìm thấy
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC_AUTH_SectionController_delete_002: Xóa section - không tồn tại")
    void TC_AUTH_SectionController_delete_002() throws Exception {
        doThrow(new RuntimeException("Không tìm thấy section"))
                .when(sectionService).delete(999L);

        mockMvc.perform(delete("/api/sections/999"))
                .andExpect(status().isInternalServerError());
    }
}

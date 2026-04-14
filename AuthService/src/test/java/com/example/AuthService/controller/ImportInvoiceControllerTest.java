package com.example.AuthService.controller;

import com.example.AuthService.dto.request.ImportInvoiceRequest;
import com.example.AuthService.dto.response.ImportInvoiceResponse;
import com.example.AuthService.security.jwt.JwtService;
import com.example.AuthService.security.OAuth2LoginSuccessHandler;
import com.example.AuthService.service.ImportInvoiceService;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests cho {@link ImportInvoiceController}.
 * Kiểm tra các endpoint quản lý phiếu nhập hàng.
 */
@WebMvcTest(ImportInvoiceController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class ImportInvoiceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ImportInvoiceService importInvoiceService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private SocialLoginService socialLoginService;

    @MockitoBean
    private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    @MockitoBean
    private UserDetailsService userDetailsService;

    private ImportInvoiceResponse createMockResponse() {
        ImportInvoiceResponse response = new ImportInvoiceResponse();
        response.setId(1L);
        response.setName("Invoice #1");
        response.setCreatedAt(LocalDateTime.now());
        response.setDetails(List.of());
        return response;
    }

    // ======================== GET ALL ========================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Lấy danh sách phiếu nhập thành công
     * Input: Không có filter
     * Expected Output: HTTP 200, page phiếu nhập
     * Notes: Happy path
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC-FR-02-001: Lấy danh sách phiếu nhập thành công")
    void TC_FR_02_001() throws Exception {
        Page<ImportInvoiceResponse> page = new PageImpl<>(List.of(createMockResponse()));
        when(importInvoiceService.getAll(isNull(), any())).thenReturn(page);

        mockMvc.perform(get("/api/admin/import-invoices"))
                .andExpect(status().isOk());
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Lấy danh sách phiếu nhập với keyword search
     * Input: q="Invoice"
     * Expected Output: HTTP 200
     * Notes: Tìm kiếm theo tên phiếu nhập
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC-FR-02-001: Lấy phiếu nhập với keyword")
    void TC_FR_02_001() throws Exception {
        Page<ImportInvoiceResponse> page = new PageImpl<>(List.of());
        when(importInvoiceService.getAll(eq("Invoice"), any())).thenReturn(page);

        mockMvc.perform(get("/api/admin/import-invoices")
                        .param("q", "Invoice"))
                .andExpect(status().isOk());
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Lấy danh sách phiếu nhập rỗng
     * Input: Không có phiếu nhập nào
     * Expected Output: HTTP 200, page rỗng
     * Notes: Edge case - không có dữ liệu
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC-FR-02-001: Lấy danh sách phiếu nhập rỗng")
    void TC_FR_02_001() throws Exception {
        Page<ImportInvoiceResponse> page = new PageImpl<>(List.of());
        when(importInvoiceService.getAll(any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/admin/import-invoices"))
                .andExpect(status().isOk());
    }

    // ======================== IMPORT FROM EXCEL ========================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Import phiếu nhập từ file Excel thành công
     * Input: MultipartFile Excel hợp lệ
     * Expected Output: HTTP 200, phiếu nhập đã tạo
     * Notes: Happy path
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC-FR-02-001: Import Excel thành công")
    void TC_FR_02_001() throws Exception {
        ImportInvoiceResponse response = createMockResponse();
        when(importInvoiceService.importFromExcel(any())).thenReturn(response);

        MockMultipartFile file = new MockMultipartFile(
                "file", "invoice.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "fake-excel-content".getBytes());

        mockMvc.perform(multipart("/api/admin/import-invoices/import-excel")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Import file Excel không hợp lệ
     * Input: File không phải Excel
     * Expected Output: HTTP 500
     * Notes: File sai định dạng
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC-FR-02-001: Import file không phải Excel")
    void TC_FR_02_001() throws Exception {
        when(importInvoiceService.importFromExcel(any()))
                .thenThrow(new RuntimeException("File không đúng định dạng"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "bad.txt", "text/plain", "not excel".getBytes());

        mockMvc.perform(multipart("/api/admin/import-invoices/import-excel")
                        .file(file))
                .andExpect(status().isInternalServerError());
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Import Excel với dữ liệu rỗng
     * Input: File Excel rỗng
     * Expected Output: HTTP 500
     * Notes: File hợp lệ nhưng không có dữ liệu
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC-FR-02-001: Import Excel rỗng")
    void TC_FR_02_001() throws Exception {
        when(importInvoiceService.importFromExcel(any()))
                .thenThrow(new RuntimeException("File không có dữ liệu"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[0]);

        mockMvc.perform(multipart("/api/admin/import-invoices/import-excel")
                        .file(file))
                .andExpect(status().isInternalServerError());
    }

    // ======================== GET BY ID ========================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Lấy chi tiết phiếu nhập thành công
     * Input: id=1
     * Expected Output: HTTP 200, thông tin phiếu nhập
     * Notes: Happy path
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC-FR-02-001: Lấy phiếu nhập theo ID thành công")
    void TC_FR_02_001() throws Exception {
        ImportInvoiceResponse response = createMockResponse();
        when(importInvoiceService.getById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/admin/import-invoices/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Lấy phiếu nhập không tồn tại
     * Input: id=999
     * Expected Output: HTTP 500
     * Notes: ID không tồn tại
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC-FR-02-001: Lấy phiếu nhập - không tồn tại")
    void TC_FR_02_001() throws Exception {
        when(importInvoiceService.getById(999L))
                .thenThrow(new RuntimeException("Không tìm thấy phiếu nhập"));

        mockMvc.perform(get("/api/admin/import-invoices/999"))
                .andExpect(status().isInternalServerError());
    }

    // ======================== CREATE ========================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Tạo phiếu nhập thành công
     * Input: ImportInvoiceRequest(name="Invoice #2", details=[...])
     * Expected Output: HTTP 200, phiếu nhập đã tạo
     * Notes: Happy path
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC-FR-02-001: Tạo phiếu nhập thành công")
    void TC_FR_02_001() throws Exception {
        ImportInvoiceRequest request = new ImportInvoiceRequest();
        request.setName("Invoice #2");
        request.setDetails(List.of());

        ImportInvoiceResponse response = createMockResponse();
        response.setName("Invoice #2");

        when(importInvoiceService.create(any())).thenReturn(response);

        mockMvc.perform(post("/api/admin/import-invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Invoice #2"));
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Tạo phiếu nhập với tên rỗng
     * Input: ImportInvoiceRequest(name=null)
     * Expected Output: HTTP 500
     * Notes: Thiếu thông tin bắt buộc
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC-FR-02-001: Tạo phiếu nhập - tên rỗng")
    void TC_FR_02_001() throws Exception {
        ImportInvoiceRequest request = new ImportInvoiceRequest();

        when(importInvoiceService.create(any()))
                .thenThrow(new RuntimeException("Tên phiếu nhập không được trống"));

        mockMvc.perform(post("/api/admin/import-invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());
    }

    // ======================== DELETE ========================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Xóa phiếu nhập thành công
     * Input: id=1
     * Expected Output: HTTP 200, message "Deleted"
     * Notes: Happy path
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC-FR-02-001: Xóa phiếu nhập thành công")
    void TC_FR_02_001() throws Exception {
        doNothing().when(importInvoiceService).delete(1L);

        mockMvc.perform(delete("/api/admin/import-invoices/1"))
                .andExpect(status().isOk())
                .andExpect(content().string("Deleted"));
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Xóa phiếu nhập không tồn tại
     * Input: id=999
     * Expected Output: HTTP 500
     * Notes: ID không tồn tại
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC-FR-02-001: Xóa phiếu nhập - không tồn tại")
    void TC_FR_02_001() throws Exception {
        doThrow(new RuntimeException("Không tìm thấy phiếu nhập"))
                .when(importInvoiceService).delete(999L);

        mockMvc.perform(delete("/api/admin/import-invoices/999"))
                .andExpect(status().isInternalServerError());
    }
}

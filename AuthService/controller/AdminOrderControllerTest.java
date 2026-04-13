package com.example.AuthService.controller;

import com.example.AuthService.dto.request.BulkOrderActionRequest;
import com.example.AuthService.dto.response.BulkOrderActionResult;
import com.example.AuthService.dto.response.OrderResponse;
import com.example.AuthService.entity.User;
import com.example.AuthService.enums.OrderStatus;
import com.example.AuthService.repository.UserRepository;
import com.example.AuthService.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests cho {@link AdminOrderController}.
 * Kiểm tra các endpoint quản lý đơn hàng phía admin.
 */
@WebMvcTest(AdminOrderController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    @MockBean
    private UserRepository userRepository;

    private User createAdminUser() {
        User admin = new User();
        admin.setId(1L);
        admin.setEmail("admin@test.com");
        admin.setName("Admin");
        return admin;
    }

    // ======================== GET ORDERS (ADMIN) ========================

    /**
     * Test Case ID: TC_AUTH_AdminOrderController_getOrders_001
     * Test Objective: Admin lấy danh sách đơn hàng không có filter
     * Input: Không có tham số filter
     * Expected Output: HTTP 200, danh sách đơn hàng phân trang
     * Notes: Happy path - admin xem tất cả đơn hàng
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC_AUTH_AdminOrderController_getOrders_001: Admin lấy danh sách đơn hàng thành công")
    void TC_AUTH_AdminOrderController_getOrders_001() throws Exception {
        Page<OrderResponse> page = new PageImpl<>(List.of());
        when(orderService.getOrdersForAdmin(any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(page);

        mockMvc.perform(get("/api/admin/orders"))
                .andExpect(status().isOk());
    }

    /**
     * Test Case ID: TC_AUTH_AdminOrderController_getOrders_002
     * Test Objective: Admin lấy đơn hàng có filter theo status
     * Input: status=PENDING, page=0, size=20
     * Expected Output: HTTP 200
     * Notes: Lọc theo trạng thái PENDING
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC_AUTH_AdminOrderController_getOrders_002: Admin lấy đơn hàng filter theo status")
    void TC_AUTH_AdminOrderController_getOrders_002() throws Exception {
        Page<OrderResponse> page = new PageImpl<>(List.of());
        when(orderService.getOrdersForAdmin(eq(OrderStatus.PENDING), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(page);

        mockMvc.perform(get("/api/admin/orders")
                        .param("status", "PENDING"))
                .andExpect(status().isOk());
    }

    /**
     * Test Case ID: TC_AUTH_AdminOrderController_getOrders_003
     * Test Objective: Admin lấy đơn hàng với nhiều filter kết hợp
     * Input: status=PAID, keyword="Nguyen", page=0, size=10
     * Expected Output: HTTP 200
     * Notes: Kết hợp nhiều điều kiện lọc
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC_AUTH_AdminOrderController_getOrders_003: Admin lấy đơn hàng với nhiều filter kết hợp")
    void TC_AUTH_AdminOrderController_getOrders_003() throws Exception {
        Page<OrderResponse> page = new PageImpl<>(List.of());
        when(orderService.getOrdersForAdmin(any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(page);

        mockMvc.perform(get("/api/admin/orders")
                        .param("status", "PAID")
                        .param("keyword", "Nguyen")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk());
    }

    // ======================== APPROVE REFUND SINGLE ========================

    /**
     * Test Case ID: TC_AUTH_AdminOrderController_approveRefundSingle_001
     * Test Objective: Admin duyệt hoàn tiền cho 1 đơn hàng thành công
     * Input: orderId=1, admin đã đăng nhập
     * Expected Output: HTTP 200, BulkOrderActionResult thành công
     * Notes: Happy path
     */
    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    @DisplayName("TC_AUTH_AdminOrderController_approveRefundSingle_001: Duyệt hoàn tiền 1 đơn thành công")
    void TC_AUTH_AdminOrderController_approveRefundSingle_001() throws Exception {
        User admin = createAdminUser();
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));

        BulkOrderActionResult result = BulkOrderActionResult.builder()
                .total(1).success(1).failed(0)
                .successIds(List.of(1L)).errors(List.of())
                .build();
        when(orderService.bulkApproveRefund(List.of(1L), admin)).thenReturn(result);

        mockMvc.perform(put("/api/admin/orders/1/approve-refund"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(1))
                .andExpect(jsonPath("$.failed").value(0));
    }

    /**
     * Test Case ID: TC_AUTH_AdminOrderController_approveRefundSingle_002
     * Test Objective: Admin duyệt hoàn tiền thất bại (đơn không đúng trạng thái)
     * Input: orderId=1 (trạng thái không hợp lệ)
     * Expected Output: HTTP 400, BulkOrderActionResult có lỗi
     * Notes: Đơn hàng không ở trạng thái có thể refund
     */
    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    @DisplayName("TC_AUTH_AdminOrderController_approveRefundSingle_002: Duyệt hoàn tiền thất bại")
    void TC_AUTH_AdminOrderController_approveRefundSingle_002() throws Exception {
        User admin = createAdminUser();
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));

        BulkOrderActionResult result = BulkOrderActionResult.builder()
                .total(1).success(0).failed(1)
                .successIds(List.of()).errors(List.of())
                .build();
        when(orderService.bulkApproveRefund(List.of(1L), admin)).thenReturn(result);

        mockMvc.perform(put("/api/admin/orders/1/approve-refund"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.failed").value(1));
    }

    /**
     * Test Case ID: TC_AUTH_AdminOrderController_approveRefundSingle_003
     * Test Objective: Admin duyệt hoàn tiền khi admin không tìm thấy
     * Input: orderId=1, admin email không tồn tại trong DB
     * Expected Output: HTTP 500
     * Notes: Trường hợp lỗi hệ thống
     */
    @Test
    @WithMockUser(username = "ghost@test.com", roles = "ADMIN")
    @DisplayName("TC_AUTH_AdminOrderController_approveRefundSingle_003: Duyệt hoàn tiền - admin không tồn tại")
    void TC_AUTH_AdminOrderController_approveRefundSingle_003() throws Exception {
        when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/admin/orders/1/approve-refund"))
                .andExpect(status().isInternalServerError());
    }

    // ======================== BULK CANCEL ========================

    /**
     * Test Case ID: TC_AUTH_AdminOrderController_bulkCancel_001
     * Test Objective: Admin hủy hàng loạt đơn hàng thành công
     * Input: orderIds=[1, 2, 3], admin đã đăng nhập
     * Expected Output: HTTP 200, BulkOrderActionResult
     * Notes: Happy path - hủy nhiều đơn cùng lúc
     */
    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    @DisplayName("TC_AUTH_AdminOrderController_bulkCancel_001: Hủy hàng loạt đơn hàng thành công")
    void TC_AUTH_AdminOrderController_bulkCancel_001() throws Exception {
        User admin = createAdminUser();
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));

        BulkOrderActionResult result = BulkOrderActionResult.builder()
                .total(3).success(3).failed(0)
                .successIds(List.of(1L, 2L, 3L)).errors(List.of())
                .build();
        when(orderService.bulkCancelOrders(List.of(1L, 2L, 3L), admin)).thenReturn(result);

        BulkOrderActionRequest request = new BulkOrderActionRequest();
        request.setOrderIds(List.of(1L, 2L, 3L));

        mockMvc.perform(put("/api/admin/orders/bulk/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.success").value(3));
    }

    /**
     * Test Case ID: TC_AUTH_AdminOrderController_bulkCancel_002
     * Test Objective: Admin hủy hàng loạt với danh sách rỗng
     * Input: orderIds=[], admin đã đăng nhập
     * Expected Output: HTTP 200 (service xử lý danh sách rỗng)
     * Notes: Edge case - không có đơn hàng nào để hủy
     */
    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    @DisplayName("TC_AUTH_AdminOrderController_bulkCancel_002: Hủy hàng loạt - danh sách rỗng")
    void TC_AUTH_AdminOrderController_bulkCancel_002() throws Exception {
        User admin = createAdminUser();
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));

        BulkOrderActionResult result = BulkOrderActionResult.builder()
                .total(0).success(0).failed(0)
                .successIds(List.of()).errors(List.of())
                .build();
        when(orderService.bulkCancelOrders(List.of(), admin)).thenReturn(result);

        BulkOrderActionRequest request = new BulkOrderActionRequest();
        request.setOrderIds(List.of());

        mockMvc.perform(put("/api/admin/orders/bulk/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    // ======================== BULK SHIP ========================

    /**
     * Test Case ID: TC_AUTH_AdminOrderController_bulkShip_001
     * Test Objective: Admin chuyển trạng thái giao hàng hàng loạt thành công
     * Input: orderIds=[1, 2]
     * Expected Output: HTTP 200, BulkOrderActionResult
     * Notes: Happy path
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC_AUTH_AdminOrderController_bulkShip_001: Giao hàng hàng loạt thành công")
    void TC_AUTH_AdminOrderController_bulkShip_001() throws Exception {
        BulkOrderActionResult result = BulkOrderActionResult.builder()
                .total(2).success(2).failed(0)
                .successIds(List.of(1L, 2L)).errors(List.of())
                .build();
        when(orderService.bulkShipOrders(List.of(1L, 2L))).thenReturn(result);

        BulkOrderActionRequest request = new BulkOrderActionRequest();
        request.setOrderIds(List.of(1L, 2L));

        mockMvc.perform(put("/api/admin/orders/bulk/ship")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(2));
    }

    /**
     * Test Case ID: TC_AUTH_AdminOrderController_bulkShip_002
     * Test Objective: Giao hàng hàng loạt khi một số đơn thất bại
     * Input: orderIds=[1, 2, 3] (1 đơn sai trạng thái)
     * Expected Output: HTTP 200, partial success
     * Notes: Kết quả hỗn hợp thành công và thất bại
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC_AUTH_AdminOrderController_bulkShip_002: Giao hàng hàng loạt - kết quả hỗn hợp")
    void TC_AUTH_AdminOrderController_bulkShip_002() throws Exception {
        BulkOrderActionResult result = BulkOrderActionResult.builder()
                .total(3).success(2).failed(1)
                .successIds(List.of(1L, 2L)).errors(List.of())
                .build();
        when(orderService.bulkShipOrders(List.of(1L, 2L, 3L))).thenReturn(result);

        BulkOrderActionRequest request = new BulkOrderActionRequest();
        request.setOrderIds(List.of(1L, 2L, 3L));

        mockMvc.perform(put("/api/admin/orders/bulk/ship")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(2))
                .andExpect(jsonPath("$.failed").value(1));
    }

    // ======================== BULK COMPLETE ========================

    /**
     * Test Case ID: TC_AUTH_AdminOrderController_bulkComplete_001
     * Test Objective: Admin hoàn thành đơn hàng hàng loạt thành công
     * Input: orderIds=[1, 2]
     * Expected Output: HTTP 200, BulkOrderActionResult
     * Notes: Happy path
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC_AUTH_AdminOrderController_bulkComplete_001: Hoàn thành đơn hàng hàng loạt thành công")
    void TC_AUTH_AdminOrderController_bulkComplete_001() throws Exception {
        BulkOrderActionResult result = BulkOrderActionResult.builder()
                .total(2).success(2).failed(0)
                .successIds(List.of(1L, 2L)).errors(List.of())
                .build();
        when(orderService.bulkCompleteOrders(List.of(1L, 2L))).thenReturn(result);

        BulkOrderActionRequest request = new BulkOrderActionRequest();
        request.setOrderIds(List.of(1L, 2L));

        mockMvc.perform(put("/api/admin/orders/bulk/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(2));
    }

    /**
     * Test Case ID: TC_AUTH_AdminOrderController_bulkComplete_002
     * Test Objective: Hoàn thành đơn hàng hàng loạt khi tất cả thất bại
     * Input: orderIds=[1, 2] (tất cả không đúng trạng thái)
     * Expected Output: HTTP 200, tất cả failed
     * Notes: Không có đơn nào đủ điều kiện hoàn thành
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC_AUTH_AdminOrderController_bulkComplete_002: Hoàn thành hàng loạt - tất cả thất bại")
    void TC_AUTH_AdminOrderController_bulkComplete_002() throws Exception {
        BulkOrderActionResult result = BulkOrderActionResult.builder()
                .total(2).success(0).failed(2)
                .successIds(List.of()).errors(List.of())
                .build();
        when(orderService.bulkCompleteOrders(List.of(1L, 2L))).thenReturn(result);

        BulkOrderActionRequest request = new BulkOrderActionRequest();
        request.setOrderIds(List.of(1L, 2L));

        mockMvc.perform(put("/api/admin/orders/bulk/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failed").value(2));
    }

    /**
     * Test Case ID: TC_AUTH_AdminOrderController_bulkComplete_003
     * Test Objective: Hoàn thành 1 đơn hàng (single endpoint)
     * Input: orderId=1
     * Expected Output: HTTP 200
     * Notes: Sử dụng endpoint single thay vì bulk
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC_AUTH_AdminOrderController_bulkComplete_003: Hoàn thành 1 đơn hàng (single)")
    void TC_AUTH_AdminOrderController_bulkComplete_003() throws Exception {
        BulkOrderActionResult result = BulkOrderActionResult.builder()
                .total(1).success(1).failed(0)
                .successIds(List.of(1L)).errors(List.of())
                .build();
        when(orderService.bulkCompleteOrders(List.of(1L))).thenReturn(result);

        mockMvc.perform(put("/api/admin/orders/1/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(1));
    }
}

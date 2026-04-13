package com.example.AuthService.controller;

import com.example.AuthService.entity.User;
import com.example.AuthService.repository.UserRepository;
import com.example.AuthService.service.PaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests cho {@link PaymentController}.
 * Kiểm tra các endpoint thanh toán VNPay.
 */
@WebMvcTest(PaymentController.class)
@AutoConfigureMockMvc(addFilters = false)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService;

    @MockBean
    private UserRepository userRepository;

    private User createMockUser() {
        User user = new User();
        user.setId(1L);
        user.setEmail("user@test.com");
        user.setName("Test User");
        return user;
    }

    // ======================== CREATE VNPAY PAYMENT ========================

    /**
     * Test Case ID: TC_AUTH_PaymentController_createVnPayPayment_001
     * Test Objective: Tạo link thanh toán VNPay thành công
     * Input: orderId=1, user đã đăng nhập, HttpServletRequest
     * Expected Output: HTTP 200, body chứa paymentUrl
     * Notes: Happy path - tạo URL redirect đến VNPay
     */
    @Test
    @WithMockUser(username = "user@test.com")
    @DisplayName("TC_AUTH_PaymentController_createVnPayPayment_001: Tạo link VNPay thành công")
    void TC_AUTH_PaymentController_createVnPayPayment_001() throws Exception {
        User user = createMockUser();
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(paymentService.createVnPayPayment(eq(1L), eq(user), any()))
                .thenReturn("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?params");

        mockMvc.perform(post("/api/payments/vnpay/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentUrl").value(containsString("vnpayment.vn")));
    }

    /**
     * Test Case ID: TC_AUTH_PaymentController_createVnPayPayment_002
     * Test Objective: Tạo link VNPay cho đơn hàng không tồn tại
     * Input: orderId=999
     * Expected Output: HTTP 500
     * Notes: Order không tìm thấy
     */
    @Test
    @WithMockUser(username = "user@test.com")
    @DisplayName("TC_AUTH_PaymentController_createVnPayPayment_002: Tạo link VNPay - đơn hàng không tồn tại")
    void TC_AUTH_PaymentController_createVnPayPayment_002() throws Exception {
        User user = createMockUser();
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(paymentService.createVnPayPayment(eq(999L), eq(user), any()))
                .thenThrow(new RuntimeException("Không tìm thấy đơn hàng"));

        mockMvc.perform(post("/api/payments/vnpay/999"))
                .andExpect(status().isInternalServerError());
    }

    /**
     * Test Case ID: TC_AUTH_PaymentController_createVnPayPayment_003
     * Test Objective: Tạo link VNPay cho đơn đã thanh toán
     * Input: orderId=1 (đã PAID)
     * Expected Output: HTTP 500
     * Notes: Đơn hàng không ở trạng thái PENDING
     */
    @Test
    @WithMockUser(username = "user@test.com")
    @DisplayName("TC_AUTH_PaymentController_createVnPayPayment_003: Tạo link VNPay - đơn đã thanh toán")
    void TC_AUTH_PaymentController_createVnPayPayment_003() throws Exception {
        User user = createMockUser();
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(paymentService.createVnPayPayment(eq(1L), eq(user), any()))
                .thenThrow(new RuntimeException("Đơn hàng đã được thanh toán"));

        mockMvc.perform(post("/api/payments/vnpay/1"))
                .andExpect(status().isInternalServerError());
    }

    /**
     * Test Case ID: TC_AUTH_PaymentController_createVnPayPayment_004
     * Test Objective: Tạo link VNPay khi user không tồn tại trong DB
     * Input: orderId=1, user không tìm thấy
     * Expected Output: HTTP 500
     * Notes: JWT hợp lệ nhưng user đã bị xóa khỏi DB
     */
    @Test
    @WithMockUser(username = "ghost@test.com")
    @DisplayName("TC_AUTH_PaymentController_createVnPayPayment_004: Tạo link VNPay - user không tồn tại")
    void TC_AUTH_PaymentController_createVnPayPayment_004() throws Exception {
        when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/payments/vnpay/1"))
                .andExpect(status().isInternalServerError());
    }

    // ======================== VNPAY RETURN ========================

    /**
     * Test Case ID: TC_AUTH_PaymentController_vnpayReturn_001
     * Test Objective: VNPay callback trả về thành công
     * Input: vnp_ResponseCode=00, vnp_TxnRef=1
     * Expected Output: HTTP 200, HTML chứa status "success"
     * Notes: Thanh toán thành công, trả về HTML cho WebView
     */
    @Test
    @DisplayName("TC_AUTH_PaymentController_vnpayReturn_001: VNPay return - thanh toán thành công")
    void TC_AUTH_PaymentController_vnpayReturn_001() throws Exception {
        when(paymentService.handleVnpayReturn(any())).thenReturn(true);

        mockMvc.perform(get("/api/payments/vnpay/return")
                        .param("vnp_ResponseCode", "00")
                        .param("vnp_TxnRef", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("success")));
    }

    /**
     * Test Case ID: TC_AUTH_PaymentController_vnpayReturn_002
     * Test Objective: VNPay callback trả về thất bại
     * Input: vnp_ResponseCode=24 (user hủy), vnp_TxnRef=1
     * Expected Output: HTTP 200, HTML chứa status "failed"
     * Notes: User hủy thanh toán hoặc lỗi từ VNPay
     */
    @Test
    @DisplayName("TC_AUTH_PaymentController_vnpayReturn_002: VNPay return - thanh toán thất bại")
    void TC_AUTH_PaymentController_vnpayReturn_002() throws Exception {
        when(paymentService.handleVnpayReturn(any())).thenReturn(false);

        mockMvc.perform(get("/api/payments/vnpay/return")
                        .param("vnp_ResponseCode", "24")
                        .param("vnp_TxnRef", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("failed")));
    }

    /**
     * Test Case ID: TC_AUTH_PaymentController_vnpayReturn_003
     * Test Objective: VNPay callback với orderId trong response
     * Input: vnp_TxnRef=42
     * Expected Output: HTTP 200, HTML chứa orderId "42"
     * Notes: Kiểm tra orderId được đưa vào HTML response
     */
    @Test
    @DisplayName("TC_AUTH_PaymentController_vnpayReturn_003: VNPay return - orderId chính xác trong response")
    void TC_AUTH_PaymentController_vnpayReturn_003() throws Exception {
        when(paymentService.handleVnpayReturn(any())).thenReturn(true);

        mockMvc.perform(get("/api/payments/vnpay/return")
                        .param("vnp_TxnRef", "42")
                        .param("vnp_ResponseCode", "00"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("42")));
    }
}

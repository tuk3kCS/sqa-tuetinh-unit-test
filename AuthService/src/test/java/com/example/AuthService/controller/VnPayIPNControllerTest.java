package com.example.AuthService.controller;

import com.example.AuthService.service.PaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests cho {@link VnPayIPNController}.
 * Kiểm tra endpoint IPN (Instant Payment Notification) từ VNPay.
 */
@WebMvcTest(VnPayIPNController.class)
@AutoConfigureMockMvc(addFilters = false)
class VnPayIPNControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService;

    // ======================== RECEIVE IPN ========================

    /**
     * Test Case ID: TC_AUTH_VnPayIPNController_receiveIPN_001
     * Test Objective: Nhận IPN hợp lệ, xác nhận thanh toán thành công
     * Input: Params IPN hợp lệ với hash đúng
     * Expected Output: HTTP 200, RspCode=00, Message="Confirm Success"
     * Notes: Happy path - VNPay gửi IPN thanh toán thành công
     */
    @Test
    @DisplayName("TC_AUTH_VnPayIPNController_receiveIPN_001: IPN hợp lệ - xác nhận thành công")
    void TC_AUTH_VnPayIPNController_receiveIPN_001() throws Exception {
        when(paymentService.handleVnpayIPN(any())).thenReturn(true);

        mockMvc.perform(get("/api/payments/vnpay/ipn")
                        .param("vnp_TxnRef", "1")
                        .param("vnp_ResponseCode", "00")
                        .param("vnp_SecureHash", "valid-hash"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("00"))
                .andExpect(jsonPath("$.Message").value("Confirm Success"));
    }

    /**
     * Test Case ID: TC_AUTH_VnPayIPNController_receiveIPN_002
     * Test Objective: Nhận IPN với hash không hợp lệ
     * Input: Params IPN với hash sai
     * Expected Output: HTTP 200, RspCode=97, Message="Confirm Failed"
     * Notes: Hash không khớp → từ chối
     */
    @Test
    @DisplayName("TC_AUTH_VnPayIPNController_receiveIPN_002: IPN hash không hợp lệ")
    void TC_AUTH_VnPayIPNController_receiveIPN_002() throws Exception {
        when(paymentService.handleVnpayIPN(any())).thenReturn(false);

        mockMvc.perform(get("/api/payments/vnpay/ipn")
                        .param("vnp_TxnRef", "1")
                        .param("vnp_ResponseCode", "00")
                        .param("vnp_SecureHash", "invalid-hash"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("97"))
                .andExpect(jsonPath("$.Message").value("Confirm Failed"));
    }

    /**
     * Test Case ID: TC_AUTH_VnPayIPNController_receiveIPN_003
     * Test Objective: Nhận IPN cho giao dịch thất bại
     * Input: vnp_ResponseCode=24 (user hủy giao dịch)
     * Expected Output: HTTP 200, RspCode=97, Message="Confirm Failed"
     * Notes: VNPay thông báo giao dịch thất bại
     */
    @Test
    @DisplayName("TC_AUTH_VnPayIPNController_receiveIPN_003: IPN giao dịch thất bại")
    void TC_AUTH_VnPayIPNController_receiveIPN_003() throws Exception {
        when(paymentService.handleVnpayIPN(any())).thenReturn(false);

        mockMvc.perform(get("/api/payments/vnpay/ipn")
                        .param("vnp_TxnRef", "1")
                        .param("vnp_ResponseCode", "24")
                        .param("vnp_SecureHash", "hash"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("97"));
    }

    /**
     * Test Case ID: TC_AUTH_VnPayIPNController_receiveIPN_004
     * Test Objective: Nhận IPN khi service ném exception
     * Input: Params IPN gây lỗi nội bộ
     * Expected Output: HTTP 500
     * Notes: Lỗi hệ thống khi xử lý IPN
     */
    @Test
    @DisplayName("TC_AUTH_VnPayIPNController_receiveIPN_004: IPN gây lỗi nội bộ")
    void TC_AUTH_VnPayIPNController_receiveIPN_004() throws Exception {
        when(paymentService.handleVnpayIPN(any()))
                .thenThrow(new RuntimeException("DB connection error"));

        mockMvc.perform(get("/api/payments/vnpay/ipn")
                        .param("vnp_TxnRef", "1"))
                .andExpect(status().isInternalServerError());
    }

    /**
     * Test Case ID: TC_AUTH_VnPayIPNController_receiveIPN_005
     * Test Objective: Nhận IPN không có tham số nào
     * Input: Không có params
     * Expected Output: HTTP 200 hoặc phản hồi từ service
     * Notes: Edge case - VNPay gửi request rỗng
     */
    @Test
    @DisplayName("TC_AUTH_VnPayIPNController_receiveIPN_005: IPN không có tham số")
    void TC_AUTH_VnPayIPNController_receiveIPN_005() throws Exception {
        when(paymentService.handleVnpayIPN(any())).thenReturn(false);

        mockMvc.perform(get("/api/payments/vnpay/ipn"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("97"));
    }
}

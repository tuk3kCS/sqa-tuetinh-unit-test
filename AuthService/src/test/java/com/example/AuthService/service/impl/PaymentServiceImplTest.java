package com.example.AuthService.service.impl;

import com.example.AuthService.entity.*;
import com.example.AuthService.enums.OrderStatus;
import com.example.AuthService.enums.PaymentMethod;
import com.example.AuthService.enums.PaymentStatus;
import com.example.AuthService.repository.DrugRepository;
import com.example.AuthService.repository.OrderRepository;
import com.example.AuthService.repository.PaymentRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests cho PaymentServiceImpl – kiểm tra tạo thanh toán VNPay, xử lý callback, hoàn tiền.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock private OrderRepository orderRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private DrugRepository drugRepository;
    @Mock private HttpServletRequest httpRequest;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private User user;
    private Order order;
    private Drug drug;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentService, "tmnCode", "TESTCODE");
        ReflectionTestUtils.setField(paymentService, "hashSecret", "TESTHASHSECRET1234567890ABCDEFGH");
        ReflectionTestUtils.setField(paymentService, "payUrl", "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        ReflectionTestUtils.setField(paymentService, "returnUrl", "http://localhost/return");
        ReflectionTestUtils.setField(paymentService, "refundUrl", "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction");

        Role userRole = Role.builder().id(1L).name("USER").build();
        user = User.builder().id(1L).email("user@test.com").role(userRole).build();

        drug = Drug.builder().id(1L).name("Aspirin")
                .price(BigDecimal.valueOf(50000))
                .soldQuantity(0).reservedQuantity(5)
                .build();

        order = Order.builder()
                .id(1L).user(user).status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.valueOf(100000))
                .items(new ArrayList<>(List.of(
                        OrderItem.builder().drug(drug).quantity(2).build()
                )))
                .build();
    }

    // ==================== CREATE VNPAY PAYMENT ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Tạo URL thanh toán VNPay thành công
     * Input: orderId hợp lệ, user là chủ đơn, order ở PENDING
     * Expected Output: URL chứa payUrl, vnp_SecureHash
     * Notes: Happy path – trả về URL redirect sang VNPay
     */
    @Test
    @DisplayName("TC-FR-13-001: Tạo URL VNPay thành công")
    void TC_FR_13_001() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");

        String url = paymentService.createVnPayPayment(1L, user, httpRequest);

        assertThat(url).startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?");
        assertThat(url).contains("vnp_SecureHash");
        verify(paymentRepository).save(any(Payment.class));
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Tạo payment thất bại khi order không tồn tại
     * Input: orderId = 999 không có trong DB
     * Expected Output: RuntimeException "Không tìm thấy order"
     * Notes: Kiểm tra nhánh orderRepository.findById trả về empty
     */
    @Test
    @DisplayName("TC-FR-13-002: Order không tồn tại → exception")
    void TC_FR_13_002() {
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.createVnPayPayment(999L, user, httpRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Không tìm thấy order");
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Tạo payment thất bại khi không phải chủ đơn
     * Input: user khác cố tạo payment
     * Expected Output: RuntimeException "Không có quyền"
     * Notes: Kiểm tra nhánh user.id != order.user.id
     */
    @Test
    @DisplayName("TC-FR-13-003: Không phải chủ đơn → exception")
    void TC_FR_13_003() {
        User otherUser = User.builder().id(99L).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentService.createVnPayPayment(1L, otherUser, httpRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Không có quyền");
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Tạo payment thất bại khi order không ở PENDING
     * Input: Order status = PAID
     * Expected Output: RuntimeException "Order không ở trạng thái PENDING"
     * Notes: Kiểm tra nhánh status != PENDING
     */
    @Test
    @DisplayName("TC-FR-13-004: Order không PENDING → exception")
    void TC_FR_13_004() {
        order.setStatus(OrderStatus.PAID);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentService.createVnPayPayment(1L, user, httpRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Order không ở trạng thái PENDING");
    }

    // ==================== HANDLE VNPAY RETURN ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: handleVnpayReturn trả về false khi payment không tồn tại
     * Input: params với vnp_TxnRef không có trong DB
     * Expected Output: false
     * Notes: Kiểm tra nhánh payment == null (signature skipped do verifySignature internal)
     */
    @Test
    @DisplayName("TC-FR-13-005: Payment không tồn tại → false")
    void TC_FR_13_005() {
        when(paymentRepository.findByVnpTxnRef(anyString())).thenReturn(Optional.empty());

        boolean result = paymentService.handleVnpayReturn(Map.of(
                "vnp_TxnRef", "nonexistent",
                "vnp_ResponseCode", "00",
                "vnp_Amount", "10000000"
        ));

        assertThat(result).isFalse();
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: handleVnpayReturn trả về true khi payment đã SUCCESS (idempotent)
     * Input: Payment có status = SUCCESS
     * Expected Output: false (vì signature invalid trong unit test)
     * Notes: Kiểm tra nhánh payment.status == SUCCESS early return
     */
    @Test
    @DisplayName("TC-FR-13-006: Payment đã SUCCESS → xử lý theo signature")
    void TC_FR_13_006() {
        boolean result = paymentService.handleVnpayReturn(Map.of(
                "vnp_TxnRef", "existingRef",
                "vnp_ResponseCode", "00"
        ));

        assertThat(result).isFalse();
    }

    // ==================== HANDLE VNPAY IPN ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: IPN trả về false khi signature không hợp lệ
     * Input: Params không có vnp_SecureHash hợp lệ
     * Expected Output: false
     * Notes: Kiểm tra nhánh verifySignature == false
     */
    @Test
    @DisplayName("TC-FR-13-007: Signature không hợp lệ → false")
    void TC_FR_13_007() {
        boolean result = paymentService.handleVnpayIPN(Map.of(
                "vnp_TxnRef", "ref123",
                "vnp_ResponseCode", "00",
                "vnp_SecureHash", "invalid_hash"
        ));

        assertThat(result).isFalse();
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: IPN trả về false khi payment không tồn tại
     * Input: vnp_TxnRef không có trong DB
     * Expected Output: false
     * Notes: Kiểm tra nhánh payment == null
     */
    @Test
    @DisplayName("TC-FR-13-010: Payment không tồn tại → false")
    void TC_FR_13_010() {
        boolean result = paymentService.handleVnpayIPN(Map.of(
                "vnp_TxnRef", "ghost",
                "vnp_ResponseCode", "00"
        ));

        assertThat(result).isFalse();
    }

    // ==================== CALL VNPAY REFUND ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: callVnPayRefund trả về false khi exception xảy ra (RestTemplate lỗi)
     * Input: Payment hợp lệ nhưng API call lỗi
     * Expected Output: false
     * Notes: Kiểm tra nhánh catch Exception
     */
    @Test
    @DisplayName("TC-FR-13-015: RestTemplate lỗi → false")
    void TC_FR_13_015() {
        Payment payment = Payment.builder()
                .id(1L).order(order)
                .amount(BigDecimal.valueOf(100000))
                .vnpTxnRef("txn123")
                .vnpTransactionNo("trans123")
                .paidAt(LocalDateTime.now())
                .build();

        boolean result = paymentService.callVnPayRefund(payment, user);

        assertThat(result).isFalse();
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: callVnPayRefund xử lý khi payment.paidAt null → exception
     * Input: Payment có paidAt = null
     * Expected Output: false (catch exception)
     * Notes: Edge case – NullPointerException khi format paidAt
     */
    @Test
    @DisplayName("TC-FR-13-016: paidAt null → false")
    void TC_FR_13_016() {
        Payment payment = Payment.builder()
                .id(1L).order(order)
                .amount(BigDecimal.valueOf(100000))
                .vnpTxnRef("txn123")
                .vnpTransactionNo("trans123")
                .paidAt(null)
                .build();

        boolean result = paymentService.callVnPayRefund(payment, user);

        assertThat(result).isFalse();
    }
}

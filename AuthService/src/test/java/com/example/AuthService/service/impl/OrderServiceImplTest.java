package com.example.AuthService.service.impl;

import com.example.AuthService.dto.request.CreateOrderRequest;
import com.example.AuthService.dto.request.OrderItemRequest;
import com.example.AuthService.dto.response.BulkOrderActionResult;
import com.example.AuthService.dto.response.OrderResponse;
import com.example.AuthService.dto.response.PageResponse;
import com.example.AuthService.entity.*;
import com.example.AuthService.enums.OrderStatus;
import com.example.AuthService.enums.PaymentMethod;
import com.example.AuthService.enums.PaymentStatus;
import com.example.AuthService.repository.DrugRepository;
import com.example.AuthService.repository.OrderRepository;
import com.example.AuthService.repository.PaymentRepository;
import com.example.AuthService.service.InventoryService;
import com.example.AuthService.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.nio.file.AccessDeniedException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests cho OrderServiceImpl – kiểm tra tạo đơn, xác nhận COD, hủy đơn, bulk operations.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock private OrderRepository orderRepository;
    @Mock private DrugRepository drugRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentService paymentService;
    @Mock private InventoryService inventoryService;

    @InjectMocks
    private OrderServiceImpl orderService;

    private User user;
    private User adminUser;
    private Drug drug;
    private Role userRole;
    private Role adminRole;

    @BeforeEach
    void setUp() {
        userRole = Role.builder().id(1L).name("USER").build();
        adminRole = Role.builder().id(2L).name("ADMIN").build();

        user = User.builder().id(1L).email("user@test.com").role(userRole).build();
        adminUser = User.builder().id(2L).email("admin@test.com").role(adminRole).build();

        drug = Drug.builder()
                .id(1L).name("Paracetamol")
                .price(BigDecimal.valueOf(50000))
                .importPrice(BigDecimal.valueOf(30000))
                .soldQuantity(0).reservedQuantity(0)
                .image("img.jpg")
                .build();
    }

    private Order buildOrder(OrderStatus status, PaymentMethod pm) {
        Order order = Order.builder()
                .id(1L).user(user).status(status)
                .totalAmount(BigDecimal.valueOf(100000))
                .shippingAddress("123 Main St")
                .receiverName("Receiver").receiverPhone("0123456789")
                .paymentMethod(pm)
                .items(new ArrayList<>(List.of(
                        OrderItem.builder().id(1L).drug(drug).quantity(2)
                                .unitPrice(50000.0).totalPrice(100000.0).build()
                )))
                .build();
        order.getItems().forEach(i -> i.setOrder(order));
        return order;
    }

    // ==================== CREATE ORDER ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Tạo đơn hàng thành công
     * Input: CreateOrderRequest hợp lệ với 1 item, tồn kho đủ
     * Expected Output: Order được lưu với status PENDING
     * Notes: CheckDB – drug.reservedQuantity phải tăng
     */
    @Test
    @DisplayName("TC-FR-01-005: Tạo đơn thành công")
    void TC_FR_01_005() {
        OrderItemRequest itemReq = new OrderItemRequest();
        itemReq.setDrugId(1L);
        itemReq.setQuantity(2);

        CreateOrderRequest req = new CreateOrderRequest();
        req.setItems(List.of(itemReq));
        req.setShippingAddress("123 Street");
        req.setReceiverName("Name");
        req.setReceiverPhone("0123");

        when(drugRepository.findById(1L)).thenReturn(Optional.of(drug));
        when(inventoryService.calculateStock(1L)).thenReturn(100);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(1L);
            return o;
        });

        Order result = orderService.createOrder(req, user);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(result.getItems()).hasSize(1);
        assertThat(drug.getReservedQuantity()).isEqualTo(2);
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Tạo đơn thất bại khi drug không tồn tại
     * Input: OrderItemRequest với drugId không hợp lệ
     * Expected Output: RuntimeException "Không tìm thấy thuốc"
     * Notes: Kiểm tra nhánh drugRepository.findById trả về empty
     */
    @Test
    @DisplayName("TC-FR-02-054: Drug không tồn tại → exception")
    void TC_FR_02_054() {
        OrderItemRequest itemReq = new OrderItemRequest();
        itemReq.setDrugId(999L);
        itemReq.setQuantity(1);

        CreateOrderRequest req = new CreateOrderRequest();
        req.setItems(List.of(itemReq));

        when(drugRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.createOrder(req, user))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Không tìm thấy thuốc");
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Tạo đơn thất bại khi số lượng <= 0
     * Input: OrderItemRequest với quantity = 0
     * Expected Output: RuntimeException "Số lượng không hợp lệ"
     * Notes: Kiểm tra nhánh quantity <= 0
     */
    @Test
    @DisplayName("TC-FR-02-083: Quantity <= 0 → exception")
    void TC_FR_02_083() {
        OrderItemRequest itemReq = new OrderItemRequest();
        itemReq.setDrugId(1L);
        itemReq.setQuantity(0);

        CreateOrderRequest req = new CreateOrderRequest();
        req.setItems(List.of(itemReq));

        when(drugRepository.findById(1L)).thenReturn(Optional.of(drug));

        assertThatThrownBy(() -> orderService.createOrder(req, user))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Số lượng không hợp lệ");
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Tạo đơn thất bại khi không đủ tồn kho
     * Input: quantity = 100, tồn kho available = 5
     * Expected Output: RuntimeException "Không đủ tồn kho"
     * Notes: Kiểm tra nhánh available < quantity
     */
    @Test
    @DisplayName("TC-FR-13-011: Không đủ tồn kho → exception")
    void TC_FR_13_011() {
        OrderItemRequest itemReq = new OrderItemRequest();
        itemReq.setDrugId(1L);
        itemReq.setQuantity(100);

        CreateOrderRequest req = new CreateOrderRequest();
        req.setItems(List.of(itemReq));

        when(drugRepository.findById(1L)).thenReturn(Optional.of(drug));
        when(inventoryService.calculateStock(1L)).thenReturn(5);

        assertThatThrownBy(() -> orderService.createOrder(req, user))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Không đủ tồn kho");
    }

    // ==================== CONFIRM COD PAYMENT ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Xác nhận COD thành công
     * Input: Order ở trạng thái PENDING, chưa chọn payment method
     * Expected Output: Order được set PaymentMethod = COD
     * Notes: CheckDB – paymentMethod phải là COD sau khi xác nhận
     */
    @Test
    @DisplayName("TC-FR-13-012: Xác nhận COD thành công")
    void TC_FR_13_012() {
        Order order = buildOrder(OrderStatus.PENDING, null);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order result = orderService.confirmCodPayment(1L, user);

        assertThat(result.getPaymentMethod()).isEqualTo(PaymentMethod.COD);
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Xác nhận COD thất bại khi không phải chủ đơn
     * Input: User khác cố xác nhận đơn
     * Expected Output: RuntimeException "Bạn không có quyền"
     * Notes: Kiểm tra nhánh user.getId != order.getUser.getId
     */
    @Test
    @DisplayName("TC-FR-13-013: Không phải chủ đơn → exception")
    void TC_FR_13_013() {
        Order order = buildOrder(OrderStatus.PENDING, null);
        User otherUser = User.builder().id(99L).build();

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.confirmCodPayment(1L, otherUser))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Bạn không có quyền");
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Xác nhận COD thất bại khi order không ở PENDING
     * Input: Order có status PAID
     * Expected Output: RuntimeException "Đơn hàng không ở trạng thái PENDING"
     * Notes: Kiểm tra nhánh status != PENDING
     */
    @Test
    @DisplayName("TC-FR-13-024: Không ở PENDING → exception")
    void TC_FR_13_024() {
        Order order = buildOrder(OrderStatus.PAID, null);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.confirmCodPayment(1L, user))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Đơn hàng không ở trạng thái PENDING");
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Xác nhận COD thất bại khi đã có payment method
     * Input: Order đã chọn paymentMethod = VNPAY
     * Expected Output: RuntimeException "Đơn hàng đã chọn phương thức thanh toán"
     * Notes: Kiểm tra nhánh paymentMethod != null
     */
    @Test
    @DisplayName("TC-FR-13-026: Đã có payment method → exception")
    void TC_FR_13_026() {
        Order order = buildOrder(OrderStatus.PENDING, PaymentMethod.VNPAY);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.confirmCodPayment(1L, user))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Đơn hàng đã chọn phương thức thanh toán");
    }

    // ==================== CANCEL ORDER ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Hủy đơn PENDING thành công, trả lại reserved quantity
     * Input: Order PENDING thuộc user hiện tại
     * Expected Output: Status = CANCELLED, reservedQuantity giảm
     * Notes: CheckDB – drug.reservedQuantity phải giảm, status = CANCELLED
     */
    @Test
    @DisplayName("TC-FR-13-028: Hủy đơn PENDING thành công")
    void TC_FR_13_028() {
        drug.setReservedQuantity(5);
        Order order = buildOrder(OrderStatus.PENDING, null);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        orderService.cancelOrder(1L, user);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(drug.getReservedQuantity()).isEqualTo(3);
        verify(orderRepository).save(order);
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Hủy đơn PAID + VNPAY → chuyển sang CANCEL_REQUESTED
     * Input: Order PAID với paymentMethod VNPAY
     * Expected Output: Status = CANCEL_REQUESTED, payment status = REFUND_PENDING
     * Notes: CheckDB – trạng thái chuyển thành yêu cầu hoàn tiền
     */
    @Test
    @DisplayName("TC-FR-13-030: Hủy đơn PAID VNPAY → CANCEL_REQUESTED")
    void TC_FR_13_030() {
        Order order = buildOrder(OrderStatus.PAID, PaymentMethod.VNPAY);
        Payment payment = Payment.builder().id(1L).order(order)
                .status(PaymentStatus.SUCCESS).build();

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrder(order)).thenReturn(Optional.of(payment));

        orderService.cancelOrder(1L, user);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCEL_REQUESTED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Hủy đơn thất bại ở trạng thái không cho phép
     * Input: Order SHIPPED
     * Expected Output: RuntimeException "Không thể huỷ đơn"
     * Notes: Kiểm tra nhánh status không phải PENDING hoặc PAID+VNPAY
     */
    @Test
    @DisplayName("TC-FR-13-033: Trạng thái không hợp lệ → exception")
    void TC_FR_13_033() {
        Order order = buildOrder(OrderStatus.SHIPPED, PaymentMethod.COD);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(1L, user))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Không thể huỷ đơn");
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Hủy đơn thất bại khi không phải chủ đơn
     * Input: User khác cố hủy đơn
     * Expected Output: RuntimeException "Không có quyền"
     * Notes: Kiểm tra nhánh user.id != order.user.id
     */
    @Test
    @DisplayName("TC-FR-14-001: Không phải chủ đơn → exception")
    void TC_FR_14_001() {
        Order order = buildOrder(OrderStatus.PENDING, null);
        User otherUser = User.builder().id(99L).build();

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(1L, otherUser))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Không có quyền");
    }

    // ==================== GET ORDERS ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Lấy danh sách đơn hàng cho user thường (chỉ xem đơn của mình)
     * Input: User role USER, không filter
     * Expected Output: PageResponse chứa danh sách đơn hàng
     * Notes: Kiểm tra user chỉ thấy đơn của mình
     */
    @Test
    @DisplayName("TC-FR-14-002: USER xem đơn của mình")
    void TC_FR_14_002() {
        Order order = buildOrder(OrderStatus.PENDING, null);
        Page<Order> page = new PageImpl<>(List.of(order), PageRequest.of(0, 10), 1);

        when(orderRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        PageResponse<OrderResponse> result = orderService.getOrders(
                user, null, null, null, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Admin xem tất cả đơn hàng với filter status
     * Input: Admin user, filter status = PENDING
     * Expected Output: PageResponse với các đơn PENDING
     * Notes: Admin không bị giới hạn chỉ xem đơn của mình
     */
    @Test
    @DisplayName("TC-FR-14-004: ADMIN xem tất cả đơn, có filter")
    void TC_FR_14_004() {
        Order order = buildOrder(OrderStatus.PENDING, null);
        Page<Order> page = new PageImpl<>(List.of(order));

        when(orderRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        PageResponse<OrderResponse> result = orderService.getOrders(
                adminUser, OrderStatus.PENDING, null, null, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).isNotEmpty();
    }

    // ==================== GET ORDER BY ID ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Lấy chi tiết đơn hàng thành công
     * Input: orderId hợp lệ thuộc user hiện tại
     * Expected Output: OrderResponse chứa đầy đủ thông tin
     * Notes: Happy path
     */
    @Test
    @DisplayName("TC-FR-14-006: Lấy đơn thành công")
    void TC_FR_14_006() throws AccessDeniedException {
        Order order = buildOrder(OrderStatus.PENDING, null);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        OrderResponse result = orderService.getOrderById(user, 1L);

        assertThat(result.getOrderId()).isEqualTo(1L);
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Lấy đơn thất bại khi user không phải chủ đơn
     * Input: orderId thuộc user khác
     * Expected Output: AccessDeniedException
     * Notes: Kiểm tra nhánh user.id != order.user.id cho USER role
     */
    @Test
    @DisplayName("TC-FR-14-007: Không phải chủ đơn → AccessDeniedException")
    void TC_FR_14_007() {
        Order order = buildOrder(OrderStatus.PENDING, null);
        User otherUser = User.builder().id(99L).email("other@test.com").role(userRole).build();

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.getOrderById(otherUser, 1L))
                .isInstanceOf(AccessDeniedException.class);
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Admin xem được đơn hàng của bất kỳ user nào
     * Input: Admin user xem đơn của user khác
     * Expected Output: OrderResponse trả về thành công
     * Notes: Kiểm tra nhánh isAdminOrMod == true
     */
    @Test
    @DisplayName("TC-FR-14-008: ADMIN xem đơn của user khác → thành công")
    void TC_FR_14_008() throws AccessDeniedException {
        Order order = buildOrder(OrderStatus.PENDING, null);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        OrderResponse result = orderService.getOrderById(adminUser, 1L);

        assertThat(result).isNotNull();
    }

    // ==================== APPROVE REFUND ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Duyệt hoàn tiền thành công
     * Input: Order CANCEL_REQUESTED + VNPAY, payment REFUND_PENDING, refund thành công
     * Expected Output: Order REFUNDED, payment REFUNDED
     * Notes: CheckDB – soldQuantity giảm, payment.refundedAt được set
     */
    @Test
    @DisplayName("TC-FR-14-010: Duyệt hoàn tiền thành công")
    void TC_FR_14_010() {
        drug.setSoldQuantity(10);
        Order order = buildOrder(OrderStatus.CANCEL_REQUESTED, PaymentMethod.VNPAY);
        Payment payment = Payment.builder().id(1L).order(order)
                .status(PaymentStatus.REFUND_PENDING).build();

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrder(order)).thenReturn(Optional.of(payment));
        when(paymentService.callVnPayRefund(payment, adminUser)).thenReturn(true);

        orderService.approveRefund(1L, adminUser);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(drug.getSoldQuantity()).isEqualTo(8);
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Duyệt hoàn tiền thất bại khi VNPay refund lỗi
     * Input: paymentService.callVnPayRefund trả về false
     * Expected Output: RuntimeException "Hoàn tiền thất bại", payment = REFUND_FAILED
     * Notes: Kiểm tra nhánh refundSuccess == false
     */
    @Test
    @DisplayName("TC-FR-14-011: VNPay refund lỗi → exception")
    void TC_FR_14_011() {
        Order order = buildOrder(OrderStatus.CANCEL_REQUESTED, PaymentMethod.VNPAY);
        Payment payment = Payment.builder().id(1L).order(order)
                .status(PaymentStatus.REFUND_PENDING).build();

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrder(order)).thenReturn(Optional.of(payment));
        when(paymentService.callVnPayRefund(payment, adminUser)).thenReturn(false);

        assertThatThrownBy(() -> orderService.approveRefund(1L, adminUser))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Hoàn tiền thất bại");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUND_FAILED);
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Duyệt hoàn tiền thất bại khi order không ở CANCEL_REQUESTED
     * Input: Order PENDING
     * Expected Output: RuntimeException "Order không ở trạng thái yêu cầu hoàn"
     * Notes: Kiểm tra nhánh status != CANCEL_REQUESTED
     */
    @Test
    @DisplayName("TC-FR-14-012: Trạng thái không hợp lệ → exception")
    void TC_FR_14_012() {
        Order order = buildOrder(OrderStatus.PENDING, PaymentMethod.VNPAY);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.approveRefund(1L, adminUser))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Order không ở trạng thái yêu cầu hoàn");
    }

    // ==================== REJECT REFUND ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Từ chối hoàn tiền thành công
     * Input: Order CANCEL_REQUESTED + VNPAY, payment REFUND_PENDING
     * Expected Output: Order PAID, payment REFUND_FAILED
     * Notes: CheckDB – trạng thái quay lại PAID
     */
    @Test
    @DisplayName("TC-FR-14-013: Từ chối hoàn tiền thành công")
    void TC_FR_14_013() {
        Order order = buildOrder(OrderStatus.CANCEL_REQUESTED, PaymentMethod.VNPAY);
        Payment payment = Payment.builder().id(1L).order(order)
                .status(PaymentStatus.REFUND_PENDING).build();

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrder(order)).thenReturn(Optional.of(payment));

        orderService.rejectRefund(1L, adminUser);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUND_FAILED);
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Từ chối hoàn tiền thất bại khi không phải VNPAY
     * Input: Order CANCEL_REQUESTED + COD
     * Expected Output: RuntimeException
     * Notes: Kiểm tra nhánh paymentMethod != VNPAY
     */
    @Test
    @DisplayName("TC-FR-14-014: Không phải VNPAY → exception")
    void TC_FR_14_014() {
        Order order = buildOrder(OrderStatus.CANCEL_REQUESTED, PaymentMethod.COD);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.rejectRefund(1L, adminUser))
                .isInstanceOf(RuntimeException.class);
    }

    // ==================== BULK SHIP ORDERS ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Bulk ship thành công cho đơn PENDING và PAID
     * Input: List orderIds gồm đơn PENDING và PAID
     * Expected Output: BulkOrderActionResult với success > 0
     * Notes: Kiểm tra cả 2 nhánh status == PENDING và status == PAID
     */
    @Test
    @DisplayName("TC-FR-14-015: Bulk ship thành công")
    void TC_FR_14_015() {
        Order pendingOrder = buildOrder(OrderStatus.PENDING, null);
        pendingOrder.setId(1L);

        Order paidOrder = buildOrder(OrderStatus.PAID, PaymentMethod.VNPAY);
        paidOrder.setId(2L);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));
        when(orderRepository.findById(2L)).thenReturn(Optional.of(paidOrder));

        BulkOrderActionResult result = orderService.bulkShipOrders(List.of(1L, 2L));

        assertThat(result.getSuccess()).isEqualTo(2);
        assertThat(result.getFailed()).isEqualTo(0);
        assertThat(pendingOrder.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(paidOrder.getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Bulk ship với một số đơn không hợp lệ
     * Input: Gồm đơn COMPLETED (không ship được)
     * Expected Output: BulkOrderActionResult có errors
     * Notes: Kiểm tra xử lý lỗi trong vòng lặp
     */
    @Test
    @DisplayName("TC-FR-14-016: Một số đơn lỗi → có errors")
    void TC_FR_14_016() {
        Order completedOrder = buildOrder(OrderStatus.COMPLETED, PaymentMethod.COD);
        completedOrder.setId(1L);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(completedOrder));

        BulkOrderActionResult result = orderService.bulkShipOrders(List.of(1L));

        assertThat(result.getFailed()).isEqualTo(1);
        assertThat(result.getErrors()).hasSize(1);
    }

    // ==================== BULK COMPLETE ORDERS ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Bulk complete thành công cho đơn SHIPPED + COD
     * Input: Order SHIPPED, paymentMethod COD
     * Expected Output: Status = COMPLETED, reservedQty giảm, soldQty tăng
     * Notes: CheckDB – kiểm tra drug quantities được cập nhật
     */
    @Test
    @DisplayName("TC-FR-14-017: Complete COD thành công")
    void TC_FR_14_017() {
        drug.setReservedQuantity(10);
        drug.setSoldQuantity(0);
        Order order = buildOrder(OrderStatus.SHIPPED, PaymentMethod.COD);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        BulkOrderActionResult result = orderService.bulkCompleteOrders(List.of(1L));

        assertThat(result.getSuccess()).isEqualTo(1);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(drug.getReservedQuantity()).isEqualTo(8);
        assertThat(drug.getSoldQuantity()).isEqualTo(2);
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Bulk complete thất bại cho đơn chưa SHIPPED
     * Input: Order PENDING
     * Expected Output: BulkOrderActionResult có errors
     * Notes: Kiểm tra nhánh status != SHIPPED
     */
    @Test
    @DisplayName("TC-FR-14-018: Chưa SHIPPED → error")
    void TC_FR_14_018() {
        Order order = buildOrder(OrderStatus.PENDING, null);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        BulkOrderActionResult result = orderService.bulkCompleteOrders(List.of(1L));

        assertThat(result.getFailed()).isEqualTo(1);
    }

    // ==================== BULK CANCEL ORDERS ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Bulk cancel đơn PENDING thành công
     * Input: Order PENDING
     * Expected Output: Status = CANCELLED, reservedQty giảm
     * Notes: CheckDB – trả lại tồn kho reserved
     */
    @Test
    @DisplayName("TC-FR-14-019: Cancel đơn PENDING thành công")
    void TC_FR_14_019() {
        drug.setReservedQuantity(10);
        Order order = buildOrder(OrderStatus.PENDING, null);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        BulkOrderActionResult result = orderService.bulkCancelOrders(List.of(1L), adminUser);

        assertThat(result.getSuccess()).isEqualTo(1);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Bulk cancel đơn PAID VNPAY → hoàn tiền thành công
     * Input: Order PAID + VNPAY, refund success
     * Expected Output: Status = REFUNDED
     * Notes: CheckDB – payment refunded, soldQty giảm
     */
    @Test
    @DisplayName("TC-FR-14-020: Cancel đơn PAID VNPAY → REFUNDED")
    void TC_FR_14_020() {
        drug.setSoldQuantity(5);
        Order order = buildOrder(OrderStatus.PAID, PaymentMethod.VNPAY);
        Payment payment = Payment.builder().id(1L).order(order)
                .status(PaymentStatus.SUCCESS).build();

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrder(order)).thenReturn(Optional.of(payment));
        when(paymentService.callVnPayRefund(payment, adminUser)).thenReturn(true);

        BulkOrderActionResult result = orderService.bulkCancelOrders(List.of(1L), adminUser);

        assertThat(result.getSuccess()).isEqualTo(1);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Bulk cancel thất bại cho trạng thái không hợp lệ
     * Input: Order COMPLETED
     * Expected Output: BulkOrderActionResult có errors
     * Notes: Kiểm tra nhánh default throw
     */
    @Test
    @DisplayName("TC-FR-14-021: Trạng thái không hợp lệ → error")
    void TC_FR_14_021() {
        Order order = buildOrder(OrderStatus.COMPLETED, PaymentMethod.COD);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        BulkOrderActionResult result = orderService.bulkCancelOrders(List.of(1L), adminUser);

        assertThat(result.getFailed()).isEqualTo(1);
        assertThat(result.getErrors().get(0).getReason())
                .contains("Không thể huỷ đơn");
    }
}

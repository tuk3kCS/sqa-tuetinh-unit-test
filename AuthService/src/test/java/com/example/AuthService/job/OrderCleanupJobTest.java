package com.example.AuthService.job;

import com.example.AuthService.entity.Drug;
import com.example.AuthService.entity.Order;
import com.example.AuthService.entity.OrderItem;
import com.example.AuthService.enums.OrderStatus;
import com.example.AuthService.enums.PaymentMethod;
import com.example.AuthService.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests cho {@link OrderCleanupJob}.
 * Kiểm tra logic hủy đơn hàng hết hạn.
 */
@ExtendWith(MockitoExtension.class)
class OrderCleanupJobTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderCleanupJob orderCleanupJob;

    private Order createExpiredOrder(Long id, PaymentMethod paymentMethod) {
        Drug drug = Drug.builder()
                .id(1L).name("Test Drug")
                .price(BigDecimal.valueOf(10000))
                .importPrice(BigDecimal.valueOf(8000))
                .reservedQuantity(5)
                .build();

        OrderItem item = OrderItem.builder()
                .id(1L).drug(drug).quantity(2)
                .unitPrice(10000.0).totalPrice(20000.0)
                .build();

        Order order = Order.builder()
                .id(id)
                .status(OrderStatus.PENDING)
                .paymentMethod(paymentMethod)
                .createdAt(LocalDateTime.now().minusMinutes(20))
                .totalAmount(BigDecimal.valueOf(20000))
                .items(new ArrayList<>(List.of(item)))
                .build();

        item.setOrder(order);
        return order;
    }

    // ======================== CANCEL EXPIRED ORDERS ========================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Hủy đơn hàng hết hạn thành công (paymentMethod=null → cần cleanup)
     * Input: 1 đơn hàng PENDING, paymentMethod=null, đã quá 15 phút
     * Expected Output: Order status chuyển thành CANCELLED, reservedQuantity giảm
     * Notes: Happy path - đơn hàng chưa xác nhận phương thức thanh toán
     */
    @Test
    @DisplayName("TC-FR-02-001: Hủy đơn hết hạn thành công")
    void TC_FR_02_001() {
        Order expiredOrder = createExpiredOrder(1L, null);
        int originalReserved = expiredOrder.getItems().get(0).getDrug().getReservedQuantity();

        when(orderRepository.findExpiredUnconfirmedOrders(any(LocalDateTime.class)))
                .thenReturn(List.of(expiredOrder));

        orderCleanupJob.cancelExpiredOrders();

        assertEquals(OrderStatus.CANCELLED, expiredOrder.getStatus());
        assertEquals(originalReserved - 2,
                expiredOrder.getItems().get(0).getDrug().getReservedQuantity());
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Không làm gì khi không có đơn hàng hết hạn
     * Input: Danh sách đơn hàng hết hạn rỗng
     * Expected Output: Không có order nào bị thay đổi
     * Notes: Không có đơn nào quá 15 phút
     */
    @Test
    @DisplayName("TC-FR-02-001: Không có đơn hết hạn")
    void TC_FR_02_001() {
        when(orderRepository.findExpiredUnconfirmedOrders(any(LocalDateTime.class)))
                .thenReturn(List.of());

        orderCleanupJob.cancelExpiredOrders();

        verify(orderRepository).findExpiredUnconfirmedOrders(any());
        verifyNoMoreInteractions(orderRepository);
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Bỏ qua đơn hàng đã có paymentMethod (không null)
     * Input: 1 đơn hàng PENDING, paymentMethod=COD
     * Expected Output: Order status KHÔNG thay đổi (vẫn PENDING)
     * Notes: Đơn hàng đã chọn phương thức thanh toán → bỏ qua
     */
    @Test
    @DisplayName("TC-FR-02-001: Bỏ qua đơn có paymentMethod")
    void TC_FR_02_001() {
        Order orderWithPayment = createExpiredOrder(1L, PaymentMethod.COD);

        when(orderRepository.findExpiredUnconfirmedOrders(any(LocalDateTime.class)))
                .thenReturn(List.of(orderWithPayment));

        orderCleanupJob.cancelExpiredOrders();

        assertEquals(OrderStatus.PENDING, orderWithPayment.getStatus());
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Hủy nhiều đơn hàng cùng lúc
     * Input: 3 đơn hàng hết hạn (2 cần cleanup, 1 có paymentMethod)
     * Expected Output: 2 đơn CANCELLED, 1 đơn PENDING
     * Notes: Xử lý batch
     */
    @Test
    @DisplayName("TC-FR-02-001: Hủy nhiều đơn hàng hết hạn")
    void TC_FR_02_001() {
        Order order1 = createExpiredOrder(1L, null);
        Order order2 = createExpiredOrder(2L, null);
        Order order3 = createExpiredOrder(3L, PaymentMethod.VNPAY);

        when(orderRepository.findExpiredUnconfirmedOrders(any(LocalDateTime.class)))
                .thenReturn(List.of(order1, order2, order3));

        orderCleanupJob.cancelExpiredOrders();

        assertEquals(OrderStatus.CANCELLED, order1.getStatus());
        assertEquals(OrderStatus.CANCELLED, order2.getStatus());
        assertEquals(OrderStatus.PENDING, order3.getStatus());
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Kiểm tra trả kho đúng số lượng khi hủy
     * Input: Đơn hàng có 2 items, item1.qty=3, item2.qty=5
     * Expected Output: Drug1.reservedQuantity giảm 3, Drug2.reservedQuantity giảm 5
     * Notes: Kiểm tra hoàn trả reserve cho từng item
     */
    @Test
    @DisplayName("TC-FR-02-001: Trả kho đúng số lượng")
    void TC_FR_02_001() {
        Drug drug1 = Drug.builder().id(1L).name("Drug1").price(BigDecimal.TEN)
                .importPrice(BigDecimal.ONE).reservedQuantity(10).build();
        Drug drug2 = Drug.builder().id(2L).name("Drug2").price(BigDecimal.TEN)
                .importPrice(BigDecimal.ONE).reservedQuantity(8).build();

        OrderItem item1 = OrderItem.builder().id(1L).drug(drug1).quantity(3).build();
        OrderItem item2 = OrderItem.builder().id(2L).drug(drug2).quantity(5).build();

        Order order = Order.builder()
                .id(1L).status(OrderStatus.PENDING).paymentMethod(null)
                .items(new ArrayList<>(List.of(item1, item2)))
                .build();

        when(orderRepository.findExpiredUnconfirmedOrders(any()))
                .thenReturn(List.of(order));

        orderCleanupJob.cancelExpiredOrders();

        assertEquals(7, drug1.getReservedQuantity());
        assertEquals(3, drug2.getReservedQuantity());
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
    }
}

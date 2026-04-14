package com.example.AuthService.spec;

import com.example.AuthService.entity.Order;
import com.example.AuthService.enums.OrderStatus;
import com.example.AuthService.enums.PaymentMethod;
import jakarta.persistence.criteria.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests cho {@link OrderSpecification}.
 * Kiểm tra việc tạo JPA Specification cho truy vấn Order.
 */
@ExtendWith(MockitoExtension.class)
class OrderSpecificationTest {

    @Mock private Root<Order> root;
    @Mock private CriteriaQuery<?> query;
    @Mock private CriteriaBuilder cb;
    @Mock private Path<Object> path;
    @Mock private Path<Object> nestedPath;
    @Mock private Predicate predicate;
    @Mock private Expression<String> stringExpression;

    // ======================== FILTER ========================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Filter theo status
     * Input: status=PENDING, tất cả field khác null
     * Expected Output: Predicate equal trên status
     * Notes: Lọc đơn hàng theo trạng thái
     */
    @Test
    @DisplayName("TC-FR-02-001: Filter theo status")
    void TC_FR_02_001() {
        when(root.get("status")).thenReturn(path);
        when(cb.equal(any(), eq(OrderStatus.PENDING))).thenReturn(predicate);
        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        Specification<Order> spec = OrderSpecification.filter(
                OrderStatus.PENDING, null, null, null, null, null);
        spec.toPredicate(root, query, cb);

        verify(cb).equal(any(), eq(OrderStatus.PENDING));
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Filter theo paymentMethod
     * Input: paymentMethod=COD
     * Expected Output: Predicate equal trên paymentMethod
     * Notes: Lọc theo phương thức thanh toán
     */
    @Test
    @DisplayName("TC-FR-02-001: Filter theo paymentMethod")
    void TC_FR_02_001() {
        when(root.get("paymentMethod")).thenReturn(path);
        when(cb.equal(any(), eq(PaymentMethod.COD))).thenReturn(predicate);
        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        Specification<Order> spec = OrderSpecification.filter(
                null, PaymentMethod.COD, null, null, null, null);
        spec.toPredicate(root, query, cb);

        verify(cb).equal(any(), eq(PaymentMethod.COD));
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Filter theo userId
     * Input: userId=1
     * Expected Output: Predicate equal trên user.id
     * Notes: Lọc đơn hàng của user cụ thể
     */
    @Test
    @DisplayName("TC-FR-02-001: Filter theo userId")
    void TC_FR_02_001() {
        when(root.get("user")).thenReturn(path);
        when(path.get("id")).thenReturn(nestedPath);
        when(cb.equal(any(), eq(1L))).thenReturn(predicate);
        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        Specification<Order> spec = OrderSpecification.filter(
                null, null, 1L, null, null, null);
        spec.toPredicate(root, query, cb);

        verify(cb).equal(any(), eq(1L));
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Filter theo keyword (tìm trong receiverName, userEmail, receiverPhone)
     * Input: keyword="Nguyen"
     * Expected Output: Predicate OR LIKE trên 3 trường
     * Notes: Tìm kiếm đa trường
     */
    @Test
    @DisplayName("TC-FR-02-001: Filter theo keyword")
    void TC_FR_02_001() {
        when(root.get(anyString())).thenReturn(path);
        when(cb.lower(any())).thenReturn(stringExpression);
        when(cb.like(any(Expression.class), anyString())).thenReturn(predicate);
        when(cb.or(any(Predicate[].class))).thenReturn(predicate);
        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        Specification<Order> spec = OrderSpecification.filter(
                null, null, null, "Nguyen", null, null);
        spec.toPredicate(root, query, cb);

        verify(cb).or(any(Predicate[].class));
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Filter theo khoảng ngày (fromDate, toDate)
     * Input: fromDate=2025-01-01, toDate=2025-01-31
     * Expected Output: 2 Predicate greaterThanOrEqualTo và lessThanOrEqualTo trên createdAt
     * Notes: Lọc theo khoảng thời gian tạo đơn
     */
    @Test
    @DisplayName("TC-FR-02-001: Filter theo khoảng ngày")
    void TC_FR_02_001() {
        LocalDateTime fromDate = LocalDateTime.of(2025, 1, 1, 0, 0);
        LocalDateTime toDate = LocalDateTime.of(2025, 1, 31, 23, 59);

        when(root.get("createdAt")).thenReturn(path);
        when(cb.greaterThanOrEqualTo(any(Expression.class), any(LocalDateTime.class))).thenReturn(predicate);
        when(cb.lessThanOrEqualTo(any(Expression.class), any(LocalDateTime.class))).thenReturn(predicate);
        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        Specification<Order> spec = OrderSpecification.filter(
                null, null, null, null, fromDate, toDate);
        spec.toPredicate(root, query, cb);

        verify(cb).greaterThanOrEqualTo(any(Expression.class), eq(fromDate));
        verify(cb).lessThanOrEqualTo(any(Expression.class), eq(toDate));
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Filter với tất cả tham số null
     * Input: Tất cả null
     * Expected Output: Không có predicate nào, trả conjunction rỗng
     * Notes: Không lọc → lấy tất cả
     */
    @Test
    @DisplayName("TC-FR-02-001: Không filter - lấy tất cả")
    void TC_FR_02_001() {
        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        Specification<Order> spec = OrderSpecification.filter(
                null, null, null, null, null, null);
        Predicate result = spec.toPredicate(root, query, cb);

        assertNotNull(result);
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Filter với keyword rỗng (blank)
     * Input: keyword="   "
     * Expected Output: Bỏ qua keyword filter (isBlank = true)
     * Notes: Keyword toàn khoảng trắng được bỏ qua
     */
    @Test
    @DisplayName("TC-FR-02-001: Keyword rỗng (blank) bị bỏ qua")
    void TC_FR_02_001() {
        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        Specification<Order> spec = OrderSpecification.filter(
                null, null, null, "   ", null, null);
        spec.toPredicate(root, query, cb);

        verify(cb, never()).or(any(Predicate[].class));
    }
}

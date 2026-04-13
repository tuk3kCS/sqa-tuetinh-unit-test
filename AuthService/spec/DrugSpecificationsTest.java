package com.example.AuthService.spec;

import com.example.AuthService.dto.DrugFilter;
import com.example.AuthService.entity.Drug;
import jakarta.persistence.criteria.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests cho {@link DrugSpecifications}.
 * Kiểm tra việc tạo JPA Specification cho truy vấn Drug.
 */
@ExtendWith(MockitoExtension.class)
class DrugSpecificationsTest {

    @Mock private Root<Drug> root;
    @Mock private CriteriaQuery<?> query;
    @Mock private CriteriaBuilder cb;
    @Mock private Path<Object> path;
    @Mock private Predicate predicate;
    @Mock private Expression<String> stringExpression;

    // ======================== WITH FILTER ========================

    /**
     * Test Case ID: TC_AUTH_DrugSpecifications_withFilter_001
     * Test Objective: Filter với tất cả tham số null (trả tất cả)
     * Input: DrugFilter rỗng (tất cả fields null)
     * Expected Output: Specification không null, toPredicate trả về conjunction rỗng
     * Notes: Không có điều kiện lọc → lấy tất cả
     */
    @Test
    @DisplayName("TC_AUTH_DrugSpecifications_withFilter_001: Filter rỗng - lấy tất cả")
    void TC_AUTH_DrugSpecifications_withFilter_001() {
        DrugFilter filter = new DrugFilter();

        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        Specification<Drug> spec = DrugSpecifications.withFilter(filter);
        Predicate result = spec.toPredicate(root, query, cb);

        assertNotNull(result);
    }

    /**
     * Test Case ID: TC_AUTH_DrugSpecifications_withFilter_002
     * Test Objective: Filter với keyword tìm kiếm (q)
     * Input: DrugFilter(q="para")
     * Expected Output: Predicate LIKE trên trường name
     * Notes: Tìm kiếm theo tên thuốc
     */
    @Test
    @DisplayName("TC_AUTH_DrugSpecifications_withFilter_002: Filter với keyword")
    void TC_AUTH_DrugSpecifications_withFilter_002() {
        DrugFilter filter = new DrugFilter();
        filter.setQ("para");

        when(root.get("name")).thenReturn(path);
        when(cb.lower(any())).thenReturn(stringExpression);
        when(cb.like(any(Expression.class), anyString())).thenReturn(predicate);
        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        Specification<Drug> spec = DrugSpecifications.withFilter(filter);
        spec.toPredicate(root, query, cb);

        verify(cb).like(any(Expression.class), eq("%para%"));
    }

    /**
     * Test Case ID: TC_AUTH_DrugSpecifications_withFilter_003
     * Test Objective: Filter với minPrice
     * Input: DrugFilter(minPrice=10000)
     * Expected Output: Predicate >= trên trường price
     * Notes: Giá tối thiểu
     */
    @Test
    @DisplayName("TC_AUTH_DrugSpecifications_withFilter_003: Filter với minPrice")
    void TC_AUTH_DrugSpecifications_withFilter_003() {
        DrugFilter filter = new DrugFilter();
        filter.setMinPrice(BigDecimal.valueOf(10000));

        when(root.get("price")).thenReturn(path);
        when(cb.greaterThanOrEqualTo(any(Expression.class), any(BigDecimal.class))).thenReturn(predicate);
        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        Specification<Drug> spec = DrugSpecifications.withFilter(filter);
        spec.toPredicate(root, query, cb);

        verify(cb).greaterThanOrEqualTo(any(Expression.class), eq(BigDecimal.valueOf(10000)));
    }

    /**
     * Test Case ID: TC_AUTH_DrugSpecifications_withFilter_004
     * Test Objective: Filter với maxPrice
     * Input: DrugFilter(maxPrice=50000)
     * Expected Output: Predicate <= trên trường price
     * Notes: Giá tối đa
     */
    @Test
    @DisplayName("TC_AUTH_DrugSpecifications_withFilter_004: Filter với maxPrice")
    void TC_AUTH_DrugSpecifications_withFilter_004() {
        DrugFilter filter = new DrugFilter();
        filter.setMaxPrice(BigDecimal.valueOf(50000));

        when(root.get("price")).thenReturn(path);
        when(cb.lessThanOrEqualTo(any(Expression.class), any(BigDecimal.class))).thenReturn(predicate);
        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        Specification<Drug> spec = DrugSpecifications.withFilter(filter);
        spec.toPredicate(root, query, cb);

        verify(cb).lessThanOrEqualTo(any(Expression.class), eq(BigDecimal.valueOf(50000)));
    }

    /**
     * Test Case ID: TC_AUTH_DrugSpecifications_withFilter_005
     * Test Objective: Filter với inStock=true (stockQuantity > 0)
     * Input: DrugFilter(inStock=true)
     * Expected Output: Predicate > 0 trên stockQuantity
     * Notes: Chỉ lấy thuốc còn hàng
     */
    @Test
    @DisplayName("TC_AUTH_DrugSpecifications_withFilter_005: Filter inStock=true")
    void TC_AUTH_DrugSpecifications_withFilter_005() {
        DrugFilter filter = new DrugFilter();
        filter.setInStock(true);

        when(root.get("stockQuantity")).thenReturn(path);
        when(cb.greaterThan(any(Expression.class), eq(0))).thenReturn(predicate);
        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        Specification<Drug> spec = DrugSpecifications.withFilter(filter);
        spec.toPredicate(root, query, cb);

        verify(cb).greaterThan(any(Expression.class), eq(0));
    }

    /**
     * Test Case ID: TC_AUTH_DrugSpecifications_withFilter_006
     * Test Objective: Filter với null DrugFilter
     * Input: DrugFilter = null (truyền null)
     * Expected Output: Predicate trả kết quả mặc định (không lọc)
     * Notes: Edge case - filter null
     */
    @Test
    @DisplayName("TC_AUTH_DrugSpecifications_withFilter_006: Filter null")
    void TC_AUTH_DrugSpecifications_withFilter_006() {
        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        Specification<Drug> spec = DrugSpecifications.withFilter(null);
        Predicate result = spec.toPredicate(root, query, cb);

        assertNotNull(result);
    }

    /**
     * Test Case ID: TC_AUTH_DrugSpecifications_withFilter_007
     * Test Objective: Filter với isActive
     * Input: DrugFilter(isActive=true)
     * Expected Output: Predicate equal trên isActive
     * Notes: Lọc thuốc đang hoạt động
     */
    @Test
    @DisplayName("TC_AUTH_DrugSpecifications_withFilter_007: Filter isActive=true")
    void TC_AUTH_DrugSpecifications_withFilter_007() {
        DrugFilter filter = new DrugFilter();
        filter.setIsActive(true);

        when(root.get("isActive")).thenReturn(path);
        when(cb.equal(any(Expression.class), eq(true))).thenReturn(predicate);
        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        Specification<Drug> spec = DrugSpecifications.withFilter(filter);
        spec.toPredicate(root, query, cb);

        verify(cb).equal(any(Expression.class), eq(true));
    }
}

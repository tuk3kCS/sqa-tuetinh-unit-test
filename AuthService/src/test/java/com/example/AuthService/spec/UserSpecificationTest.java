package com.example.AuthService.spec;

import com.example.AuthService.entity.User;
import jakarta.persistence.criteria.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests cho {@link UserSpecification}.
 * Kiểm tra việc tạo JPA Specification cho truy vấn User.
 */
@ExtendWith(MockitoExtension.class)
class UserSpecificationTest {

    @Mock private Root<User> root;
    @Mock private CriteriaQuery<?> query;
    @Mock private CriteriaBuilder cb;
    @Mock private Path<Object> path;
    @Mock private Path<Object> nestedPath;
    @Mock private Predicate predicate;
    @Mock private Expression<String> stringExpression;

    // ======================== FILTER ========================

    /**
     * Test Case ID: TC_AUTH_UserSpecification_filter_001
     * Test Objective: Filter theo keyword (tìm trong name và email)
     * Input: keyword="test"
     * Expected Output: Predicate OR LIKE trên name và email
     * Notes: Tìm kiếm đa trường
     */
    @Test
    @DisplayName("TC_AUTH_UserSpecification_filter_001: Filter theo keyword")
    void TC_AUTH_UserSpecification_filter_001() {
        when(root.get(anyString())).thenReturn(path);
        when(cb.lower(any())).thenReturn(stringExpression);
        when(cb.like(any(Expression.class), anyString())).thenReturn(predicate);
        when(cb.or(any(Predicate.class), any(Predicate.class))).thenReturn(predicate);
        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        Specification<User> spec = UserSpecification.filter("test", null, null);
        spec.toPredicate(root, query, cb);

        verify(cb).or(any(Predicate.class), any(Predicate.class));
    }

    /**
     * Test Case ID: TC_AUTH_UserSpecification_filter_002
     * Test Objective: Filter theo roleId
     * Input: roleId=1
     * Expected Output: Predicate equal trên role.id
     * Notes: Lọc user theo vai trò
     */
    @Test
    @DisplayName("TC_AUTH_UserSpecification_filter_002: Filter theo roleId")
    void TC_AUTH_UserSpecification_filter_002() {
        when(root.get("role")).thenReturn(path);
        when(path.get("id")).thenReturn(nestedPath);
        when(cb.equal(any(), eq(1L))).thenReturn(predicate);
        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        Specification<User> spec = UserSpecification.filter(null, 1L, null);
        spec.toPredicate(root, query, cb);

        verify(cb).equal(any(), eq(1L));
    }

    /**
     * Test Case ID: TC_AUTH_UserSpecification_filter_003
     * Test Objective: Filter theo enabled
     * Input: enabled=true
     * Expected Output: Predicate equal trên enabled
     * Notes: Lọc user đang hoạt động
     */
    @Test
    @DisplayName("TC_AUTH_UserSpecification_filter_003: Filter theo enabled")
    void TC_AUTH_UserSpecification_filter_003() {
        when(root.get("enabled")).thenReturn(path);
        when(cb.equal(any(), eq(true))).thenReturn(predicate);
        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        Specification<User> spec = UserSpecification.filter(null, null, true);
        spec.toPredicate(root, query, cb);

        verify(cb).equal(any(), eq(true));
    }

    /**
     * Test Case ID: TC_AUTH_UserSpecification_filter_004
     * Test Objective: Filter với tất cả tham số null
     * Input: keyword=null, roleId=null, enabled=null
     * Expected Output: Không có predicate nào, conjunction rỗng
     * Notes: Lấy tất cả users
     */
    @Test
    @DisplayName("TC_AUTH_UserSpecification_filter_004: Không filter - lấy tất cả")
    void TC_AUTH_UserSpecification_filter_004() {
        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        Specification<User> spec = UserSpecification.filter(null, null, null);
        Predicate result = spec.toPredicate(root, query, cb);

        assertNotNull(result);
    }

    /**
     * Test Case ID: TC_AUTH_UserSpecification_filter_005
     * Test Objective: Filter kết hợp keyword + roleId + enabled
     * Input: keyword="admin", roleId=3, enabled=true
     * Expected Output: 3 predicates kết hợp
     * Notes: Lọc kết hợp nhiều điều kiện
     */
    @Test
    @DisplayName("TC_AUTH_UserSpecification_filter_005: Filter kết hợp nhiều điều kiện")
    void TC_AUTH_UserSpecification_filter_005() {
        when(root.get(anyString())).thenReturn(path);
        when(path.get("id")).thenReturn(nestedPath);
        when(cb.lower(any())).thenReturn(stringExpression);
        when(cb.like(any(Expression.class), anyString())).thenReturn(predicate);
        when(cb.or(any(Predicate.class), any(Predicate.class))).thenReturn(predicate);
        when(cb.equal(eq(nestedPath), eq(3L))).thenReturn(predicate);
        when(cb.equal(eq(path), eq(true))).thenReturn(predicate);
        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        Specification<User> spec = UserSpecification.filter("admin", 3L, true);
        spec.toPredicate(root, query, cb);

        verify(cb).or(any(Predicate.class), any(Predicate.class));
        verify(cb).equal(nestedPath, 3L);
        verify(cb).equal(path, true);
    }

    /**
     * Test Case ID: TC_AUTH_UserSpecification_filter_006
     * Test Objective: Filter với keyword rỗng (blank)
     * Input: keyword="   "
     * Expected Output: Bỏ qua keyword filter
     * Notes: Keyword toàn khoảng trắng
     */
    @Test
    @DisplayName("TC_AUTH_UserSpecification_filter_006: Keyword rỗng (blank) bị bỏ qua")
    void TC_AUTH_UserSpecification_filter_006() {
        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        Specification<User> spec = UserSpecification.filter("   ", null, null);
        spec.toPredicate(root, query, cb);

        verify(cb, never()).or(any(Predicate.class), any(Predicate.class));
    }

    /**
     * Test Case ID: TC_AUTH_UserSpecification_filter_007
     * Test Objective: Filter enabled=false (user bị disable)
     * Input: enabled=false
     * Expected Output: Predicate equal(enabled, false)
     * Notes: Lọc user bị vô hiệu hóa
     */
    @Test
    @DisplayName("TC_AUTH_UserSpecification_filter_007: Filter enabled=false")
    void TC_AUTH_UserSpecification_filter_007() {
        when(root.get("enabled")).thenReturn(path);
        when(cb.equal(any(), eq(false))).thenReturn(predicate);
        when(cb.and(any(Predicate[].class))).thenReturn(predicate);

        Specification<User> spec = UserSpecification.filter(null, null, false);
        spec.toPredicate(root, query, cb);

        verify(cb).equal(any(), eq(false));
    }
}

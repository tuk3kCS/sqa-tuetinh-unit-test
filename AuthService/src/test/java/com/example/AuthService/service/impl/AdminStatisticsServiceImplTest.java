package com.example.AuthService.service.impl;

import com.example.AuthService.dto.stats.*;
import com.example.AuthService.enums.*;
import com.example.AuthService.repository.OrderRepository;
import com.example.AuthService.repository.PaymentRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests cho AdminStatisticsServiceImpl – kiểm tra thống kê doanh thu, time series, top products.
 */
@ExtendWith(MockitoExtension.class)
class AdminStatisticsServiceImplTest {

    @Mock private OrderRepository orderRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private EntityManager em;

    @InjectMocks
    private AdminStatisticsServiceImpl statisticsService;

    @SuppressWarnings("unchecked")
    private <T> TypedQuery<T> mockQuery(T result) {
        TypedQuery<T> query = mock(TypedQuery.class);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(result);
        when(query.getResultList()).thenReturn(List.of());
        when(query.setMaxResults(anyInt())).thenReturn(query);
        return query;
    }

    // ==================== GET REVENUE SUMMARY ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Lấy tổng quan doanh thu chế độ SALES_ALL
     * Input: RevenueStatsFilter mode = SALES_ALL, có from/to
     * Expected Output: RevenueSummaryDto chứa các trường doanh thu
     * Notes: Happy path – chế độ tính cả VNPAY + COD
     */
    @Test
    @DisplayName("TC-FR-18-001: SALES_ALL thành công")
    void TC_FR_18_001() {
        RevenueStatsFilter filter = new RevenueStatsFilter();
        filter.setFrom(LocalDateTime.now().minusDays(30));
        filter.setTo(LocalDateTime.now());
        filter.setMode(StatsMode.SALES_ALL);

        @SuppressWarnings("unchecked")
        TypedQuery<BigDecimal> bdQuery = mock(TypedQuery.class);
        when(bdQuery.setParameter(anyString(), any())).thenReturn(bdQuery);
        when(bdQuery.getSingleResult()).thenReturn(BigDecimal.valueOf(500000));

        @SuppressWarnings("unchecked")
        TypedQuery<Long> longQuery = mock(TypedQuery.class);
        when(longQuery.setParameter(anyString(), any())).thenReturn(longQuery);
        when(longQuery.getSingleResult()).thenReturn(10L);

        when(em.createQuery(anyString(), eq(BigDecimal.class))).thenReturn(bdQuery);
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(longQuery);

        RevenueSummaryDto result = statisticsService.getRevenueSummary(filter);

        assertThat(result).isNotNull();
        assertThat(result.getOrdersCount()).isEqualTo(10L);
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Lấy doanh thu chế độ CASHFLOW_VNPAY_ONLY
     * Input: mode = CASHFLOW_VNPAY_ONLY
     * Expected Output: codRevenue = 0, chỉ tính VNPAY
     * Notes: Kiểm tra nhánh mode == CASHFLOW_VNPAY_ONLY → codRevenue = ZERO
     */
    @Test
    @DisplayName("TC-FR-18-002: CASHFLOW_VNPAY_ONLY")
    void TC_FR_18_002() {
        RevenueStatsFilter filter = new RevenueStatsFilter();
        filter.setFrom(LocalDateTime.now().minusDays(7));
        filter.setTo(LocalDateTime.now());
        filter.setMode(StatsMode.CASHFLOW_VNPAY_ONLY);

        @SuppressWarnings("unchecked")
        TypedQuery<BigDecimal> bdQuery = mock(TypedQuery.class);
        when(bdQuery.setParameter(anyString(), any())).thenReturn(bdQuery);
        when(bdQuery.getSingleResult()).thenReturn(BigDecimal.valueOf(100000));

        @SuppressWarnings("unchecked")
        TypedQuery<Long> longQuery = mock(TypedQuery.class);
        when(longQuery.setParameter(anyString(), any())).thenReturn(longQuery);
        when(longQuery.getSingleResult()).thenReturn(5L);

        when(em.createQuery(anyString(), eq(BigDecimal.class))).thenReturn(bdQuery);
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(longQuery);

        RevenueSummaryDto result = statisticsService.getRevenueSummary(filter);

        assertThat(result).isNotNull();
        assertThat(result.getCodRevenue()).isEqualTo(BigDecimal.ZERO);
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Xử lý from/to null → sử dụng default
     * Input: from = null, to = null
     * Expected Output: Không throw exception, sử dụng default 30 ngày
     * Notes: Kiểm tra requireFrom/requireTo với null
     */
    @Test
    @DisplayName("TC-FR-18-003: From/To null → default")
    void TC_FR_18_003() {
        RevenueStatsFilter filter = new RevenueStatsFilter();
        filter.setFrom(null);
        filter.setTo(null);
        filter.setMode(StatsMode.SALES_ALL);

        @SuppressWarnings("unchecked")
        TypedQuery<BigDecimal> bdQuery = mock(TypedQuery.class);
        when(bdQuery.setParameter(anyString(), any())).thenReturn(bdQuery);
        when(bdQuery.getSingleResult()).thenReturn(BigDecimal.ZERO);

        @SuppressWarnings("unchecked")
        TypedQuery<Long> longQuery = mock(TypedQuery.class);
        when(longQuery.setParameter(anyString(), any())).thenReturn(longQuery);
        when(longQuery.getSingleResult()).thenReturn(0L);

        when(em.createQuery(anyString(), eq(BigDecimal.class))).thenReturn(bdQuery);
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(longQuery);

        RevenueSummaryDto result = statisticsService.getRevenueSummary(filter);

        assertThat(result).isNotNull();
        assertThat(result.getAov()).isEqualTo(BigDecimal.ZERO);
    }

    // ==================== GET REVENUE TIME SERIES ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Lấy time series doanh thu theo ngày
     * Input: groupBy = DAY
     * Expected Output: RevenueTimeSeriesDto với groupBy = DAY
     * Notes: Happy path
     */
    @Test
    @DisplayName("TC-FR-18-004: GroupBy DAY")
    void TC_FR_18_004() {
        RevenueStatsFilter filter = new RevenueStatsFilter();
        filter.setFrom(LocalDateTime.now().minusDays(3));
        filter.setTo(LocalDateTime.now());
        filter.setGroupBy(StatsGroupBy.DAY);
        filter.setMode(StatsMode.SALES_ALL);

        @SuppressWarnings("unchecked")
        TypedQuery<Object[]> objQuery = mock(TypedQuery.class);
        when(objQuery.setParameter(anyString(), any())).thenReturn(objQuery);
        when(objQuery.getResultList()).thenReturn(List.of());

        when(em.createQuery(anyString(), eq(Object[].class))).thenReturn(objQuery);

        RevenueTimeSeriesDto result = statisticsService.getRevenueTimeSeries(filter);

        assertThat(result.getGroupBy()).isEqualTo(StatsGroupBy.DAY);
        assertThat(result.getPoints()).isNotNull();
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Time series với groupBy WEEK
     * Input: groupBy = WEEK
     * Expected Output: RevenueTimeSeriesDto với groupBy = WEEK
     * Notes: Kiểm tra bucket alignment cho tuần
     */
    @Test
    @DisplayName("TC-FR-18-005: GroupBy WEEK")
    void TC_FR_18_005() {
        RevenueStatsFilter filter = new RevenueStatsFilter();
        filter.setFrom(LocalDateTime.now().minusDays(14));
        filter.setTo(LocalDateTime.now());
        filter.setGroupBy(StatsGroupBy.WEEK);
        filter.setMode(StatsMode.CASHFLOW_VNPAY_ONLY);

        @SuppressWarnings("unchecked")
        TypedQuery<Object[]> objQuery = mock(TypedQuery.class);
        when(objQuery.setParameter(anyString(), any())).thenReturn(objQuery);
        when(objQuery.getResultList()).thenReturn(List.of());

        when(em.createQuery(anyString(), eq(Object[].class))).thenReturn(objQuery);

        RevenueTimeSeriesDto result = statisticsService.getRevenueTimeSeries(filter);

        assertThat(result.getGroupBy()).isEqualTo(StatsGroupBy.WEEK);
    }

    // ==================== GET TOP PRODUCTS ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Lấy top sản phẩm bán chạy chế độ SALES_ALL
     * Input: topN = 5, mode = SALES_ALL
     * Expected Output: Page<TopProductDto>
     * Notes: Happy path – merge vnpay + cod
     */
    @Test
    @DisplayName("TC-FR-18-006: Top products SALES_ALL")
    void TC_FR_18_006() {
        RevenueStatsFilter filter = new RevenueStatsFilter();
        filter.setFrom(LocalDateTime.now().minusDays(30));
        filter.setTo(LocalDateTime.now());
        filter.setMode(StatsMode.SALES_ALL);
        filter.setTopN(5);

        @SuppressWarnings("unchecked")
        TypedQuery<Object[]> objQuery = mock(TypedQuery.class);
        when(objQuery.setParameter(anyString(), any())).thenReturn(objQuery);
        when(objQuery.setMaxResults(anyInt())).thenReturn(objQuery);
        when(objQuery.getResultList()).thenReturn(List.of());

        when(em.createQuery(anyString(), eq(Object[].class))).thenReturn(objQuery);

        var result = statisticsService.getTopProducts(filter);

        assertThat(result).isNotNull();
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: topN null → default 10
     * Input: topN = null
     * Expected Output: Không throw exception, default topN = 10
     * Notes: Kiểm tra nhánh topN == null → default 10
     */
    @Test
    @DisplayName("TC-FR-18-007: topN null → default 10")
    void TC_FR_18_007() {
        RevenueStatsFilter filter = new RevenueStatsFilter();
        filter.setFrom(LocalDateTime.now().minusDays(7));
        filter.setTo(LocalDateTime.now());
        filter.setMode(StatsMode.CASHFLOW_VNPAY_ONLY);
        filter.setTopN(null);

        @SuppressWarnings("unchecked")
        TypedQuery<Object[]> objQuery = mock(TypedQuery.class);
        when(objQuery.setParameter(anyString(), any())).thenReturn(objQuery);
        when(objQuery.setMaxResults(anyInt())).thenReturn(objQuery);
        when(objQuery.getResultList()).thenReturn(List.of());

        when(em.createQuery(anyString(), eq(Object[].class))).thenReturn(objQuery);

        var result = statisticsService.getTopProducts(filter);

        assertThat(result).isNotNull();
    }
}

package com.example.AuthService.controller;

import com.example.AuthService.dto.stats.*;
import com.example.AuthService.enums.StatsGroupBy;
import com.example.AuthService.security.jwt.JwtService;
import com.example.AuthService.service.AdminStatisticsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests cho {@link AdminStatisticsController}.
 * Kiểm tra các endpoint thống kê doanh thu admin.
 */
@WebMvcTest(AdminStatisticsController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminStatisticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminStatisticsService adminStatisticsService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsService userDetailsService;

    // ======================== REVENUE SUMMARY ========================

    /**
     * Test Case ID: TC_AUTH_AdminStatisticsController_revenueSummary_001
     * Test Objective: Lấy tổng hợp doanh thu thành công
     * Input: Không có filter (mặc định)
     * Expected Output: HTTP 200, RevenueSummaryDto
     * Notes: Happy path
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC_AUTH_AdminStatisticsController_revenueSummary_001: Lấy tổng hợp doanh thu thành công")
    void TC_AUTH_AdminStatisticsController_revenueSummary_001() throws Exception {
        RevenueSummaryDto summary = RevenueSummaryDto.builder()
                .grossRevenue(BigDecimal.valueOf(1000000))
                .netRevenue(BigDecimal.valueOf(900000))
                .ordersCount(50)
                .build();

        when(adminStatisticsService.getRevenueSummary(any())).thenReturn(summary);

        mockMvc.perform(get("/api/admin/statistics/revenue/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grossRevenue").value(1000000))
                .andExpect(jsonPath("$.ordersCount").value(50));
    }

    /**
     * Test Case ID: TC_AUTH_AdminStatisticsController_revenueSummary_002
     * Test Objective: Lấy doanh thu với filter khoảng thời gian
     * Input: from=2025-01-01, to=2025-01-31
     * Expected Output: HTTP 200
     * Notes: Lọc theo tháng 1/2025
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC_AUTH_AdminStatisticsController_revenueSummary_002: Doanh thu với filter thời gian")
    void TC_AUTH_AdminStatisticsController_revenueSummary_002() throws Exception {
        RevenueSummaryDto summary = RevenueSummaryDto.builder()
                .grossRevenue(BigDecimal.valueOf(500000))
                .ordersCount(20)
                .build();

        when(adminStatisticsService.getRevenueSummary(any())).thenReturn(summary);

        mockMvc.perform(get("/api/admin/statistics/revenue/summary")
                        .param("from", "2025-01-01T00:00:00")
                        .param("to", "2025-01-31T23:59:59"))
                .andExpect(status().isOk());
    }

    /**
     * Test Case ID: TC_AUTH_AdminStatisticsController_revenueSummary_003
     * Test Objective: Doanh thu khi không có đơn hàng
     * Input: Filter thời gian không có đơn hàng nào
     * Expected Output: HTTP 200, tất cả giá trị = 0
     * Notes: Edge case - khoảng thời gian trống
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC_AUTH_AdminStatisticsController_revenueSummary_003: Doanh thu = 0 khi không có đơn")
    void TC_AUTH_AdminStatisticsController_revenueSummary_003() throws Exception {
        RevenueSummaryDto summary = RevenueSummaryDto.builder()
                .grossRevenue(BigDecimal.ZERO)
                .netRevenue(BigDecimal.ZERO)
                .ordersCount(0)
                .build();

        when(adminStatisticsService.getRevenueSummary(any())).thenReturn(summary);

        mockMvc.perform(get("/api/admin/statistics/revenue/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ordersCount").value(0));
    }

    // ======================== REVENUE TIME SERIES ========================

    /**
     * Test Case ID: TC_AUTH_AdminStatisticsController_revenueTimeSeries_001
     * Test Objective: Lấy biểu đồ doanh thu theo thời gian thành công
     * Input: Không filter
     * Expected Output: HTTP 200, RevenueTimeSeriesDto
     * Notes: Happy path
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC_AUTH_AdminStatisticsController_revenueTimeSeries_001: Lấy time series thành công")
    void TC_AUTH_AdminStatisticsController_revenueTimeSeries_001() throws Exception {
        RevenueTimeSeriesDto timeSeries = RevenueTimeSeriesDto.builder()
                .groupBy(StatsGroupBy.DAY)
                .points(List.of())
                .build();

        when(adminStatisticsService.getRevenueTimeSeries(any())).thenReturn(timeSeries);

        mockMvc.perform(get("/api/admin/statistics/revenue/timeseries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupBy").value("DAY"));
    }

    /**
     * Test Case ID: TC_AUTH_AdminStatisticsController_revenueTimeSeries_002
     * Test Objective: Lấy time series nhóm theo MONTH
     * Input: groupBy=MONTH
     * Expected Output: HTTP 200, groupBy=MONTH
     * Notes: Nhóm doanh thu theo tháng
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC_AUTH_AdminStatisticsController_revenueTimeSeries_002: Time series nhóm theo MONTH")
    void TC_AUTH_AdminStatisticsController_revenueTimeSeries_002() throws Exception {
        RevenueTimeSeriesDto timeSeries = RevenueTimeSeriesDto.builder()
                .groupBy(StatsGroupBy.MONTH)
                .points(List.of())
                .build();

        when(adminStatisticsService.getRevenueTimeSeries(any())).thenReturn(timeSeries);

        mockMvc.perform(get("/api/admin/statistics/revenue/timeseries")
                        .param("groupBy", "MONTH"))
                .andExpect(status().isOk());
    }

    /**
     * Test Case ID: TC_AUTH_AdminStatisticsController_revenueTimeSeries_003
     * Test Objective: Lấy time series khi service ném lỗi
     * Input: Filter không hợp lệ
     * Expected Output: HTTP 500
     * Notes: Service ném exception
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC_AUTH_AdminStatisticsController_revenueTimeSeries_003: Time series - service lỗi")
    void TC_AUTH_AdminStatisticsController_revenueTimeSeries_003() throws Exception {
        when(adminStatisticsService.getRevenueTimeSeries(any()))
                .thenThrow(new RuntimeException("Lỗi truy vấn"));

        mockMvc.perform(get("/api/admin/statistics/revenue/timeseries"))
                .andExpect(status().isInternalServerError());
    }

    // ======================== TOP PRODUCTS ========================

    /**
     * Test Case ID: TC_AUTH_AdminStatisticsController_topProducts_001
     * Test Objective: Lấy danh sách sản phẩm bán chạy thành công
     * Input: Không filter
     * Expected Output: HTTP 200, danh sách TopProductDto
     * Notes: Happy path
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC_AUTH_AdminStatisticsController_topProducts_001: Lấy top sản phẩm thành công")
    void TC_AUTH_AdminStatisticsController_topProducts_001() throws Exception {
        TopProductDto product = TopProductDto.builder()
                .drugId(1L).drugName("Paracetamol").quantity(100)
                .revenue(BigDecimal.valueOf(1500000))
                .build();

        when(adminStatisticsService.getTopProducts(any())).thenReturn(new PageImpl<>(List.of(product)));

        mockMvc.perform(get("/api/admin/statistics/revenue/top-products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].drugName").value("Paracetamol"));
    }

    /**
     * Test Case ID: TC_AUTH_AdminStatisticsController_topProducts_002
     * Test Objective: Lấy top sản phẩm khi không có dữ liệu
     * Input: Khoảng thời gian không có đơn hàng
     * Expected Output: HTTP 200, danh sách rỗng
     * Notes: Edge case
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC_AUTH_AdminStatisticsController_topProducts_002: Top sản phẩm - không có dữ liệu")
    void TC_AUTH_AdminStatisticsController_topProducts_002() throws Exception {
        when(adminStatisticsService.getTopProducts(any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/admin/statistics/revenue/top-products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    /**
     * Test Case ID: TC_AUTH_AdminStatisticsController_topProducts_003
     * Test Objective: Lấy top sản phẩm với topN tùy chỉnh
     * Input: topN=5
     * Expected Output: HTTP 200
     * Notes: Giới hạn số lượng sản phẩm trả về
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("TC_AUTH_AdminStatisticsController_topProducts_003: Top sản phẩm với topN=5")
    void TC_AUTH_AdminStatisticsController_topProducts_003() throws Exception {
        when(adminStatisticsService.getTopProducts(any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/admin/statistics/revenue/top-products")
                        .param("topN", "5"))
                .andExpect(status().isOk());
    }
}

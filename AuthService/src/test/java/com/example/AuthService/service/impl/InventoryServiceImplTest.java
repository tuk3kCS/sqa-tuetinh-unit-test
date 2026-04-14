package com.example.AuthService.service.impl;

import com.example.AuthService.entity.Drug;
import com.example.AuthService.repository.DrugRepository;
import com.example.AuthService.repository.ImportInvoiceDetailRepository;
import com.example.AuthService.repository.OrderItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests cho InventoryServiceImpl – kiểm tra tính toán tồn kho.
 */
@ExtendWith(MockitoExtension.class)
class InventoryServiceImplTest {

    @Mock private ImportInvoiceDetailRepository importRepo;
    @Mock private OrderItemRepository orderItemRepo;
    @Mock private DrugRepository drugRepository;

    @InjectMocks
    private InventoryServiceImpl inventoryService;

    private Drug drug;

    @BeforeEach
    void setUp() {
        drug = Drug.builder().id(1L).name("Aspirin")
                .price(BigDecimal.valueOf(10000))
                .importPrice(BigDecimal.valueOf(5000))
                .soldQuantity(20).reservedQuantity(5)
                .build();
    }

    // ==================== CALCULATE STOCK ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Tính tồn kho đúng = imported - sold
     * Input: drugId hợp lệ, imported = 100, sold = 20
     * Expected Output: 80
     * Notes: Happy path – stock = totalImported - soldQuantity
     */
    @Test
    @DisplayName("TC-FR-02-001: Tính tồn kho đúng")
    void TC_FR_02_001() {
        when(importRepo.totalImported(1L)).thenReturn(100);
        when(drugRepository.findById(1L)).thenReturn(Optional.of(drug));

        Integer stock = inventoryService.calculateStock(1L);

        assertThat(stock).isEqualTo(80);
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Tính tồn kho khi imported = 0
     * Input: imported = 0, sold = 0
     * Expected Output: 0
     * Notes: Edge case – kho trống
     */
    @Test
    @DisplayName("TC-FR-02-001: Kho trống → 0")
    void TC_FR_02_001() {
        drug.setSoldQuantity(0);
        when(importRepo.totalImported(1L)).thenReturn(0);
        when(drugRepository.findById(1L)).thenReturn(Optional.of(drug));

        Integer stock = inventoryService.calculateStock(1L);

        assertThat(stock).isEqualTo(0);
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Tính tồn kho thất bại khi drug không tồn tại
     * Input: drugId = 999
     * Expected Output: RuntimeException "Drug not found"
     * Notes: Kiểm tra nhánh drugRepository.findById trả về empty
     */
    @Test
    @DisplayName("TC-FR-02-001: Drug không tồn tại → exception")
    void TC_FR_02_001() {
        when(importRepo.totalImported(999L)).thenReturn(0);
        when(drugRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.calculateStock(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Drug not found");
    }

    // ==================== CALCULATE STOCK FOR DRUGS ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: calculateStockForDrugs trả về map rỗng (chưa implement)
     * Input: list drugIds
     * Expected Output: Map rỗng
     * Notes: Phương thức hiện tại return Map.of()
     */
    @Test
    @DisplayName("TC-FR-02-001: Trả về map rỗng")
    void TC_FR_02_001() {
        Map<Long, Integer> result = inventoryService.calculateStockForDrugs(List.of(1L, 2L));

        assertThat(result).isEmpty();
    }

    // ==================== GET TOTAL IMPORTED FOR DRUGS ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Lấy tổng số lượng nhập cho nhiều drugs
     * Input: drugIds = [1, 2], DB trả về dữ liệu
     * Expected Output: Map chứa drugId → totalImported
     * Notes: Happy path – có dữ liệu nhập kho
     */
    @Test
    @DisplayName("TC-FR-02-001: Lấy tổng nhập thành công")
    void TC_FR_02_001() {
        List<Object[]> mockResults = List.of(
                new Object[]{1L, 100L},
                new Object[]{2L, 200L}
        );
        when(importRepo.findTotalImportedForDrugs(List.of(1L, 2L))).thenReturn(mockResults);

        Map<Long, Integer> result = inventoryService.getTotalImportedForDrugs(List.of(1L, 2L));

        assertThat(result).hasSize(2);
        assertThat(result.get(1L)).isEqualTo(100);
        assertThat(result.get(2L)).isEqualTo(200);
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Lấy tổng nhập khi không có dữ liệu
     * Input: drugIds = [999], DB trả về danh sách rỗng
     * Expected Output: Map rỗng
     * Notes: Edge case – không có phiếu nhập nào
     */
    @Test
    @DisplayName("TC-FR-02-001: Không có dữ liệu → map rỗng")
    void TC_FR_02_001() {
        when(importRepo.findTotalImportedForDrugs(List.of(999L))).thenReturn(List.of());

        Map<Long, Integer> result = inventoryService.getTotalImportedForDrugs(List.of(999L));

        assertThat(result).isEmpty();
    }
}

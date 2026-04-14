package com.example.AuthService.service.impl;

import com.example.AuthService.dto.request.ImportInvoiceDetailRequest;
import com.example.AuthService.dto.request.ImportInvoiceRequest;
import com.example.AuthService.dto.response.ImportInvoiceResponse;
import com.example.AuthService.entity.Drug;
import com.example.AuthService.entity.ImportInvoice;
import com.example.AuthService.entity.ImportInvoiceDetail;
import com.example.AuthService.repository.DrugRepository;
import com.example.AuthService.repository.ImportInvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests cho ImportInvoiceServiceImpl – kiểm tra import Excel, CRUD phiếu nhập.
 */
@ExtendWith(MockitoExtension.class)
class ImportInvoiceServiceImplTest {

    @Mock private ImportInvoiceRepository invoiceRepository;
    @Mock private DrugRepository drugRepository;

    @InjectMocks
    private ImportInvoiceServiceImpl invoiceService;

    private Drug drug;
    private ImportInvoice invoice;

    @BeforeEach
    void setUp() {
        drug = Drug.builder().id(1L).name("Paracetamol")
                .price(BigDecimal.valueOf(50000))
                .importPrice(BigDecimal.valueOf(30000))
                .build();

        ImportInvoiceDetail detail = new ImportInvoiceDetail();
        detail.setId(1L);
        detail.setDrug(drug);
        detail.setQuantity(100);

        invoice = new ImportInvoice();
        invoice.setId(1L);
        invoice.setName("Invoice_01");
        invoice.setCreatedAt(LocalDateTime.now());
        invoice.setDetails(new ArrayList<>(List.of(detail)));
        detail.setInvoice(invoice);
    }

    // ==================== CREATE ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Tạo phiếu nhập thành công từ request
     * Input: ImportInvoiceRequest hợp lệ với 1 drug
     * Expected Output: ImportInvoiceResponse chứa thông tin phiếu nhập
     * Notes: CheckDB – phiếu nhập mới được lưu vào DB
     */
    @Test
    @DisplayName("TC-FR-02-001: Tạo phiếu nhập thành công")
    void TC_FR_02_001() {
        ImportInvoiceDetailRequest detailReq = new ImportInvoiceDetailRequest();
        detailReq.setDrugName("Paracetamol");
        detailReq.setQuantity(50);

        ImportInvoiceRequest request = new ImportInvoiceRequest();
        request.setName("New Invoice");
        request.setDetails(List.of(detailReq));

        when(drugRepository.findByNameIgnoreCase("Paracetamol")).thenReturn(Optional.of(drug));
        when(invoiceRepository.save(any(ImportInvoice.class))).thenAnswer(inv -> {
            ImportInvoice saved = inv.getArgument(0);
            saved.setId(2L);
            return saved;
        });

        ImportInvoiceResponse result = invoiceService.create(request);

        assertThat(result.getName()).isEqualTo("New Invoice");
        assertThat(result.getDetails()).hasSize(1);
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Tạo phiếu nhập thất bại khi drug không tồn tại
     * Input: ImportInvoiceRequest với drugName không có trong DB
     * Expected Output: RuntimeException "Không tìm thấy thuốc"
     * Notes: Kiểm tra nhánh drugRepository.findByNameIgnoreCase trả về empty
     */
    @Test
    @DisplayName("TC-FR-02-001: Drug không tồn tại → exception")
    void TC_FR_02_001() {
        ImportInvoiceDetailRequest detailReq = new ImportInvoiceDetailRequest();
        detailReq.setDrugName("NonExistent");
        detailReq.setQuantity(10);

        ImportInvoiceRequest request = new ImportInvoiceRequest();
        request.setName("Test");
        request.setDetails(List.of(detailReq));

        when(drugRepository.findByNameIgnoreCase("NonExistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> invoiceService.create(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Không tìm thấy thuốc");
    }

    // ==================== GET BY ID ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Lấy phiếu nhập theo id thành công
     * Input: id hợp lệ
     * Expected Output: ImportInvoiceResponse
     * Notes: Happy path
     */
    @Test
    @DisplayName("TC-FR-02-001: Lấy phiếu nhập thành công")
    void TC_FR_02_001() {
        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(invoice));

        ImportInvoiceResponse result = invoiceService.getById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("Invoice_01");
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Lấy phiếu nhập thất bại khi không tồn tại
     * Input: id = 999
     * Expected Output: RuntimeException "Invoice not found"
     * Notes: Kiểm tra nhánh findById trả về empty
     */
    @Test
    @DisplayName("TC-FR-02-001: Không tồn tại → exception")
    void TC_FR_02_001() {
        when(invoiceRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> invoiceService.getById(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invoice not found");
    }

    // ==================== GET ALL ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Lấy tất cả phiếu nhập không có keyword
     * Input: keyword = null
     * Expected Output: Page<ImportInvoiceResponse> từ findAll
     * Notes: Kiểm tra nhánh keyword == null → findAll
     */
    @Test
    @DisplayName("TC-FR-02-001: Không keyword → findAll")
    void TC_FR_02_001() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<ImportInvoice> page = new PageImpl<>(List.of(invoice));

        when(invoiceRepository.findAll(pageable)).thenReturn(page);

        Page<ImportInvoiceResponse> result = invoiceService.getAll(null, pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(invoiceRepository).findAll(pageable);
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Lấy phiếu nhập có keyword filter
     * Input: keyword = "Invoice"
     * Expected Output: Page<ImportInvoiceResponse> từ findByNameContaining
     * Notes: Kiểm tra nhánh keyword != null → findByNameContainingIgnoreCase
     */
    @Test
    @DisplayName("TC-FR-02-001: Có keyword → filter theo tên")
    void TC_FR_02_001() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<ImportInvoice> page = new PageImpl<>(List.of(invoice));

        when(invoiceRepository.findByNameContainingIgnoreCase("Invoice", pageable)).thenReturn(page);

        Page<ImportInvoiceResponse> result = invoiceService.getAll("Invoice", pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(invoiceRepository).findByNameContainingIgnoreCase("Invoice", pageable);
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Keyword trống → dùng findAll thay vì filter
     * Input: keyword = "   " (blank)
     * Expected Output: Gọi findAll
     * Notes: Kiểm tra nhánh keyword.isBlank()
     */
    @Test
    @DisplayName("TC-FR-02-001: Keyword blank → findAll")
    void TC_FR_02_001() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<ImportInvoice> page = new PageImpl<>(List.of());

        when(invoiceRepository.findAll(pageable)).thenReturn(page);

        invoiceService.getAll("   ", pageable);

        verify(invoiceRepository).findAll(pageable);
    }

    // ==================== DELETE ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Xóa phiếu nhập thành công
     * Input: id hợp lệ
     * Expected Output: deleteById được gọi
     * Notes: CheckDB – phiếu nhập bị xóa
     */
    @Test
    @DisplayName("TC-FR-02-001: Xóa thành công")
    void TC_FR_02_001() {
        invoiceService.delete(1L);

        verify(invoiceRepository).deleteById(1L);
    }
}

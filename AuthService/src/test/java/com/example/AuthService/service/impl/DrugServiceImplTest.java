package com.example.AuthService.service.impl;

import com.example.AuthService.dto.DrugFilter;
import com.example.AuthService.dto.response.DrugResponse;
import com.example.AuthService.entity.Drug;
import com.example.AuthService.entity.Section;
import com.example.AuthService.repository.DrugRepository;
import com.example.AuthService.service.CloudinaryService;
import com.example.AuthService.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests cho DrugServiceImpl – kiểm tra CRUD thuốc, upload ảnh, phân trang, tìm kiếm.
 */
@ExtendWith(MockitoExtension.class)
class DrugServiceImplTest {

    @Mock private DrugRepository drugRepository;
    @Mock private CloudinaryService cloudinaryService;
    @Mock private InventoryService inventoryService;

    @InjectMocks
    private DrugServiceImpl drugService;

    private Drug drug;

    @BeforeEach
    void setUp() {
        drug = Drug.builder()
                .id(1L).name("Paracetamol").title("Giảm đau")
                .price(BigDecimal.valueOf(50000))
                .importPrice(BigDecimal.valueOf(30000))
                .soldQuantity(0).reservedQuantity(0)
                .isActive(true)
                .sections(new ArrayList<>())
                .build();
    }

    // ==================== CREATE DRUG ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Tạo thuốc mới thành công
     * Input: Drug entity hợp lệ
     * Expected Output: Drug được lưu vào DB
     * Notes: CheckDB – drug mới xuất hiện trong DB
     */
    @Test
    @DisplayName("TC-FR-12-015: Tạo thuốc thành công")
    void TC_FR_12_015() {
        when(drugRepository.save(drug)).thenReturn(drug);

        Drug result = drugService.createDrug(drug);

        assertThat(result).isEqualTo(drug);
        verify(drugRepository).save(drug);
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Tạo thuốc với sections, set quan hệ drug cho từng section
     * Input: Drug có danh sách sections
     * Expected Output: Mỗi section có drug được gán đúng
     * Notes: Kiểm tra nhánh drug.getSections() != null
     */
    @Test
    @DisplayName("TC-FR-14-040: Tạo thuốc có sections → set drug cho sections")
    void TC_FR_14_040() {
        Section section = new Section();
        section.setTitle("Composition");
        drug.setSections(new ArrayList<>(List.of(section)));

        when(drugRepository.save(drug)).thenReturn(drug);

        drugService.createDrug(drug);

        assertThat(section.getDrug()).isEqualTo(drug);
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Tạo thuốc với sections = null
     * Input: Drug có sections = null
     * Expected Output: Không throw exception
     * Notes: Kiểm tra nhánh drug.getSections() == null
     */
    @Test
    @DisplayName("TC-FR-15-001: Sections null → không lỗi")
    void TC_FR_15_001() {
        drug.setSections(null);
        when(drugRepository.save(drug)).thenReturn(drug);

        Drug result = drugService.createDrug(drug);

        assertThat(result).isNotNull();
    }

    // ==================== CREATE DRUG WITH IMAGE ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Tạo thuốc có upload ảnh thành công
     * Input: Drug + MultipartFile hợp lệ
     * Expected Output: Drug có image URL từ Cloudinary
     * Notes: CheckDB – drug.image = URL trả về từ cloudinary
     */
    @Test
    @DisplayName("TC-FR-15-002: Upload ảnh thành công")
    void TC_FR_15_002() {
        MultipartFile mockFile = mock(MultipartFile.class);
        when(cloudinaryService.uploadImage(mockFile)).thenReturn("https://cloudinary.com/img.jpg");
        when(drugRepository.save(any(Drug.class))).thenAnswer(inv -> inv.getArgument(0));

        Drug result = drugService.createDrugWithImage(drug, mockFile);

        assertThat(result.getImage()).isEqualTo("https://cloudinary.com/img.jpg");
    }

    // ==================== UPDATE DRUG ACTIVE ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Cập nhật trạng thái active thành công
     * Input: Drug id hợp lệ, active = false
     * Expected Output: Drug.isActive = false
     * Notes: CheckDB – isActive thay đổi
     */
    @Test
    @DisplayName("TC-FR-15-003: Set active = false thành công")
    void TC_FR_15_003() {
        when(drugRepository.findById(1L)).thenReturn(Optional.of(drug));
        when(drugRepository.save(drug)).thenReturn(drug);

        Drug result = drugService.updateDrugActive(1L, false);

        assertThat(result.getIsActive()).isFalse();
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Cập nhật active thất bại khi drug không tồn tại
     * Input: Drug id = 999
     * Expected Output: RuntimeException "Không tìm thấy thuốc"
     * Notes: Kiểm tra nhánh findById trả về empty
     */
    @Test
    @DisplayName("TC-FR-15-004: Drug không tồn tại → exception")
    void TC_FR_15_004() {
        when(drugRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> drugService.updateDrugActive(999L, true))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Không tìm thấy thuốc");
    }

    // ==================== UPDATE DRUG WITH IMAGE ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Cập nhật thuốc có ảnh mới thành công
     * Input: Drug id hợp lệ, có ảnh mới upload
     * Expected Output: Drug.image = URL mới, các field cơ bản được cập nhật
     * Notes: Kiểm tra nhánh image != null && !image.isEmpty()
     */
    @Test
    @DisplayName("TC-FR-15-005: Cập nhật có ảnh mới")
    void TC_FR_15_005() {
        Drug updated = Drug.builder()
                .name("Updated Name").title("Updated Title")
                .price(BigDecimal.valueOf(60000)).importPrice(BigDecimal.valueOf(35000))
                .sections(null)
                .build();

        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.isEmpty()).thenReturn(false);
        when(drugRepository.findById(1L)).thenReturn(Optional.of(drug));
        when(cloudinaryService.uploadImage(mockFile)).thenReturn("https://new-url.jpg");
        when(drugRepository.save(drug)).thenReturn(drug);

        Drug result = drugService.updateDrugWithImage(1L, updated, mockFile);

        assertThat(result.getName()).isEqualTo("Updated Name");
        assertThat(result.getImage()).isEqualTo("https://new-url.jpg");
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Cập nhật thuốc không có ảnh mới (giữ ảnh cũ)
     * Input: image = null
     * Expected Output: Drug.image giữ nguyên
     * Notes: Kiểm tra nhánh image == null
     */
    @Test
    @DisplayName("TC-FR-15-006: Không có ảnh → giữ ảnh cũ")
    void TC_FR_15_006() {
        drug.setImage("old-image.jpg");
        Drug updated = Drug.builder()
                .name("X").title("Y")
                .price(BigDecimal.valueOf(1)).importPrice(BigDecimal.valueOf(1))
                .build();

        when(drugRepository.findById(1L)).thenReturn(Optional.of(drug));
        when(drugRepository.save(drug)).thenReturn(drug);

        Drug result = drugService.updateDrugWithImage(1L, updated, null);

        assertThat(result.getImage()).isEqualTo("old-image.jpg");
        verify(cloudinaryService, never()).uploadImage(any());
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Cập nhật thuốc thất bại khi drug không tồn tại
     * Input: id = 999
     * Expected Output: RuntimeException "Drug not found"
     * Notes: Kiểm tra nhánh findById trả về empty
     */
    @Test
    @DisplayName("TC-FR-15-007: Drug không tồn tại → exception")
    void TC_FR_15_007() {
        when(drugRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> drugService.updateDrugWithImage(999L, drug, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Drug not found");
    }

    // ==================== DELETE DRUG ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Xóa thuốc thành công
     * Input: Drug id tồn tại
     * Expected Output: deleteById được gọi
     * Notes: CheckDB – drug bị xóa khỏi DB
     */
    @Test
    @DisplayName("TC-FR-15-008: Xóa thành công")
    void TC_FR_15_008() {
        when(drugRepository.existsById(1L)).thenReturn(true);

        drugService.deleteDrug(1L);

        verify(drugRepository).deleteById(1L);
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Xóa thuốc thất bại khi không tồn tại
     * Input: Drug id không tồn tại
     * Expected Output: RuntimeException "Drug not found"
     * Notes: Kiểm tra nhánh existsById == false
     */
    @Test
    @DisplayName("TC-FR-15-009: Drug không tồn tại → exception")
    void TC_FR_15_009() {
        when(drugRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> drugService.deleteDrug(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Drug not found");
    }

    // ==================== GET DRUG BY ID ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Lấy thuốc theo id thành công
     * Input: id hợp lệ
     * Expected Output: Drug entity
     * Notes: Happy path
     */
    @Test
    @DisplayName("TC-FR-15-010: Tìm thuốc thành công")
    void TC_FR_15_010() {
        when(drugRepository.findById(1L)).thenReturn(Optional.of(drug));

        Drug result = drugService.getDrugById(1L);

        assertThat(result).isEqualTo(drug);
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Lấy thuốc thất bại khi id không tồn tại
     * Input: id = 999
     * Expected Output: RuntimeException "Drug not found"
     * Notes: Kiểm tra nhánh findById empty
     */
    @Test
    @DisplayName("TC-FR-15-011: Drug không tồn tại → exception")
    void TC_FR_15_011() {
        when(drugRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> drugService.getDrugById(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Drug not found");
    }

    // ==================== GET DRUGS (PAGED) ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Lấy danh sách thuốc phân trang cho admin
     * Input: isAdmin = true, pageable hợp lệ
     * Expected Output: Page<DrugResponse> có nội dung
     * Notes: Admin không bị ép filter isActive = true
     */
    @Test
    @DisplayName("TC-FR-15-012: Admin xem danh sách thuốc")
    void TC_FR_15_012() {
        DrugFilter filter = new DrugFilter();
        Pageable pageable = PageRequest.of(0, 10, Sort.by("id").descending());
        Page<Drug> drugPage = new PageImpl<>(List.of(drug), pageable, 1);

        when(drugRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(drugPage);
        when(inventoryService.getTotalImportedForDrugs(anyList())).thenReturn(Map.of(1L, 100));

        Page<DrugResponse> result = drugService.getDrugs(filter, pageable, true);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getStockQuantity()).isEqualTo(100);
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Lấy danh sách thuốc cho user – chỉ thấy active
     * Input: isAdmin = false
     * Expected Output: filter.isActive tự động set true
     * Notes: Kiểm tra nhánh !isAdmin → setIsActive(true)
     */
    @Test
    @DisplayName("TC-FR-15-013: User chỉ thấy thuốc active")
    void TC_FR_15_013() {
        DrugFilter filter = new DrugFilter();
        Pageable pageable = PageRequest.of(0, 10, Sort.by("id").descending());
        Page<Drug> drugPage = new PageImpl<>(List.of(drug), pageable, 1);

        when(drugRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(drugPage);
        when(inventoryService.getTotalImportedForDrugs(anyList())).thenReturn(Map.of(1L, 50));

        drugService.getDrugs(filter, pageable, false);

        assertThat(filter.getIsActive()).isTrue();
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Pageable null → sử dụng default sort
     * Input: pageable = null
     * Expected Output: Không throw exception, default sort by id desc
     * Notes: Kiểm tra nhánh pageable == null
     */
    @Test
    @DisplayName("TC-FR-15-014: Pageable null → default")
    void TC_FR_15_014() {
        DrugFilter filter = new DrugFilter();
        Page<Drug> drugPage = new PageImpl<>(List.of(drug));

        when(drugRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(drugPage);
        when(inventoryService.getTotalImportedForDrugs(anyList())).thenReturn(Map.of());

        Page<DrugResponse> result = drugService.getDrugs(filter, null, true);

        assertThat(result).isNotNull();
    }

    // ==================== SUGGEST NAMES ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Gợi ý tên thuốc thành công
     * Input: q = "para", limit = 5
     * Expected Output: Danh sách tên thuốc chứa "para"
     * Notes: Happy path
     */
    @Test
    @DisplayName("TC-FR-15-015: Gợi ý tên thành công")
    void TC_FR_15_015() {
        when(drugRepository.suggestNames(eq("para"), any(PageRequest.class)))
                .thenReturn(List.of("Paracetamol", "Paracetamol 500"));

        List<String> result = drugService.suggestNames("para", 5);

        assertThat(result).hasSize(2);
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Gợi ý với q = null → sử dụng chuỗi rỗng
     * Input: q = null, limit = 10
     * Expected Output: Không throw exception
     * Notes: Kiểm tra nhánh q == null → ""
     */
    @Test
    @DisplayName("TC-FR-15-016: q null → sử dụng chuỗi rỗng")
    void TC_FR_15_016() {
        when(drugRepository.suggestNames(eq(""), any(PageRequest.class)))
                .thenReturn(List.of());

        List<String> result = drugService.suggestNames(null, 10);

        assertThat(result).isEmpty();
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Limit <= 0 → sử dụng default 10
     * Input: limit = 0
     * Expected Output: PageRequest.of(0, 10) được sử dụng
     * Notes: Kiểm tra nhánh limit <= 0
     */
    @Test
    @DisplayName("TC-FR-15-018: Limit <= 0 → default 10")
    void TC_FR_15_018() {
        when(drugRepository.suggestNames(anyString(), any(PageRequest.class)))
                .thenReturn(List.of());

        drugService.suggestNames("x", 0);

        verify(drugRepository).suggestNames(eq("x"), eq(PageRequest.of(0, 10)));
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Limit > 20 → sử dụng default 10
     * Input: limit = 50
     * Expected Output: PageRequest.of(0, 10) được sử dụng
     * Notes: Kiểm tra nhánh limit > 20
     */
    @Test
    @DisplayName("TC-FR-15-022: Limit > 20 → default 10")
    void TC_FR_15_022() {
        when(drugRepository.suggestNames(anyString(), any(PageRequest.class)))
                .thenReturn(List.of());

        drugService.suggestNames("x", 50);

        verify(drugRepository).suggestNames(eq("x"), eq(PageRequest.of(0, 10)));
    }
}

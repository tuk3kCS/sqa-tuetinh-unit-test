package com.example.AuthService.service.impl;

import com.example.AuthService.entity.Drug;
import com.example.AuthService.entity.Section;
import com.example.AuthService.repository.DrugRepository;
import com.example.AuthService.repository.SectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests cho SectionServiceImpl – kiểm tra CRUD section (mục thông tin thuốc).
 */
@ExtendWith(MockitoExtension.class)
class SectionServiceImplTest {

    @Mock private SectionRepository sectionRepository;
    @Mock private DrugRepository drugRepository;

    @InjectMocks
    private SectionServiceImpl sectionService;

    private Drug drug;
    private Section section;

    @BeforeEach
    void setUp() {
        drug = Drug.builder().id(1L).name("Aspirin")
                .price(BigDecimal.valueOf(10000)).importPrice(BigDecimal.valueOf(5000)).build();

        section = new Section();
        section.setId(1L);
        section.setTitle("Composition");
        section.setContent("Aspirin 500mg");
        section.setDrug(drug);
    }

    // ==================== LIST BY DRUG ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Lấy danh sách sections theo drugId thành công
     * Input: drugId tồn tại
     * Expected Output: List<Section> không rỗng
     * Notes: Happy path
     */
    @Test
    @DisplayName("TC-FR-15-019: Lấy sections thành công")
    void TC_FR_15_019() {
        when(drugRepository.existsById(1L)).thenReturn(true);
        when(sectionRepository.findByDrugIdOrderByIdAsc(1L)).thenReturn(List.of(section));

        List<Section> result = sectionService.listByDrug(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Composition");
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Lấy sections thất bại khi drug không tồn tại
     * Input: drugId không có
     * Expected Output: RuntimeException "Drug not found"
     * Notes: Kiểm tra nhánh existsById == false
     */
    @Test
    @DisplayName("TC-FR-15-020: Drug không tồn tại → exception")
    void TC_FR_15_020() {
        when(drugRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> sectionService.listByDrug(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Drug not found");
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Drug tồn tại nhưng không có sections
     * Input: drugId tồn tại, không có sections
     * Expected Output: List rỗng
     * Notes: Edge case – danh sách rỗng
     */
    @Test
    @DisplayName("TC-FR-15-073: Không có sections → list rỗng")
    void TC_FR_15_073() {
        when(drugRepository.existsById(1L)).thenReturn(true);
        when(sectionRepository.findByDrugIdOrderByIdAsc(1L)).thenReturn(List.of());

        List<Section> result = sectionService.listByDrug(1L);

        assertThat(result).isEmpty();
    }

    // ==================== CREATE ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Tạo section mới thành công
     * Input: drugId hợp lệ, payload có title/content
     * Expected Output: Section mới với drug được gán
     * Notes: CheckDB – section mới được lưu, drug lấy từ path
     */
    @Test
    @DisplayName("TC-FR-15-074: Tạo section thành công")
    void TC_FR_15_074() {
        Section payload = new Section();
        payload.setTitle("Dosage");
        payload.setContent("Take 1 tablet");

        when(drugRepository.findById(1L)).thenReturn(Optional.of(drug));
        when(sectionRepository.save(any(Section.class))).thenAnswer(inv -> {
            Section s = inv.getArgument(0);
            s.setId(2L);
            return s;
        });

        Section result = sectionService.create(1L, payload);

        assertThat(result.getTitle()).isEqualTo("Dosage");
        assertThat(result.getDrug()).isEqualTo(drug);
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Tạo section thất bại khi drug không tồn tại
     * Input: drugId = 999
     * Expected Output: RuntimeException "Drug not found"
     * Notes: Kiểm tra nhánh drugRepository.findById trả về empty
     */
    @Test
    @DisplayName("TC-FR-15-075: Drug không tồn tại → exception")
    void TC_FR_15_075() {
        when(drugRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sectionService.create(999L, new Section()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Drug not found");
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Tạo section với payload null
     * Input: payload = null
     * Expected Output: Section mới với title/content = null
     * Notes: Kiểm tra nhánh payload == null → title/content null
     */
    @Test
    @DisplayName("TC-FR-15-076: Payload null → section với null fields")
    void TC_FR_15_076() {
        when(drugRepository.findById(1L)).thenReturn(Optional.of(drug));
        when(sectionRepository.save(any(Section.class))).thenAnswer(inv -> inv.getArgument(0));

        Section result = sectionService.create(1L, null);

        assertThat(result.getTitle()).isNull();
        assertThat(result.getContent()).isNull();
    }

    // ==================== GET BY ID ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Lấy section theo id thành công
     * Input: id = 1
     * Expected Output: Section entity
     * Notes: Happy path
     */
    @Test
    @DisplayName("TC-FR-15-077: Tìm section thành công")
    void TC_FR_15_077() {
        when(sectionRepository.findById(1L)).thenReturn(Optional.of(section));

        Section result = sectionService.getById(1L);

        assertThat(result.getId()).isEqualTo(1L);
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Lấy section thất bại khi id không tồn tại
     * Input: id = 999
     * Expected Output: RuntimeException "Section not found"
     * Notes: Kiểm tra nhánh findById trả về empty
     */
    @Test
    @DisplayName("TC-FR-15-078: Section không tồn tại → exception")
    void TC_FR_15_078() {
        when(sectionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sectionService.getById(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Section not found");
    }

    // ==================== UPDATE ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Cập nhật section thành công
     * Input: id hợp lệ, payload có title/content mới
     * Expected Output: Section được cập nhật
     * Notes: CheckDB – title/content thay đổi
     */
    @Test
    @DisplayName("TC-FR-15-079: Cập nhật section thành công")
    void TC_FR_15_079() {
        Section payload = new Section();
        payload.setTitle("New Title");
        payload.setContent("New Content");

        when(sectionRepository.findById(1L)).thenReturn(Optional.of(section));
        when(sectionRepository.save(section)).thenReturn(section);

        Section result = sectionService.update(1L, payload);

        assertThat(result.getTitle()).isEqualTo("New Title");
        assertThat(result.getContent()).isEqualTo("New Content");
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Cập nhật với payload null → giữ nguyên
     * Input: payload = null
     * Expected Output: Section giữ nguyên
     * Notes: Kiểm tra nhánh payload == null
     */
    @Test
    @DisplayName("TC-FR-15-080: Payload null → giữ nguyên")
    void TC_FR_15_080() {
        when(sectionRepository.findById(1L)).thenReturn(Optional.of(section));
        when(sectionRepository.save(section)).thenReturn(section);

        Section result = sectionService.update(1L, null);

        assertThat(result.getTitle()).isEqualTo("Composition");
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Cập nhật thất bại khi section không tồn tại
     * Input: id = 999
     * Expected Output: RuntimeException "Section not found"
     * Notes: Kiểm tra nhánh findById trả về empty
     */
    @Test
    @DisplayName("TC-FR-15-082: Section không tồn tại → exception")
    void TC_FR_15_082() {
        when(sectionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sectionService.update(999L, new Section()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Section not found");
    }

    // ==================== DELETE ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Xóa section thành công
     * Input: id tồn tại
     * Expected Output: deleteById được gọi
     * Notes: CheckDB – section bị xóa
     */
    @Test
    @DisplayName("TC-FR-15-083: Xóa section thành công")
    void TC_FR_15_083() {
        when(sectionRepository.existsById(1L)).thenReturn(true);

        sectionService.delete(1L);

        verify(sectionRepository).deleteById(1L);
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Xóa section thất bại khi không tồn tại
     * Input: id = 999
     * Expected Output: RuntimeException "Section not found"
     * Notes: Kiểm tra nhánh existsById == false
     */
    @Test
    @DisplayName("TC-FR-15-084: Section không tồn tại → exception")
    void TC_FR_15_084() {
        when(sectionRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> sectionService.delete(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Section not found");
    }

    // ==================== LIST ALL ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Lấy tất cả sections
     * Input: không có
     * Expected Output: List<Section>
     * Notes: Happy path
     */
    @Test
    @DisplayName("TC-FR-15-086: Lấy tất cả sections")
    void TC_FR_15_086() {
        when(sectionRepository.findAllByOrderByIdAsc()).thenReturn(List.of(section));

        List<Section> result = sectionService.listAll();

        assertThat(result).hasSize(1);
    }
}

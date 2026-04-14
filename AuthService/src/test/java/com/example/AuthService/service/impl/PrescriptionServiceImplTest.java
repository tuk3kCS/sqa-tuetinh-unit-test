package com.example.AuthService.service.impl;

import com.example.AuthService.dto.request.*;
import com.example.AuthService.entity.*;
import com.example.AuthService.enums.FrequencyType;
import com.example.AuthService.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests cho PrescriptionServiceImpl – kiểm tra CRUD đơn thuốc, lịch uống thuốc.
 */
@ExtendWith(MockitoExtension.class)
class PrescriptionServiceImplTest {

    @Mock private PrescriptionRepository prescriptionRepository;
    @Mock private DrugInPrescriptionRepository drugInPrescriptionRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private DrugRepository drugRepository;
    @Mock private UnitRepository unitRepository;

    @InjectMocks
    private PrescriptionServiceImpl prescriptionService;

    private User user;
    private Unit unit;

    @BeforeEach
    void setUp() {
        Role role = Role.builder().id(1L).name("USER").build();
        user = User.builder().id(1L).email("user@test.com").name("Test User").role(role).build();
        unit = new Unit();
        unit.setId(1L);
        unit.setName("viên");
    }

    private DrugInPresRequest buildDrugReq() {
        return DrugInPresRequest.builder()
                .drugName("Paracetamol")
                .unitId(1L)
                .startDate(LocalDate.now().toString())
                .endDate(LocalDate.now().plusDays(7).toString())
                .frequencyType(FrequencyType.DAILY)
                .schedules(List.of())
                .build();
    }

    // ==================== CREATE PRESCRIPTION ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Tạo đơn thuốc thành công
     * Input: PrescriptionRequest hợp lệ với 1 drug
     * Expected Output: Prescription entity được lưu
     * Notes: CheckDB – prescription + drugInPrescription + schedules được tạo
     */
    @Test
    @DisplayName("TC-FR-12-001: Tạo đơn thuốc thành công")
    void TC_FR_12_001() {
        PrescriptionRequest req = PrescriptionRequest.builder()
                .name("Don thuoc 1")
                .drugs(List.of(buildDrugReq()))
                .build();

        when(prescriptionRepository.save(any(Prescription.class))).thenAnswer(inv -> {
            Prescription p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });
        when(unitRepository.findById(1L)).thenReturn(Optional.of(unit));
        when(drugInPrescriptionRepository.save(any())).thenAnswer(inv -> {
            DrugInPrescription d = inv.getArgument(0);
            d.setId(1L);
            return d;
        });

        Prescription result = prescriptionService.createPrescription(req, user);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Don thuoc 1");
        verify(scheduleRepository).saveAll(anyList());
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Tạo đơn thuốc thất bại khi user null
     * Input: user = null
     * Expected Output: IllegalArgumentException "User must not be null"
     * Notes: Kiểm tra nhánh user == null
     */
    @Test
    @DisplayName("TC-FR-12-002: User null → exception")
    void TC_FR_12_002() {
        PrescriptionRequest req = PrescriptionRequest.builder()
                .name("Test").drugs(List.of(buildDrugReq())).build();

        assertThatThrownBy(() -> prescriptionService.createPrescription(req, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User must not be null");
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Tạo đơn thuốc thất bại khi danh sách drugs rỗng
     * Input: drugs = empty list
     * Expected Output: IllegalArgumentException "at least one drug"
     * Notes: Kiểm tra nhánh drugs == null || drugs.isEmpty()
     */
    @Test
    @DisplayName("TC-FR-12-003: Drugs rỗng → exception")
    void TC_FR_12_003() {
        PrescriptionRequest req = PrescriptionRequest.builder()
                .name("Test").drugs(List.of()).build();

        assertThatThrownBy(() -> prescriptionService.createPrescription(req, user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one drug");
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Tạo đơn thuốc thất bại khi unitId null
     * Input: DrugInPresRequest với unitId = null
     * Expected Output: IllegalArgumentException "Unit ID must not be null"
     * Notes: Kiểm tra nhánh unitId == null
     */
    @Test
    @DisplayName("TC-FR-12-004: UnitId null → exception")
    void TC_FR_12_004() {
        DrugInPresRequest drugReq = buildDrugReq();
        drugReq.setUnitId(null);

        PrescriptionRequest req = PrescriptionRequest.builder()
                .name("Test").drugs(List.of(drugReq)).build();

        when(prescriptionRepository.save(any())).thenAnswer(inv -> {
            Prescription p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });

        assertThatThrownBy(() -> prescriptionService.createPrescription(req, user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unit ID must not be null");
    }

    // ==================== DELETE PRESCRIPTION ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Xóa đơn thuốc thành công
     * Input: id hợp lệ thuộc user
     * Expected Output: Prescription được xóa
     * Notes: CheckDB – prescription + cascade entities bị xóa
     */
    @Test
    @DisplayName("TC-FR-12-005: Xóa thành công")
    void TC_FR_12_005() {
        Prescription prescription = new Prescription();
        prescription.setId(1L);
        prescription.setUser(user);

        when(prescriptionRepository.findByIdAndUser(1L, user)).thenReturn(Optional.of(prescription));

        prescriptionService.deletePrescription(1L, user);

        verify(prescriptionRepository).delete(prescription);
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Xóa đơn thuốc thất bại khi không tìm thấy hoặc không có quyền
     * Input: id không thuộc user
     * Expected Output: RuntimeException
     * Notes: Kiểm tra nhánh findByIdAndUser trả về empty
     */
    @Test
    @DisplayName("TC-FR-12-006: Không tìm thấy → exception")
    void TC_FR_12_006() {
        when(prescriptionRepository.findByIdAndUser(999L, user)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> prescriptionService.deletePrescription(999L, user))
                .isInstanceOf(RuntimeException.class);
    }

    // ==================== TOGGLE PRESCRIPTION STATUS ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Toggle status từ 1 → 0
     * Input: Prescription status = 1
     * Expected Output: Status = 0
     * Notes: CheckDB – status đảo ngược
     */
    @Test
    @DisplayName("TC-FR-12-007: Toggle 1 → 0")
    void TC_FR_12_007() {
        Prescription prescription = new Prescription();
        prescription.setId(1L);
        prescription.setUser(user);
        prescription.setStatus(1);

        when(prescriptionRepository.findById(1L)).thenReturn(Optional.of(prescription));
        when(prescriptionRepository.save(prescription)).thenReturn(prescription);

        Prescription result = prescriptionService.togglePrescriptionStatus(1L, user);

        assertThat(result.getStatus()).isEqualTo(0);
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Toggle status từ 0 → 1
     * Input: Prescription status = 0
     * Expected Output: Status = 1
     * Notes: Kiểm tra nhánh status == 0 → set 1
     */
    @Test
    @DisplayName("TC-FR-12-008: Toggle 0 → 1")
    void TC_FR_12_008() {
        Prescription prescription = new Prescription();
        prescription.setId(1L);
        prescription.setUser(user);
        prescription.setStatus(0);

        when(prescriptionRepository.findById(1L)).thenReturn(Optional.of(prescription));
        when(prescriptionRepository.save(prescription)).thenReturn(prescription);

        Prescription result = prescriptionService.togglePrescriptionStatus(1L, user);

        assertThat(result.getStatus()).isEqualTo(1);
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Toggle thất bại khi user không phải chủ sở hữu
     * Input: User khác
     * Expected Output: RuntimeException "Bạn không có quyền"
     * Notes: Kiểm tra nhánh user.id != prescription.user.id
     */
    @Test
    @DisplayName("TC-FR-12-009: Không có quyền → exception")
    void TC_FR_12_009() {
        User otherUser = User.builder().id(99L).build();
        Prescription prescription = new Prescription();
        prescription.setId(1L);
        prescription.setUser(user);

        when(prescriptionRepository.findById(1L)).thenReturn(Optional.of(prescription));

        assertThatThrownBy(() -> prescriptionService.togglePrescriptionStatus(1L, otherUser))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Bạn không có quyền");
    }

    // ==================== GET SCHEDULES BY DATE ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Lấy lịch uống thuốc theo ngày quá khứ
     * Input: date = yesterday
     * Expected Output: Map chứa thông báo ngày trong quá khứ
     * Notes: Kiểm tra nhánh date.isBefore(today)
     */
    @Test
    @DisplayName("TC-FR-12-010: Ngày quá khứ → thông báo")
    void TC_FR_12_010() {
        LocalDate yesterday = LocalDate.now().minusDays(1);

        Object result = prescriptionService.getSchedulesByDate(yesterday, user);

        assertThat(result).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, String> map = (Map<String, String>) result;
        assertThat(map.get("message")).contains("quá khứ");
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Lấy lịch uống thuốc cho ngày hôm nay
     * Input: date = today
     * Expected Output: List<ScheduleResponseDTO> (có thể rỗng)
     * Notes: Happy path – ngày hợp lệ
     */
    @Test
    @DisplayName("TC-FR-12-011: Ngày hôm nay → danh sách")
    void TC_FR_12_011() {
        LocalDate today = LocalDate.now();
        when(scheduleRepository.findByDateBetween(any(), any())).thenReturn(List.of());

        Object result = prescriptionService.getSchedulesByDate(today, user);

        assertThat(result).isInstanceOf(List.class);
    }

    // ==================== UPDATE SCHEDULE STATUS ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Cập nhật trạng thái schedule = 0 (không uống)
     * Input: status = 0
     * Expected Output: Schedule.status = 0, editted = true
     * Notes: CheckDB – schedule cập nhật
     */
    @Test
    @DisplayName("TC-FR-12-012: Đánh dấu không uống")
    void TC_FR_12_012() {
        Prescription prescription = new Prescription();
        prescription.setUser(user);

        DrugInPrescription dip = new DrugInPrescription();
        dip.setPrescription(prescription);

        Schedule schedule = Schedule.builder()
                .id(1L).drugInPrescription(dip)
                .date(LocalDateTime.now()).status(0).editted(false).build();

        UpdateScheduleStatusRequest req = new UpdateScheduleStatusRequest();
        req.setScheduleId(1L);
        req.setStatus(0);

        when(scheduleRepository.findById(1L)).thenReturn(Optional.of(schedule));

        Object result = prescriptionService.updateScheduleStatus(req, user);

        assertThat(schedule.isEditted()).isTrue();
        assertThat(schedule.getStatus()).isEqualTo(0);
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Cập nhật trạng thái schedule = 1 (uống đúng giờ)
     * Input: status = 1, thời gian schedule chưa quá 10 phút
     * Expected Output: Schedule.status = 1
     * Notes: Kiểm tra nhánh status == 1, chưa trễ
     */
    @Test
    @DisplayName("TC-FR-12-013: Uống đúng giờ → status 1")
    void TC_FR_12_013() {
        Prescription prescription = new Prescription();
        prescription.setUser(user);

        DrugInPrescription dip = new DrugInPrescription();
        dip.setPrescription(prescription);

        Schedule schedule = Schedule.builder()
                .id(1L).drugInPrescription(dip)
                .date(LocalDateTime.now().plusMinutes(5))
                .status(0).editted(false).build();

        UpdateScheduleStatusRequest req = new UpdateScheduleStatusRequest();
        req.setScheduleId(1L);
        req.setStatus(1);

        when(scheduleRepository.findById(1L)).thenReturn(Optional.of(schedule));

        prescriptionService.updateScheduleStatus(req, user);

        assertThat(schedule.getStatus()).isEqualTo(1);
    }

    // ==================== CREATE SINGLE DRUG ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Tạo thuốc đơn lẻ (không thuộc đơn thuốc) thành công
     * Input: DrugInPresRequest hợp lệ, user hợp lệ
     * Expected Output: DrugInPrescription với prescription = null
     * Notes: CheckDB – drugInPrescription không gắn prescription
     */
    @Test
    @DisplayName("TC-FR-12-020: Tạo thuốc đơn thành công")
    void TC_FR_12_020() {
        DrugInPresRequest req = buildDrugReq();

        when(unitRepository.findById(1L)).thenReturn(Optional.of(unit));
        when(drugInPrescriptionRepository.save(any())).thenAnswer(inv -> {
            DrugInPrescription d = inv.getArgument(0);
            d.setId(1L);
            return d;
        });

        DrugInPrescription result = prescriptionService.createSingleDrug(req, user);

        assertThat(result).isNotNull();
        assertThat(result.getPrescription()).isNull();
        assertThat(result.getUser()).isEqualTo(user);
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Tạo thuốc đơn thất bại khi user null
     * Input: user = null
     * Expected Output: IllegalArgumentException
     * Notes: Kiểm tra nhánh user == null
     */
    @Test
    @DisplayName("TC-FR-12-026: User null → exception")
    void TC_FR_12_026() {
        assertThatThrownBy(() -> prescriptionService.createSingleDrug(buildDrugReq(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Tạo thuốc đơn thất bại khi startDate null
     * Input: startDate = null
     * Expected Output: IllegalArgumentException "Start date must not be null"
     * Notes: Kiểm tra nhánh startDate == null
     */
    @Test
    @DisplayName("TC-FR-12-027: StartDate null → exception")
    void TC_FR_12_027() {
        DrugInPresRequest req = buildDrugReq();
        req.setStartDate(null);

        when(unitRepository.findById(1L)).thenReturn(Optional.of(unit));

        assertThatThrownBy(() -> prescriptionService.createSingleDrug(req, user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Start date must not be null");
    }
}

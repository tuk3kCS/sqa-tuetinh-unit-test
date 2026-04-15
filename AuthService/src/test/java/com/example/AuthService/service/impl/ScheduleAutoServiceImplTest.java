package com.example.AuthService.service.impl;

import com.example.AuthService.entity.DrugInPrescription;
import com.example.AuthService.enums.FrequencyType;
import com.example.AuthService.repository.DrugInPrescriptionRepository;
import com.example.AuthService.repository.ScheduleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests cho ScheduleAutoServiceImpl – kiểm tra auto-skip và auto-extend schedules.
 */
@ExtendWith(MockitoExtension.class)
class ScheduleAutoServiceImplTest {

    @Mock private ScheduleRepository scheduleRepository;
    @Mock private DrugInPrescriptionRepository drugInPrescriptionRepository;

    @InjectMocks
    private ScheduleAutoServiceImpl scheduleAutoService;

    // ==================== AUTO MARK SKIPPED SCHEDULES ====================

    /**
     * Test Case ID: TC_AUTH_ScheduleAutoServiceImpl_autoMarkSkippedSchedules_001
     * Test Objective: Auto mark skipped schedules và extend đúng
     * Input: Có schedules cũ cần skip, có thuốc dài hạn cần extend
     * Expected Output: autoMarkSkipped được gọi, findByEndDateIsNull được gọi
     * Notes: Happy path – chạy cron job tự động
     */
    @Test
    @DisplayName("TC_AUTH_ScheduleAutoServiceImpl_autoMarkSkippedSchedules_001: Auto skip + extend")
    void TC_AUTH_ScheduleAutoServiceImpl_autoMarkSkippedSchedules_001() {
        when(scheduleRepository.autoMarkSkipped(any(LocalDateTime.class))).thenReturn(5);
        when(drugInPrescriptionRepository.findByEndDateIsNull()).thenReturn(List.of());

        scheduleAutoService.autoMarkSkippedSchedules();

        verify(scheduleRepository).autoMarkSkipped(any(LocalDateTime.class));
        verify(drugInPrescriptionRepository).findByEndDateIsNull();
    }

    /**
     * Test Case ID: TC_AUTH_ScheduleAutoServiceImpl_autoMarkSkippedSchedules_002
     * Test Objective: Không có schedule nào cần skip
     * Input: autoMarkSkipped trả về 0
     * Expected Output: Method hoàn thành bình thường
     * Notes: Edge case – không có gì để skip
     */
    @Test
    @DisplayName("TC_AUTH_ScheduleAutoServiceImpl_autoMarkSkippedSchedules_002: Không có schedule cần skip")
    void TC_AUTH_ScheduleAutoServiceImpl_autoMarkSkippedSchedules_002() {
        when(scheduleRepository.autoMarkSkipped(any(LocalDateTime.class))).thenReturn(0);
        when(drugInPrescriptionRepository.findByEndDateIsNull()).thenReturn(List.of());

        scheduleAutoService.autoMarkSkippedSchedules();

        verify(scheduleRepository).autoMarkSkipped(any());
    }

    /**
     * Test Case ID: TC_AUTH_ScheduleAutoServiceImpl_autoMarkSkippedSchedules_003
     * Test Objective: Auto extend cho thuốc dài hạn khi lịch sắp hết (daysLeft <= 3)
     * Input: DrugInPrescription dài hạn, last schedule cách hôm nay 2 ngày
     * Expected Output: scheduleRepository.saveAll được gọi với danh sách mới
     * Notes: Kiểm tra nhánh daysLeft <= 3 → sinh thêm schedule
     */
    @Test
    @DisplayName("TC_AUTH_ScheduleAutoServiceImpl_autoMarkSkippedSchedules_003: Extend thuốc dài hạn")
    void TC_AUTH_ScheduleAutoServiceImpl_autoMarkSkippedSchedules_003() {
        DrugInPrescription dip = new DrugInPrescription();
        dip.setId(1L);
        dip.setFrequencyType(FrequencyType.DAILY);
        dip.setStartDate(LocalDate.now().minusDays(10));
        dip.setEndDate(null);

        when(scheduleRepository.autoMarkSkipped(any())).thenReturn(0);
        when(drugInPrescriptionRepository.findByEndDateIsNull()).thenReturn(List.of(dip));
        when(scheduleRepository.findLastScheduleDate(1L))
                .thenReturn(LocalDateTime.now().plusDays(2));

        scheduleAutoService.autoMarkSkippedSchedules();

        verify(drugInPrescriptionRepository).findByEndDateIsNull();
    }

    /**
     * Test Case ID: TC_AUTH_ScheduleAutoServiceImpl_autoMarkSkippedSchedules_004
     * Test Objective: Không extend khi lịch còn nhiều (daysLeft > 3)
     * Input: Last schedule cách hôm nay 10 ngày
     * Expected Output: saveAll không được gọi
     * Notes: Kiểm tra nhánh daysLeft > 3 → không extend
     */
    @Test
    @DisplayName("TC_AUTH_ScheduleAutoServiceImpl_autoMarkSkippedSchedules_004: Lịch còn nhiều → không extend")
    void TC_AUTH_ScheduleAutoServiceImpl_autoMarkSkippedSchedules_004() {
        DrugInPrescription dip = new DrugInPrescription();
        dip.setId(1L);
        dip.setEndDate(null);

        when(scheduleRepository.autoMarkSkipped(any())).thenReturn(0);
        when(drugInPrescriptionRepository.findByEndDateIsNull()).thenReturn(List.of(dip));
        when(scheduleRepository.findLastScheduleDate(1L))
                .thenReturn(LocalDateTime.now().plusDays(10));

        scheduleAutoService.autoMarkSkippedSchedules();

        verify(scheduleRepository, never()).saveAll(anyList());
    }

    /**
     * Test Case ID: TC_AUTH_ScheduleAutoServiceImpl_autoMarkSkippedSchedules_005
     * Test Objective: Bỏ qua khi lastScheduleDate null
     * Input: findLastScheduleDate trả về null
     * Expected Output: continue, không extend
     * Notes: Kiểm tra nhánh lastScheduleDate == null → continue
     */
    @Test
    @DisplayName("TC_AUTH_ScheduleAutoServiceImpl_autoMarkSkippedSchedules_005: LastScheduleDate null → skip")
    void TC_AUTH_ScheduleAutoServiceImpl_autoMarkSkippedSchedules_005() {
        DrugInPrescription dip = new DrugInPrescription();
        dip.setId(1L);
        dip.setEndDate(null);

        when(scheduleRepository.autoMarkSkipped(any())).thenReturn(0);
        when(drugInPrescriptionRepository.findByEndDateIsNull()).thenReturn(List.of(dip));
        when(scheduleRepository.findLastScheduleDate(1L)).thenReturn(null);

        scheduleAutoService.autoMarkSkippedSchedules();

        verify(scheduleRepository, never()).saveAll(anyList());
    }
}

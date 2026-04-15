package com.example.AuthService.startup;

import com.example.AuthService.entity.Role;
import com.example.AuthService.repository.RoleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests cho {@link RoleSeeder}.
 * Kiểm tra logic seed các role mặc định (USER, MODERATOR, ADMIN).
 */
@ExtendWith(MockitoExtension.class)
class RoleSeederTest {

    @Mock
    private RoleRepository roleRepo;

    @InjectMocks
    private RoleSeeder roleSeeder;

    // ======================== RUN ========================

    /**
     * Test Case ID: TC_AUTH_RoleSeeder_run_001
     * Test Objective: Tạo tất cả 3 roles khi chưa có role nào trong DB
     * Input: DB rỗng (không có role nào)
     * Expected Output: roleRepo.save() được gọi 3 lần (USER, MODERATOR, ADMIN)
     * Notes: Happy path - khởi tạo lần đầu
     */
    @Test
    @DisplayName("TC_AUTH_RoleSeeder_run_001: Tạo tất cả roles khi DB rỗng")
    void TC_AUTH_RoleSeeder_run_001() throws Exception {
        when(roleRepo.findByName("USER")).thenReturn(Optional.empty());
        when(roleRepo.findByName("MODERATOR")).thenReturn(Optional.empty());
        when(roleRepo.findByName("ADMIN")).thenReturn(Optional.empty());

        when(roleRepo.save(any(Role.class))).thenReturn(Role.builder().id(1L).name("ROLE").build());

        roleSeeder.run();

        verify(roleRepo, times(3)).save(any(Role.class));
    }

    /**
     * Test Case ID: TC_AUTH_RoleSeeder_run_002
     * Test Objective: Không tạo role khi tất cả đã tồn tại
     * Input: DB đã có USER, MODERATOR, ADMIN
     * Expected Output: roleRepo.save() KHÔNG được gọi
     * Notes: Idempotent - chạy lại không tạo trùng
     */
    @Test
    @DisplayName("TC_AUTH_RoleSeeder_run_002: Không tạo role khi đã tồn tại")
    void TC_AUTH_RoleSeeder_run_002() throws Exception {
        when(roleRepo.findByName("USER"))
                .thenReturn(Optional.of(Role.builder().id(1L).name("USER").build()));
        when(roleRepo.findByName("MODERATOR"))
                .thenReturn(Optional.of(Role.builder().id(2L).name("MODERATOR").build()));
        when(roleRepo.findByName("ADMIN"))
                .thenReturn(Optional.of(Role.builder().id(3L).name("ADMIN").build()));

        roleSeeder.run();

        verify(roleRepo, never()).save(any(Role.class));
    }

    /**
     * Test Case ID: TC_AUTH_RoleSeeder_run_003
     * Test Objective: Chỉ tạo role còn thiếu (partial)
     * Input: DB đã có USER, thiếu MODERATOR và ADMIN
     * Expected Output: roleRepo.save() được gọi 2 lần
     * Notes: Chỉ bổ sung role chưa có
     */
    @Test
    @DisplayName("TC_AUTH_RoleSeeder_run_003: Chỉ tạo role còn thiếu")
    void TC_AUTH_RoleSeeder_run_003() throws Exception {
        when(roleRepo.findByName("USER"))
                .thenReturn(Optional.of(Role.builder().id(1L).name("USER").build()));
        when(roleRepo.findByName("MODERATOR")).thenReturn(Optional.empty());
        when(roleRepo.findByName("ADMIN")).thenReturn(Optional.empty());

        when(roleRepo.save(any(Role.class)))
                .thenReturn(Role.builder().id(2L).name("ROLE").build());

        roleSeeder.run();

        verify(roleRepo, times(2)).save(any(Role.class));
    }

    /**
     * Test Case ID: TC_AUTH_RoleSeeder_run_004
     * Test Objective: Kiểm tra đúng 3 role names được kiểm tra
     * Input: DB rỗng
     * Expected Output: findByName gọi với "USER", "MODERATOR", "ADMIN"
     * Notes: Kiểm tra tên role đúng
     */
    @Test
    @DisplayName("TC_AUTH_RoleSeeder_run_004: Kiểm tra đúng 3 role names")
    void TC_AUTH_RoleSeeder_run_004() throws Exception {
        when(roleRepo.findByName(anyString())).thenReturn(Optional.empty());
        when(roleRepo.save(any(Role.class)))
                .thenReturn(Role.builder().id(1L).name("ROLE").build());

        roleSeeder.run();

        verify(roleRepo).findByName("USER");
        verify(roleRepo).findByName("MODERATOR");
        verify(roleRepo).findByName("ADMIN");
    }

    /**
     * Test Case ID: TC_AUTH_RoleSeeder_run_005
     * Test Objective: Chỉ thiếu ADMIN role
     * Input: DB có USER và MODERATOR, thiếu ADMIN
     * Expected Output: roleRepo.save() được gọi 1 lần
     * Notes: Partial seeding - chỉ thiếu 1 role
     */
    @Test
    @DisplayName("TC_AUTH_RoleSeeder_run_005: Chỉ thiếu ADMIN role")
    void TC_AUTH_RoleSeeder_run_005() throws Exception {
        when(roleRepo.findByName("USER"))
                .thenReturn(Optional.of(Role.builder().id(1L).name("USER").build()));
        when(roleRepo.findByName("MODERATOR"))
                .thenReturn(Optional.of(Role.builder().id(2L).name("MODERATOR").build()));
        when(roleRepo.findByName("ADMIN")).thenReturn(Optional.empty());

        when(roleRepo.save(any(Role.class)))
                .thenReturn(Role.builder().id(3L).name("ADMIN").build());

        roleSeeder.run();

        verify(roleRepo, times(1)).save(any(Role.class));
    }
}

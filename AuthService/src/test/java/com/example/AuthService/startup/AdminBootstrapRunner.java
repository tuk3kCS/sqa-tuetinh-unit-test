package com.example.AuthService.startup;

import com.example.AuthService.entity.Role;
import com.example.AuthService.entity.User;
import com.example.AuthService.repository.RoleRepository;
import com.example.AuthService.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Tạo user ADMIN lần đầu khi bật {@code app.admin.bootstrap.enabled} và có mật khẩu.
 * Chạy sau {@link RoleSeeder}. Nếu email đã tồn tại thì bỏ qua.
 */
@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrapRunner implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.bootstrap.enabled:false}")
    private boolean enabled;

    @Value("${app.admin.bootstrap.email:admin@localhost.local}")
    private String email;

    @Value("${app.admin.bootstrap.password:}")
    private String rawPassword;

    @Value("${app.admin.bootstrap.name:Administrator}")
    private String displayName;

    @Override
    public void run(String... args) {
        if (!enabled) {
            return;
        }
        if (rawPassword == null || rawPassword.isBlank()) {
            log.warn("Admin bootstrap skipped: app.admin.bootstrap.enabled=true but app.admin.bootstrap.password is empty");
            return;
        }

        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(normalized)) {
            log.info("Admin bootstrap skipped: user {} already exists", normalized);
            return;
        }

        Role adminRole = roleRepository.findByName("ADMIN")
                .orElseThrow(() -> new IllegalStateException("ADMIN role missing; ensure RoleSeeder ran first"));

        User admin = User.builder()
                .email(normalized)
                .name(displayName)
                .password(passwordEncoder.encode(rawPassword))
                .role(adminRole)
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build();

        userRepository.save(admin);
        log.info("Bootstrap admin user created: {} (disable app.admin.bootstrap.enabled after first deploy)", normalized);
    }
}

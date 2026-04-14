package com.example.AuthService.service.impl;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests cho CloudinaryServiceImpl – kiểm tra upload ảnh lên Cloudinary.
 */
@ExtendWith(MockitoExtension.class)
class CloudinaryServiceImplTest {

    @Mock private Cloudinary cloudinary;
    @Mock private Uploader uploader;

    @InjectMocks
    private CloudinaryServiceImpl cloudinaryService;

    // ==================== UPLOAD IMAGE ====================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Upload ảnh thành công, trả về secure_url
     * Input: MultipartFile hợp lệ
     * Expected Output: URL ảnh từ Cloudinary
     * Notes: Happy path – upload và lấy secure_url
     */
    @Test
    @DisplayName("TC-FR-02-001: Upload thành công")
    void TC_FR_02_001() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getBytes()).thenReturn(new byte[]{1, 2, 3});

        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap()))
                .thenReturn(Map.of("secure_url", "https://res.cloudinary.com/test/img.jpg"));

        String result = cloudinaryService.uploadImage(file);

        assertThat(result).isEqualTo("https://res.cloudinary.com/test/img.jpg");
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Upload thất bại khi IOException xảy ra
     * Input: file.getBytes() throw IOException
     * Expected Output: RuntimeException "Upload image failed"
     * Notes: Kiểm tra nhánh catch IOException
     */
    @Test
    @DisplayName("TC-FR-02-001: IOException → RuntimeException")
    void TC_FR_02_001() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getBytes()).thenThrow(new IOException("Disk error"));

        assertThatThrownBy(() -> cloudinaryService.uploadImage(file))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Upload image failed");
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Upload thất bại khi Cloudinary API trả lỗi
     * Input: uploader.upload() throw IOException
     * Expected Output: RuntimeException "Upload image failed"
     * Notes: Kiểm tra Cloudinary API lỗi
     */
    @Test
    @DisplayName("TC-FR-02-001: Cloudinary API lỗi → RuntimeException")
    void TC_FR_02_001() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getBytes()).thenReturn(new byte[]{1});

        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap()))
                .thenThrow(new IOException("API error"));

        assertThatThrownBy(() -> cloudinaryService.uploadImage(file))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Upload image failed");
    }
}

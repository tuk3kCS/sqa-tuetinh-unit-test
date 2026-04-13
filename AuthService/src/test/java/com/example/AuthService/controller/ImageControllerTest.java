package com.example.AuthService.controller;

import com.example.AuthService.security.jwt.JwtService;
import com.example.AuthService.security.OAuth2LoginSuccessHandler;
import com.example.AuthService.service.CloudinaryService;
import org.springframework.security.core.userdetails.UserDetailsService;
import com.example.AuthService.service.SocialLoginService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests cho {@link ImageController}.
 * Kiểm tra endpoint upload hình ảnh lên Cloudinary.
 */
@WebMvcTest(ImageController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class ImageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CloudinaryService cloudinaryService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private SocialLoginService socialLoginService;

    @MockitoBean
    private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    @MockitoBean
    private UserDetailsService userDetailsService;

    // ======================== UPLOAD IMAGE ========================

    /**
     * Test Case ID: TC_AUTH_ImageController_uploadImage_001
     * Test Objective: Upload hình ảnh thành công
     * Input: MultipartFile ảnh JPEG hợp lệ
     * Expected Output: HTTP 200, body chứa URL ảnh
     * Notes: Happy path - upload ảnh lên Cloudinary
     */
    @Test
    @DisplayName("TC_AUTH_ImageController_uploadImage_001: Upload ảnh thành công")
    void TC_AUTH_ImageController_uploadImage_001() throws Exception {
        when(cloudinaryService.uploadImage(any()))
                .thenReturn("https://res.cloudinary.com/test/image.jpg");

        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "fake-image-data".getBytes());

        mockMvc.perform(multipart("/api/upload/image").file(file))
                .andExpect(status().isOk())
                .andExpect(content().string("https://res.cloudinary.com/test/image.jpg"));
    }

    /**
     * Test Case ID: TC_AUTH_ImageController_uploadImage_002
     * Test Objective: Upload ảnh khi Cloudinary lỗi
     * Input: MultipartFile ảnh hợp lệ nhưng Cloudinary không khả dụng
     * Expected Output: HTTP 500
     * Notes: Cloudinary service ném RuntimeException
     */
    @Test
    @DisplayName("TC_AUTH_ImageController_uploadImage_002: Upload ảnh - Cloudinary lỗi")
    void TC_AUTH_ImageController_uploadImage_002() throws Exception {
        when(cloudinaryService.uploadImage(any()))
                .thenThrow(new RuntimeException("Cloudinary upload failed"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "fake-image-data".getBytes());

        mockMvc.perform(multipart("/api/upload/image").file(file))
                .andExpect(status().isInternalServerError());
    }

    /**
     * Test Case ID: TC_AUTH_ImageController_uploadImage_003
     * Test Objective: Upload file PNG
     * Input: MultipartFile ảnh PNG
     * Expected Output: HTTP 200, URL ảnh
     * Notes: Kiểm tra hỗ trợ nhiều định dạng
     */
    @Test
    @DisplayName("TC_AUTH_ImageController_uploadImage_003: Upload ảnh PNG thành công")
    void TC_AUTH_ImageController_uploadImage_003() throws Exception {
        when(cloudinaryService.uploadImage(any()))
                .thenReturn("https://res.cloudinary.com/test/photo.png");

        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.png", "image/png", "fake-png-data".getBytes());

        mockMvc.perform(multipart("/api/upload/image").file(file))
                .andExpect(status().isOk())
                .andExpect(content().string("https://res.cloudinary.com/test/photo.png"));
    }

    /**
     * Test Case ID: TC_AUTH_ImageController_uploadImage_004
     * Test Objective: Upload file rỗng
     * Input: MultipartFile rỗng (0 bytes)
     * Expected Output: HTTP 500 hoặc xử lý từ service
     * Notes: Edge case - file không có nội dung
     */
    @Test
    @DisplayName("TC_AUTH_ImageController_uploadImage_004: Upload file rỗng")
    void TC_AUTH_ImageController_uploadImage_004() throws Exception {
        when(cloudinaryService.uploadImage(any()))
                .thenThrow(new RuntimeException("File rỗng"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.jpg", "image/jpeg", new byte[0]);

        mockMvc.perform(multipart("/api/upload/image").file(file))
                .andExpect(status().isInternalServerError());
    }
}

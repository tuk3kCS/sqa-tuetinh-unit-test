package com.example.AuthService.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests cho {@link GlobalExceptionHandler}.
 * Kiểm tra xử lý exception toàn cục.
 */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @InjectMocks
    private GlobalExceptionHandler handler;

    // ======================== HANDLE RESPONSE STATUS EXCEPTION ========================

    /**
     * Test Case ID: TC_AUTH_GlobalExceptionHandler_handleResponseStatusException_001
     * Test Objective: Xử lý ResponseStatusException 400 Bad Request
     * Input: ResponseStatusException(400, "Dữ liệu không hợp lệ")
     * Expected Output: HTTP 400, body chứa success=false, code=400, message
     * Notes: Client gửi dữ liệu sai
     */
    @Test
    @DisplayName("TC_AUTH_GlobalExceptionHandler_handleResponseStatusException_001: 400 Bad Request")
    void TC_AUTH_GlobalExceptionHandler_handleResponseStatusException_001() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ");

        ResponseEntity<Map<String, Object>> response = handler.handleResponseStatusException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertFalse((Boolean) response.getBody().get("success"));
        assertEquals(400, response.getBody().get("code"));
        assertEquals("Dữ liệu không hợp lệ", response.getBody().get("message"));
    }

    /**
     * Test Case ID: TC_AUTH_GlobalExceptionHandler_handleResponseStatusException_002
     * Test Objective: Xử lý ResponseStatusException 401 Unauthorized
     * Input: ResponseStatusException(401, "Chưa đăng nhập")
     * Expected Output: HTTP 401, body chứa success=false, code=401
     * Notes: User chưa xác thực
     */
    @Test
    @DisplayName("TC_AUTH_GlobalExceptionHandler_handleResponseStatusException_002: 401 Unauthorized")
    void TC_AUTH_GlobalExceptionHandler_handleResponseStatusException_002() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập");

        ResponseEntity<Map<String, Object>> response = handler.handleResponseStatusException(ex);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(401, response.getBody().get("code"));
        assertFalse((Boolean) response.getBody().get("success"));
    }

    /**
     * Test Case ID: TC_AUTH_GlobalExceptionHandler_handleResponseStatusException_003
     * Test Objective: Xử lý ResponseStatusException 404 Not Found
     * Input: ResponseStatusException(404, "Không tìm thấy")
     * Expected Output: HTTP 404, body chứa success=false, code=404
     * Notes: Resource không tồn tại
     */
    @Test
    @DisplayName("TC_AUTH_GlobalExceptionHandler_handleResponseStatusException_003: 404 Not Found")
    void TC_AUTH_GlobalExceptionHandler_handleResponseStatusException_003() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy");

        ResponseEntity<Map<String, Object>> response = handler.handleResponseStatusException(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(404, response.getBody().get("code"));
        assertEquals("Không tìm thấy", response.getBody().get("message"));
    }

    /**
     * Test Case ID: TC_AUTH_GlobalExceptionHandler_handleResponseStatusException_004
     * Test Objective: Xử lý ResponseStatusException 500 Internal Server Error
     * Input: ResponseStatusException(500, "Lỗi hệ thống")
     * Expected Output: HTTP 500, body chứa success=false, code=500
     * Notes: Lỗi server nội bộ
     */
    @Test
    @DisplayName("TC_AUTH_GlobalExceptionHandler_handleResponseStatusException_004: 500 Internal Server Error")
    void TC_AUTH_GlobalExceptionHandler_handleResponseStatusException_004() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi hệ thống");

        ResponseEntity<Map<String, Object>> response = handler.handleResponseStatusException(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(500, response.getBody().get("code"));
    }

    /**
     * Test Case ID: TC_AUTH_GlobalExceptionHandler_handleResponseStatusException_005
     * Test Objective: Response body chứa debug info
     * Input: ResponseStatusException(400, "Test")
     * Expected Output: Body chứa key "debug" với cấu trúc đúng
     * Notes: Kiểm tra cấu trúc debug object
     */
    @Test
    @DisplayName("TC_AUTH_GlobalExceptionHandler_handleResponseStatusException_005: Chứa debug info")
    void TC_AUTH_GlobalExceptionHandler_handleResponseStatusException_005() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.BAD_REQUEST, "Test");

        ResponseEntity<Map<String, Object>> response = handler.handleResponseStatusException(ex);

        assertNotNull(response.getBody().get("debug"));
        Map<String, Object> debug = (Map<String, Object>) response.getBody().get("debug");
        assertNotNull(debug.get("headers"));
        assertNotNull(debug.get("original"));
    }

    // ======================== HANDLE GENERIC EXCEPTION ========================

    /**
     * Test Case ID: TC_AUTH_GlobalExceptionHandler_handleGenericException_001
     * Test Objective: Xử lý Exception chung (RuntimeException)
     * Input: RuntimeException("Lỗi bất ngờ")
     * Expected Output: HTTP 500, message="Internal Server Error"
     * Notes: Exception không mong đợi → 500
     */
    @Test
    @DisplayName("TC_AUTH_GlobalExceptionHandler_handleGenericException_001: RuntimeException → 500")
    void TC_AUTH_GlobalExceptionHandler_handleGenericException_001() {
        RuntimeException ex = new RuntimeException("Lỗi bất ngờ");

        ResponseEntity<Map<String, Object>> response = handler.handleGenericException(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertFalse((Boolean) response.getBody().get("success"));
        assertEquals(500, response.getBody().get("code"));
        assertEquals("Internal Server Error", response.getBody().get("message"));
    }

    /**
     * Test Case ID: TC_AUTH_GlobalExceptionHandler_handleGenericException_002
     * Test Objective: Xử lý NullPointerException
     * Input: NullPointerException
     * Expected Output: HTTP 500, message="Internal Server Error"
     * Notes: NPE → 500
     */
    @Test
    @DisplayName("TC_AUTH_GlobalExceptionHandler_handleGenericException_002: NullPointerException → 500")
    void TC_AUTH_GlobalExceptionHandler_handleGenericException_002() {
        NullPointerException ex = new NullPointerException("null value");

        ResponseEntity<Map<String, Object>> response = handler.handleGenericException(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Internal Server Error", response.getBody().get("message"));
    }

    /**
     * Test Case ID: TC_AUTH_GlobalExceptionHandler_handleGenericException_003
     * Test Objective: Response chứa debug info với stack trace
     * Input: RuntimeException("Test error")
     * Expected Output: debug.original chứa exception class name và message
     * Notes: Kiểm tra debug info chi tiết
     */
    @Test
    @DisplayName("TC_AUTH_GlobalExceptionHandler_handleGenericException_003: Debug info chứa stack trace")
    void TC_AUTH_GlobalExceptionHandler_handleGenericException_003() {
        RuntimeException ex = new RuntimeException("Test error");

        ResponseEntity<Map<String, Object>> response = handler.handleGenericException(ex);

        Map<String, Object> debug = (Map<String, Object>) response.getBody().get("debug");
        Map<String, Object> original = (Map<String, Object>) debug.get("original");

        assertEquals("Test error", original.get("message"));
        assertEquals("java.lang.RuntimeException", original.get("exception"));
        assertNotNull(original.get("trace"));
    }

    /**
     * Test Case ID: TC_AUTH_GlobalExceptionHandler_handleGenericException_004
     * Test Objective: Xử lý IllegalArgumentException
     * Input: IllegalArgumentException("Invalid argument")
     * Expected Output: HTTP 500
     * Notes: Mọi Exception đều trả 500
     */
    @Test
    @DisplayName("TC_AUTH_GlobalExceptionHandler_handleGenericException_004: IllegalArgumentException → 500")
    void TC_AUTH_GlobalExceptionHandler_handleGenericException_004() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid argument");

        ResponseEntity<Map<String, Object>> response = handler.handleGenericException(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertFalse((Boolean) response.getBody().get("success"));
    }

    /**
     * Test Case ID: TC_AUTH_GlobalExceptionHandler_handleGenericException_005
     * Test Objective: Xử lý Exception với message null
     * Input: RuntimeException(null)
     * Expected Output: HTTP 500, debug.original.message = null
     * Notes: Edge case - exception không có message
     */
    @Test
    @DisplayName("TC_AUTH_GlobalExceptionHandler_handleGenericException_005: Exception với message null")
    void TC_AUTH_GlobalExceptionHandler_handleGenericException_005() {
        RuntimeException ex = new RuntimeException((String) null);

        ResponseEntity<Map<String, Object>> response = handler.handleGenericException(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Internal Server Error", response.getBody().get("message"));
    }
}

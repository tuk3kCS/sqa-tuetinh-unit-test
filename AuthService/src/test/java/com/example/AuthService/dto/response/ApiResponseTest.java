package com.example.AuthService.dto.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests cho {@link ApiResponse}.
 * Kiểm tra các factory methods và cấu trúc response.
 */
class ApiResponseTest {

    // ======================== SUCCESS (STRING) ========================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Tạo ApiResponse thành công chỉ với message
     * Input: message="Thao tác thành công"
     * Expected Output: success=true, message="Thao tác thành công", data=null
     * Notes: Factory method success(String)
     */
    @Test
    @DisplayName("TC-FR-00-001: success(message) - tạo thành công")
    void TC_FR_00_001() {
        ApiResponse response = ApiResponse.success("Thao tác thành công");

        assertTrue(response.isSuccess());
        assertEquals("Thao tác thành công", response.getMessage());
        assertNull(response.getData());
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Tạo ApiResponse thành công với message rỗng
     * Input: message=""
     * Expected Output: success=true, message="", data=null
     * Notes: Edge case - message rỗng
     */
    @Test
    @DisplayName("TC-FR-00-002: success với message rỗng")
    void TC_FR_00_002() {
        ApiResponse response = ApiResponse.success("");

        assertTrue(response.isSuccess());
        assertEquals("", response.getMessage());
        assertNull(response.getData());
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Tạo ApiResponse thành công với message null
     * Input: message=null
     * Expected Output: success=true, message=null, data=null
     * Notes: Edge case - message null
     */
    @Test
    @DisplayName("TC-FR-00-005: success với message null")
    void TC_FR_00_005() {
        ApiResponse response = ApiResponse.success(null);

        assertTrue(response.isSuccess());
        assertNull(response.getMessage());
    }

    // ======================== SUCCESS (STRING, OBJECT) ========================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Tạo ApiResponse thành công với message và data
     * Input: message="Lấy dữ liệu thành công", data=Map{"key":"value"}
     * Expected Output: success=true, message đúng, data không null
     * Notes: Factory method success(String, Object)
     */
    @Test
    @DisplayName("TC-FR-00-006: success(message, data) - tạo thành công")
    void TC_FR_00_006() {
        Map<String, String> data = Map.of("key", "value");
        ApiResponse response = ApiResponse.success("Lấy dữ liệu thành công", data);

        assertTrue(response.isSuccess());
        assertEquals("Lấy dữ liệu thành công", response.getMessage());
        assertNotNull(response.getData());
        assertEquals(data, response.getData());
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Tạo ApiResponse thành công với data là List
     * Input: message="OK", data=List.of("a", "b")
     * Expected Output: success=true, data là List
     * Notes: Data có thể là bất kỳ Object nào
     */
    @Test
    @DisplayName("TC-FR-00-007: success với data là List")
    void TC_FR_00_007() {
        List<String> data = List.of("item1", "item2");
        ApiResponse response = ApiResponse.success("OK", data);

        assertTrue(response.isSuccess());
        assertEquals(data, response.getData());
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Tạo ApiResponse thành công với data null
     * Input: message="OK", data=null
     * Expected Output: success=true, data=null
     * Notes: Data có thể null
     */
    @Test
    @DisplayName("TC-FR-00-008: success với data null")
    void TC_FR_00_008() {
        ApiResponse response = ApiResponse.success("OK", null);

        assertTrue(response.isSuccess());
        assertNull(response.getData());
    }

    // ======================== ERROR (STRING) ========================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Tạo ApiResponse lỗi với message
     * Input: message="Đã xảy ra lỗi"
     * Expected Output: success=false, message="Đã xảy ra lỗi", data=null
     * Notes: Factory method error(String)
     */
    @Test
    @DisplayName("TC-FR-00-009: error(message) - tạo thành công")
    void TC_FR_00_009() {
        ApiResponse response = ApiResponse.error("Đã xảy ra lỗi");

        assertFalse(response.isSuccess());
        assertEquals("Đã xảy ra lỗi", response.getMessage());
        assertNull(response.getData());
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Tạo ApiResponse lỗi với message chi tiết
     * Input: message="Không tìm thấy user ID: 123"
     * Expected Output: success=false, message chứa chi tiết lỗi
     * Notes: Message có thông tin chi tiết
     */
    @Test
    @DisplayName("TC-FR-00-010: error với message chi tiết")
    void TC_FR_00_010() {
        ApiResponse response = ApiResponse.error("Không tìm thấy user ID: 123");

        assertFalse(response.isSuccess());
        assertTrue(response.getMessage().contains("123"));
        assertNull(response.getData());
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Phân biệt giữa success và error response
     * Input: Tạo 1 success và 1 error
     * Expected Output: success.isSuccess()=true, error.isSuccess()=false
     * Notes: Kiểm tra phân biệt rõ ràng
     */
    @Test
    @DisplayName("TC-FR-02-001: Phân biệt success và error")
    void TC_FR_02_001() {
        ApiResponse successResponse = ApiResponse.success("OK");
        ApiResponse errorResponse = ApiResponse.error("Error");

        assertTrue(successResponse.isSuccess());
        assertFalse(errorResponse.isSuccess());
        assertNotEquals(successResponse.isSuccess(), errorResponse.isSuccess());
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Error response luôn có data=null
     * Input: message="Error"
     * Expected Output: data=null
     * Notes: Error không kèm data
     */
    @Test
    @DisplayName("TC-FR-02-001: Error response data luôn null")
    void TC_FR_02_001() {
        ApiResponse response = ApiResponse.error("Error");

        assertNull(response.getData());
    }
}

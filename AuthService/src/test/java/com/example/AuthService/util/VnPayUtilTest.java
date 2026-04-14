package com.example.AuthService.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests cho {@link VnPayUtil}.
 * Kiểm tra các hàm tiện ích xử lý VNPay (hash, query string, HMAC).
 */
class VnPayUtilTest {

    // ======================== BUILD HASH DATA ========================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Tạo hash data từ params đã sắp xếp và URL encode
     * Input: Map{"vnp_Amount"="1000000", "vnp_Command"="pay"}
     * Expected Output: "vnp_Amount=1000000&vnp_Command=pay"
     * Notes: Happy path - params đã sắp xếp theo key
     */
    @Test
    @DisplayName("TC-FR-02-001: Tạo hash data thành công")
    void TC_FR_02_001() {
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Amount", "1000000");
        params.put("vnp_Command", "pay");

        String result = VnPayUtil.buildHashData(params);

        assertEquals("vnp_Amount=1000000&vnp_Command=pay", result);
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Hash data URL encode ký tự đặc biệt
     * Input: Map{"vnp_OrderInfo"="Thanh toán đơn hàng"}
     * Expected Output: Chuỗi được URL encode
     * Notes: Tiếng Việt và ký tự đặc biệt
     */
    @Test
    @DisplayName("TC-FR-02-001: Hash data URL encode ký tự tiếng Việt")
    void TC_FR_02_001() {
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_OrderInfo", "Thanh toán đơn hàng");

        String result = VnPayUtil.buildHashData(params);

        assertFalse(result.contains(" "), "Khoảng trắng phải được encode");
        assertTrue(result.startsWith("vnp_OrderInfo="));
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Hash data từ map rỗng
     * Input: Map rỗng
     * Expected Output: Chuỗi rỗng ""
     * Notes: Edge case
     */
    @Test
    @DisplayName("TC-FR-02-001: Hash data từ map rỗng")
    void TC_FR_02_001() {
        Map<String, String> params = new TreeMap<>();

        String result = VnPayUtil.buildHashData(params);

        assertEquals("", result);
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Hash data sắp xếp đúng thứ tự alphabet
     * Input: Map{"b"="2", "a"="1", "c"="3"}
     * Expected Output: "a=1&b=2&c=3"
     * Notes: Params phải được sắp xếp theo key
     */
    @Test
    @DisplayName("TC-FR-02-001: Sắp xếp params đúng thứ tự")
    void TC_FR_02_001() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("c", "3");
        params.put("a", "1");
        params.put("b", "2");

        String result = VnPayUtil.buildHashData(params);

        assertEquals("a=1&b=2&c=3", result);
    }

    // ======================== BUILD QUERY STRING ========================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Tạo query string với URL encode cho redirect
     * Input: Map{"vnp_Amount"="1000000", "vnp_TxnRef"="12345"}
     * Expected Output: "vnp_Amount=1000000&vnp_TxnRef=12345"
     * Notes: Happy path
     */
    @Test
    @DisplayName("TC-FR-02-001: Tạo query string thành công")
    void TC_FR_02_001() {
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Amount", "1000000");
        params.put("vnp_TxnRef", "12345");

        String result = VnPayUtil.buildQueryString(params);

        assertTrue(result.contains("vnp_Amount=1000000"));
        assertTrue(result.contains("vnp_TxnRef=12345"));
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Query string encode ký tự đặc biệt
     * Input: Map có value chứa ký tự đặc biệt
     * Expected Output: Ký tự đặc biệt được encode
     * Notes: URL encode cho redirect URL
     */
    @Test
    @DisplayName("TC-FR-02-001: Query string encode ký tự đặc biệt")
    void TC_FR_02_001() {
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_ReturnUrl", "http://localhost:8080/return?a=1&b=2");

        String result = VnPayUtil.buildQueryString(params);

        assertTrue(result.contains("vnp_ReturnUrl="));
        assertFalse(result.contains("http://localhost"), "URL phải được encode");
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Query string với 1 tham số duy nhất
     * Input: Map{"key"="value"}
     * Expected Output: "key=value" (không có &)
     * Notes: Edge case - chỉ 1 param
     */
    @Test
    @DisplayName("TC-FR-02-001: Query string với 1 tham số")
    void TC_FR_02_001() {
        Map<String, String> params = new TreeMap<>();
        params.put("key", "value");

        String result = VnPayUtil.buildQueryString(params);

        assertEquals("key=value", result);
        assertFalse(result.contains("&"));
    }

    // ======================== HMAC SHA512 ========================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Tạo HMAC SHA512 thành công
     * Input: key="secret", data="test-data"
     * Expected Output: Chuỗi hex 128 ký tự (512 bit = 64 bytes = 128 hex chars)
     * Notes: Happy path - kiểm tra format output
     */
    @Test
    @DisplayName("TC-FR-02-001: Tạo HMAC SHA512 thành công")
    void TC_FR_02_001() {
        String result = VnPayUtil.hmacSHA512("secret-key", "test-data");

        assertNotNull(result);
        assertEquals(128, result.length(), "HMAC SHA512 phải là 128 ký tự hex");
        assertTrue(result.matches("[0-9a-f]+"), "Chỉ chứa hex chars");
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: HMAC SHA512 cùng input cho cùng output (deterministic)
     * Input: key="key", data="data" (gọi 2 lần)
     * Expected Output: 2 kết quả giống nhau
     * Notes: Tính deterministic
     */
    @Test
    @DisplayName("TC-FR-02-001: HMAC SHA512 là deterministic")
    void TC_FR_02_001() {
        String result1 = VnPayUtil.hmacSHA512("key", "data");
        String result2 = VnPayUtil.hmacSHA512("key", "data");

        assertEquals(result1, result2);
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: HMAC SHA512 với key khác cho output khác
     * Input: key1="key1", key2="key2", data="same-data"
     * Expected Output: 2 kết quả khác nhau
     * Notes: Key khác → hash khác
     */
    @Test
    @DisplayName("TC-FR-02-001: Key khác cho output khác")
    void TC_FR_02_001() {
        String result1 = VnPayUtil.hmacSHA512("key1", "same-data");
        String result2 = VnPayUtil.hmacSHA512("key2", "same-data");

        assertNotEquals(result1, result2);
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: HMAC SHA512 với data rỗng
     * Input: key="secret", data=""
     * Expected Output: Chuỗi hex 128 ký tự (hash của chuỗi rỗng)
     * Notes: Edge case - data rỗng vẫn tạo hash
     */
    @Test
    @DisplayName("TC-FR-02-001: HMAC với data rỗng")
    void TC_FR_02_001() {
        String result = VnPayUtil.hmacSHA512("secret", "");

        assertNotNull(result);
        assertEquals(128, result.length());
    }

    // ======================== BUILD REFUND HASH DATA ========================

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Tạo refund hash data KHÔNG URL encode
     * Input: Map{"vnp_Amount"="1000000", "vnp_Command"="refund"}
     * Expected Output: "vnp_Amount=1000000&vnp_Command=refund" (không encode)
     * Notes: Refund API của VNPay không URL encode
     */
    @Test
    @DisplayName("TC-FR-02-001: Refund hash data không encode")
    void TC_FR_02_001() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_Amount", "1000000");
        params.put("vnp_Command", "refund");

        String result = VnPayUtil.buildRefundHashData(params);

        assertEquals("vnp_Amount=1000000&vnp_Command=refund", result);
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Refund hash data bỏ qua entry có value null hoặc rỗng
     * Input: Map{"a"="1", "b"=null, "c"="", "d"="4"}
     * Expected Output: "a=1&d=4" (bỏ b và c)
     * Notes: Lọc bỏ các entry rỗng
     */
    @Test
    @DisplayName("TC-FR-02-001: Bỏ qua entry null/rỗng")
    void TC_FR_02_001() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("a", "1");
        params.put("b", null);
        params.put("c", "");
        params.put("d", "4");

        String result = VnPayUtil.buildRefundHashData(params);

        assertEquals("a=1&d=4", result);
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Refund hash data với tất cả entry rỗng
     * Input: Map{"a"=null, "b"=""}
     * Expected Output: "" (chuỗi rỗng)
     * Notes: Edge case - tất cả bị bỏ qua
     */
    @Test
    @DisplayName("TC-FR-02-001: Tất cả entry rỗng")
    void TC_FR_02_001() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("a", null);
        params.put("b", "");

        String result = VnPayUtil.buildRefundHashData(params);

        assertEquals("", result);
    }

    /**
     * Test Case ID: TC-FR-02-001
     * Test Objective: Refund hash data giữ nguyên ký tự đặc biệt (KHÔNG encode)
     * Input: Map{"vnp_OrderInfo"="Hoàn tiền đơn hàng 123"}
     * Expected Output: Chuỗi chứa tiếng Việt nguyên bản (không encode)
     * Notes: Đặc thù refund API không encode
     */
    @Test
    @DisplayName("TC-FR-02-001: Không encode ký tự đặc biệt")
    void TC_FR_02_001() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_OrderInfo", "Hoàn tiền đơn hàng 123");

        String result = VnPayUtil.buildRefundHashData(params);

        assertEquals("vnp_OrderInfo=Hoàn tiền đơn hàng 123", result);
    }
}

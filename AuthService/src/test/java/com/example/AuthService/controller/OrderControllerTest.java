package com.example.AuthService.controller;

import com.example.AuthService.dto.request.CreateOrderRequest;
import com.example.AuthService.dto.request.OrderItemRequest;
import com.example.AuthService.dto.response.OrderResponse;
import com.example.AuthService.dto.response.PageResponse;
import com.example.AuthService.entity.Order;
import com.example.AuthService.entity.User;
import com.example.AuthService.enums.OrderStatus;
import com.example.AuthService.enums.PaymentMethod;
import com.example.AuthService.repository.UserRepository;
import com.example.AuthService.security.jwt.JwtService;
import com.example.AuthService.security.OAuth2LoginSuccessHandler;
import com.example.AuthService.service.OrderService;
import org.springframework.security.core.userdetails.UserDetailsService;
import com.example.AuthService.service.SocialLoginService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.file.AccessDeniedException;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests cho {@link OrderController}.
 * Kiểm tra các endpoint quản lý đơn hàng phía user.
 */
@WebMvcTest(OrderController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private SocialLoginService socialLoginService;

    @MockitoBean
    private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    @MockitoBean
    private UserDetailsService userDetailsService;

    private User createMockUser() {
        User user = new User();
        user.setId(1L);
        user.setEmail("user@test.com");
        user.setName("Test User");
        return user;
    }

    // ======================== CREATE ORDER ========================

    /**
     * Test Case ID: TC_AUTH_OrderController_createOrder_001
     * Test Objective: Tạo đơn hàng thành công
     * Input: CreateOrderRequest với danh sách items hợp lệ, user đã đăng nhập
     * Expected Output: HTTP 200, thông báo tạo thành công kèm orderId
     * Notes: Happy path - user tạo đơn hàng mới
     */
    @Test
    @WithMockUser(username = "user@test.com")
    @DisplayName("TC_AUTH_OrderController_createOrder_001: Tạo đơn hàng thành công")
    void TC_AUTH_OrderController_createOrder_001() throws Exception {
        User user = createMockUser();
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        Order createdOrder = new Order();
        createdOrder.setId(100L);
        when(orderService.createOrder(any(CreateOrderRequest.class), eq(user))).thenReturn(createdOrder);

        CreateOrderRequest request = new CreateOrderRequest();
        request.setShippingAddress("123 Street");
        request.setReceiverName("Nguyen Van A");
        request.setReceiverPhone("0901234567");

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("100")));
    }

    /**
     * Test Case ID: TC_AUTH_OrderController_createOrder_002
     * Test Objective: Tạo đơn hàng khi user không tìm thấy trong DB
     * Input: User đăng nhập nhưng email không tồn tại trong DB
     * Expected Output: HTTP 500 (RuntimeException)
     * Notes: Trường hợp dữ liệu không đồng bộ giữa JWT và DB
     */
    @Test
    @WithMockUser(username = "ghost@test.com")
    @DisplayName("TC_AUTH_OrderController_createOrder_002: Tạo đơn hàng - user không tồn tại trong DB")
    void TC_AUTH_OrderController_createOrder_002() throws Exception {
        when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

        CreateOrderRequest request = new CreateOrderRequest();
        request.setShippingAddress("123 Street");

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());
    }

    /**
     * Test Case ID: TC_AUTH_OrderController_createOrder_003
     * Test Objective: Tạo đơn hàng khi service ném RuntimeException (hết hàng)
     * Input: CreateOrderRequest với item hết hàng
     * Expected Output: HTTP 500
     * Notes: Nghiệp vụ từ chối tạo đơn do kho không đủ
     */
    @Test
    @WithMockUser(username = "user@test.com")
    @DisplayName("TC_AUTH_OrderController_createOrder_003: Tạo đơn hàng - sản phẩm hết hàng")
    void TC_AUTH_OrderController_createOrder_003() throws Exception {
        User user = createMockUser();
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(orderService.createOrder(any(), eq(user)))
                .thenThrow(new RuntimeException("Sản phẩm đã hết hàng"));

        CreateOrderRequest request = new CreateOrderRequest();
        request.setShippingAddress("123 Street");

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());
    }

    // ======================== CONFIRM COD ========================

    /**
     * Test Case ID: TC_AUTH_OrderController_confirmCodPayment_001
     * Test Objective: Xác nhận thanh toán COD thành công
     * Input: orderId=1, user đã đăng nhập
     * Expected Output: HTTP 200, thông báo xác nhận COD thành công
     * Notes: Happy path
     */
    @Test
    @WithMockUser(username = "user@test.com")
    @DisplayName("TC_AUTH_OrderController_confirmCodPayment_001: Xác nhận COD thành công")
    void TC_AUTH_OrderController_confirmCodPayment_001() throws Exception {
        User user = createMockUser();
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        Order confirmedOrder = new Order();
        confirmedOrder.setId(1L);
        when(orderService.confirmCodPayment(1L, user)).thenReturn(confirmedOrder);

        mockMvc.perform(post("/api/orders/1/confirm-cod"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("1")));
    }

    /**
     * Test Case ID: TC_AUTH_OrderController_confirmCodPayment_002
     * Test Objective: Xác nhận COD cho đơn hàng không tồn tại
     * Input: orderId=999, user đã đăng nhập
     * Expected Output: HTTP 500
     * Notes: Order không tìm thấy
     */
    @Test
    @WithMockUser(username = "user@test.com")
    @DisplayName("TC_AUTH_OrderController_confirmCodPayment_002: Xác nhận COD - đơn hàng không tồn tại")
    void TC_AUTH_OrderController_confirmCodPayment_002() throws Exception {
        User user = createMockUser();
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(orderService.confirmCodPayment(999L, user))
                .thenThrow(new RuntimeException("Không tìm thấy đơn hàng"));

        mockMvc.perform(post("/api/orders/999/confirm-cod"))
                .andExpect(status().isInternalServerError());
    }

    /**
     * Test Case ID: TC_AUTH_OrderController_confirmCodPayment_003
     * Test Objective: Xác nhận COD cho đơn hàng đã được xác nhận
     * Input: orderId=1 (đã PAID), user đã đăng nhập
     * Expected Output: HTTP 500
     * Notes: Đơn hàng không ở trạng thái PENDING
     */
    @Test
    @WithMockUser(username = "user@test.com")
    @DisplayName("TC_AUTH_OrderController_confirmCodPayment_003: Xác nhận COD - đơn đã thanh toán")
    void TC_AUTH_OrderController_confirmCodPayment_003() throws Exception {
        User user = createMockUser();
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(orderService.confirmCodPayment(1L, user))
                .thenThrow(new RuntimeException("Đơn hàng đã được xác nhận"));

        mockMvc.perform(post("/api/orders/1/confirm-cod"))
                .andExpect(status().isInternalServerError());
    }

    // ======================== CANCEL ORDER ========================

    /**
     * Test Case ID: TC_AUTH_OrderController_cancelOrder_001
     * Test Objective: Hủy đơn hàng thành công
     * Input: orderId=1, user đã đăng nhập
     * Expected Output: HTTP 200, thông báo hủy thành công
     * Notes: Happy path
     */
    @Test
    @WithMockUser(username = "user@test.com")
    @DisplayName("TC_AUTH_OrderController_cancelOrder_001: Hủy đơn hàng thành công")
    void TC_AUTH_OrderController_cancelOrder_001() throws Exception {
        User user = createMockUser();
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        doNothing().when(orderService).cancelOrder(1L, user);

        mockMvc.perform(put("/api/orders/1/cancel"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Đã huỷ đơn hàng")));
    }

    /**
     * Test Case ID: TC_AUTH_OrderController_cancelOrder_002
     * Test Objective: Hủy đơn hàng đã giao
     * Input: orderId=1 (đã SHIPPED), user đã đăng nhập
     * Expected Output: HTTP 500
     * Notes: Không thể hủy đơn hàng đã giao
     */
    @Test
    @WithMockUser(username = "user@test.com")
    @DisplayName("TC_AUTH_OrderController_cancelOrder_002: Hủy đơn hàng - đơn đã giao không thể hủy")
    void TC_AUTH_OrderController_cancelOrder_002() throws Exception {
        User user = createMockUser();
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        doThrow(new RuntimeException("Không thể hủy đơn hàng đã giao"))
                .when(orderService).cancelOrder(1L, user);

        mockMvc.perform(put("/api/orders/1/cancel"))
                .andExpect(status().isInternalServerError());
    }

    /**
     * Test Case ID: TC_AUTH_OrderController_cancelOrder_003
     * Test Objective: Hủy đơn hàng của người khác
     * Input: orderId=1 (của user khác)
     * Expected Output: HTTP 500
     * Notes: User không có quyền hủy đơn của người khác
     */
    @Test
    @WithMockUser(username = "user@test.com")
    @DisplayName("TC_AUTH_OrderController_cancelOrder_003: Hủy đơn hàng - không phải đơn của mình")
    void TC_AUTH_OrderController_cancelOrder_003() throws Exception {
        User user = createMockUser();
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        doThrow(new RuntimeException("Bạn không có quyền hủy đơn hàng này"))
                .when(orderService).cancelOrder(1L, user);

        mockMvc.perform(put("/api/orders/1/cancel"))
                .andExpect(status().isInternalServerError());
    }

    // ======================== GET ORDERS ========================

    /**
     * Test Case ID: TC_AUTH_OrderController_getOrders_001
     * Test Objective: Lấy danh sách đơn hàng thành công (không filter)
     * Input: User đã đăng nhập, không có filter
     * Expected Output: HTTP 200, danh sách đơn hàng phân trang
     * Notes: Happy path - lấy tất cả đơn hàng của user
     */
    @Test
    @WithMockUser(username = "user@test.com")
    @DisplayName("TC_AUTH_OrderController_getOrders_001: Lấy danh sách đơn hàng thành công")
    void TC_AUTH_OrderController_getOrders_001() throws Exception {
        User user = createMockUser();
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        PageResponse<OrderResponse> pageResponse = PageResponse.<OrderResponse>builder()
                .content(List.of())
                .page(0).size(10).totalElements(0).totalPages(0).last(true)
                .build();
        when(orderService.getOrders(eq(user), isNull(), isNull(), isNull(), isNull(), isNull(), any()))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk());
    }

    /**
     * Test Case ID: TC_AUTH_OrderController_getOrders_002
     * Test Objective: Lấy danh sách đơn hàng có filter theo status
     * Input: User đã đăng nhập, status=PENDING
     * Expected Output: HTTP 200
     * Notes: Lọc theo trạng thái đơn hàng
     */
    @Test
    @WithMockUser(username = "user@test.com")
    @DisplayName("TC_AUTH_OrderController_getOrders_002: Lấy đơn hàng với filter status=PENDING")
    void TC_AUTH_OrderController_getOrders_002() throws Exception {
        User user = createMockUser();
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        PageResponse<OrderResponse> pageResponse = PageResponse.<OrderResponse>builder()
                .content(List.of())
                .page(0).size(10).totalElements(0).totalPages(0).last(true)
                .build();
        when(orderService.getOrders(eq(user), eq(OrderStatus.PENDING), isNull(), isNull(), isNull(), isNull(), any()))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/orders")
                        .param("status", "PENDING"))
                .andExpect(status().isOk());
    }

    /**
     * Test Case ID: TC_AUTH_OrderController_getOrders_003
     * Test Objective: Lấy danh sách đơn hàng với phân trang tùy chỉnh
     * Input: page=1, size=5, sortBy=totalAmount, direction=asc
     * Expected Output: HTTP 200
     * Notes: Kiểm tra tham số phân trang
     */
    @Test
    @WithMockUser(username = "user@test.com")
    @DisplayName("TC_AUTH_OrderController_getOrders_003: Lấy đơn hàng với phân trang tùy chỉnh")
    void TC_AUTH_OrderController_getOrders_003() throws Exception {
        User user = createMockUser();
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        PageResponse<OrderResponse> pageResponse = PageResponse.<OrderResponse>builder()
                .content(List.of())
                .page(1).size(5).totalElements(0).totalPages(0).last(true)
                .build();
        when(orderService.getOrders(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/orders")
                        .param("page", "1")
                        .param("size", "5")
                        .param("sortBy", "totalAmount")
                        .param("direction", "asc"))
                .andExpect(status().isOk());
    }

    // ======================== GET ORDER BY ID ========================

    /**
     * Test Case ID: TC_AUTH_OrderController_getOrderById_001
     * Test Objective: Lấy chi tiết đơn hàng theo ID thành công
     * Input: orderId=1, user đã đăng nhập
     * Expected Output: HTTP 200, body chứa thông tin đơn hàng
     * Notes: Happy path
     */
    @Test
    @WithMockUser(username = "user@test.com")
    @DisplayName("TC_AUTH_OrderController_getOrderById_001: Lấy chi tiết đơn hàng thành công")
    void TC_AUTH_OrderController_getOrderById_001() throws Exception {
        User user = createMockUser();
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        OrderResponse orderResponse = OrderResponse.builder()
                .orderId(1L)
                .totalAmount(BigDecimal.valueOf(100000))
                .status(OrderStatus.PENDING)
                .build();
        when(orderService.getOrderById(user, 1L)).thenReturn(orderResponse);

        mockMvc.perform(get("/api/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(1));
    }

    /**
     * Test Case ID: TC_AUTH_OrderController_getOrderById_002
     * Test Objective: Lấy chi tiết đơn hàng không tồn tại
     * Input: orderId=999
     * Expected Output: HTTP 500
     * Notes: Đơn hàng không tìm thấy
     */
    @Test
    @WithMockUser(username = "user@test.com")
    @DisplayName("TC_AUTH_OrderController_getOrderById_002: Lấy chi tiết đơn hàng - không tồn tại")
    void TC_AUTH_OrderController_getOrderById_002() throws Exception {
        User user = createMockUser();
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(orderService.getOrderById(user, 999L))
                .thenThrow(new RuntimeException("Không tìm thấy đơn hàng"));

        mockMvc.perform(get("/api/orders/999"))
                .andExpect(status().isInternalServerError());
    }

    /**
     * Test Case ID: TC_AUTH_OrderController_getOrderById_003
     * Test Objective: Lấy đơn hàng của người khác (AccessDenied)
     * Input: orderId=1 (thuộc user khác)
     * Expected Output: HTTP 500
     * Notes: Service ném AccessDeniedException
     */
    @Test
    @WithMockUser(username = "user@test.com")
    @DisplayName("TC_AUTH_OrderController_getOrderById_003: Lấy đơn hàng của người khác")
    void TC_AUTH_OrderController_getOrderById_003() throws Exception {
        User user = createMockUser();
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(orderService.getOrderById(user, 1L))
                .thenThrow(new AccessDeniedException("Bạn không có quyền xem đơn hàng này"));

        mockMvc.perform(get("/api/orders/1"))
                .andExpect(status().isInternalServerError());
    }
}

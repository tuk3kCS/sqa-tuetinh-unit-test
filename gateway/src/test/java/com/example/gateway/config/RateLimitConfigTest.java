package com.example.gateway.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests cho RateLimitConfig – cấu hình rate limiting dựa trên IP.
 */
@ExtendWith(MockitoExtension.class)
class RateLimitConfigTest {

    private RateLimitConfig rateLimitConfig;
    private KeyResolver ipKeyResolver;

    @BeforeEach
    void setUp() {
        rateLimitConfig = new RateLimitConfig();
        ipKeyResolver = rateLimitConfig.ipKeyResolver();
    }

    /**
     * Test Case ID: TC_GW_RateLimitConfig_ipKeyResolver_001
     * Test Objective: Trả về đúng IP address khi request có remote address
     * Input: ServerWebExchange với remote address 192.168.1.100
     * Expected Output: Mono chứa "192.168.1.100"
     * Notes: Kiểm tra happy path – request bình thường có IP
     */
    @Test
    @DisplayName("ipKeyResolver trả đúng IP khi request có remote address")
    void ipKeyResolver_withRemoteAddress_returnsIp() throws UnknownHostException {
        // Arrange
        ServerWebExchange exchange = mock(ServerWebExchange.class);
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        InetSocketAddress socketAddress = new InetSocketAddress(
                InetAddress.getByName("192.168.1.100"), 8080
        );

        when(exchange.getRequest()).thenReturn(request);
        when(request.getRemoteAddress()).thenReturn(socketAddress);

        // Act
        Mono<String> result = ipKeyResolver.resolve(exchange);

        // Assert
        StepVerifier.create(result)
                .expectNext("192.168.1.100")
                .verifyComplete();
    }

    /**
     * Test Case ID: TC_GW_RateLimitConfig_ipKeyResolver_002
     * Test Objective: Trả về "unknown" khi request không có remote address (null)
     * Input: ServerWebExchange với getRemoteAddress() = null
     * Expected Output: Mono chứa "unknown"
     * Notes: Kiểm tra nhánh addr == null (proxy hoặc test environment)
     */
    @Test
    @DisplayName("ipKeyResolver trả 'unknown' khi remote address là null")
    void ipKeyResolver_withoutRemoteAddress_returnsUnknown() {
        // Arrange
        ServerWebExchange exchange = mock(ServerWebExchange.class);
        ServerHttpRequest request = mock(ServerHttpRequest.class);

        when(exchange.getRequest()).thenReturn(request);
        when(request.getRemoteAddress()).thenReturn(null);

        // Act
        Mono<String> result = ipKeyResolver.resolve(exchange);

        // Assert
        StepVerifier.create(result)
                .expectNext("unknown")
                .verifyComplete();
    }

    /**
     * Test Case ID: TC_GW_RateLimitConfig_ipKeyResolver_003
     * Test Objective: Trả về "unknown" khi InetSocketAddress có getAddress() = null
     * Input: InetSocketAddress với unresolved host (address = null)
     * Expected Output: Mono chứa "unknown"
     * Notes: Kiểm tra nhánh addr.getAddress() == null (unresolved hostname)
     */
    @Test
    @DisplayName("ipKeyResolver trả 'unknown' khi InetAddress bên trong là null")
    void ipKeyResolver_withNullInetAddress_returnsUnknown() {
        // Arrange – InetSocketAddress.createUnresolved tạo address có getAddress() = null
        ServerWebExchange exchange = mock(ServerWebExchange.class);
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        InetSocketAddress unresolved = InetSocketAddress.createUnresolved("unknown-host", 8080);

        when(exchange.getRequest()).thenReturn(request);
        when(request.getRemoteAddress()).thenReturn(unresolved);

        // Act
        Mono<String> result = ipKeyResolver.resolve(exchange);

        // Assert
        StepVerifier.create(result)
                .expectNext("unknown")
                .verifyComplete();
    }

    /**
     * Test Case ID: TC_GW_RateLimitConfig_ipKeyResolver_004
     * Test Objective: Trả đúng IPv6 loopback address
     * Input: ServerWebExchange với remote address ::1 (IPv6 loopback)
     * Expected Output: Mono chứa "0:0:0:0:0:0:0:1"
     * Notes: Kiểm tra xử lý IPv6
     */
    @Test
    @DisplayName("ipKeyResolver xử lý IPv6 loopback address đúng")
    void ipKeyResolver_withIpv6Loopback_returnsIpv6() throws UnknownHostException {
        // Arrange
        ServerWebExchange exchange = mock(ServerWebExchange.class);
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        InetSocketAddress socketAddress = new InetSocketAddress(
                InetAddress.getByName("::1"), 8080
        );

        when(exchange.getRequest()).thenReturn(request);
        when(request.getRemoteAddress()).thenReturn(socketAddress);

        // Act
        Mono<String> result = ipKeyResolver.resolve(exchange);

        // Assert
        StepVerifier.create(result)
                .expectNextMatches(ip -> ip.contains("0:0:0:0:0:0:0:1") || ip.equals("0:0:0:0:0:0:0:1"))
                .verifyComplete();
    }

    /**
     * Test Case ID: TC_GW_RateLimitConfig_ipKeyResolver_005
     * Test Objective: Kiểm tra ipKeyResolver bean không null
     * Input: Gọi ipKeyResolver()
     * Expected Output: KeyResolver instance không null
     * Notes: Smoke test – bean được tạo đúng
     */
    @Test
    @DisplayName("ipKeyResolver bean được tạo thành công (not null)")
    void ipKeyResolver_beanCreated_notNull() {
        // Assert
        assert ipKeyResolver != null;
    }
}

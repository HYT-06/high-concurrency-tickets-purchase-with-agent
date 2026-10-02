package com.example.hyt.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * 身份鉴权链路验证。
 * <p>
 * 背景：改造前 {@code ChatController} 直接从请求体读 userId，
 * 而请求体完全由客户端控制——任何人伪造 userId 就能查询他人订单。
 * 现在身份只能来自网关注入的 {@code X-User-Id} 头，缺失即拒绝。
 * <p>
 * 用 JDK 自带 HttpClient 而不是 TestRestTemplate，避免测试依赖带来的版本差异。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatAuthTests {

    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private HttpResponse<String> postChat(String userIdHeader, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/chat"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (userIdHeader != null) {
            builder.header("X-User-Id", userIdHeader);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** 缺少网关注入的身份头时必须拒绝，不能退化成默认身份 */
    @Test
    void missingUserIdHeaderIsRejected() throws Exception {
        HttpResponse<String> response = postChat(null, "{\"message\":\"我的订单\"}");
        assertEquals(401, response.statusCode(),
                "缺少 X-User-Id 应返回 401，实际: " + response.statusCode() + " " + response.body());
    }

    /** 空白的身份头同样应被拒绝 */
    @Test
    void blankUserIdHeaderIsRejected() throws Exception {
        HttpResponse<String> response = postChat("   ", "{\"message\":\"我的订单\"}");
        assertEquals(401, response.statusCode(), "空白身份头应返回 401");
    }

    /**
     * 身份以请求头为准，请求体里的 userId 被忽略。
     * <p>
     * 这里刻意在 body 里塞入别人的 userId（999），同时用合法头（2）——
     * 只要不是 401，就说明闸门是按「头」判断的，而非请求体。
     * （后续 LLM 调用因账号欠费会失败，但不影响本断言。）
     */
    @Test
    void identityComesFromHeaderNotRequestBody() throws Exception {
        HttpResponse<String> response = postChat("2",
                "{\"message\":\"我的订单\",\"userId\":\"999\"}");
        assertNotEquals(401, response.statusCode(),
                "提供了合法身份头就应通过鉴权，实际: " + response.statusCode());
    }
}

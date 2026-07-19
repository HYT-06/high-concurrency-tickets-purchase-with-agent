package com.hyt.test.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.rabbitmq.listener.simple.auto-startup=false")
class GatewayRouteForwardingTests {

    private static final AtomicReference<String> forwardedPath = new AtomicReference<>();
    private static final AtomicReference<String> forwardedQuery = new AtomicReference<>();
    private static final HttpServer backend = startBackend();

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void gatewayProperties(DynamicPropertyRegistry registry) {
        registry.add("gateway.routes.app-service-uri",
                () -> "http://localhost:" + backend.getAddress().getPort());
    }

    @AfterAll
    static void stopBackend() {
        backend.stop(0);
    }

    @Test
    void forwardsGatewayTrainRequestsToConfiguredBackend() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/gateway/train/list?from=beijing"))
                .GET()
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("{\"success\":true}");
        assertThat(forwardedPath.get()).isEqualTo("/train/list");
        assertThat(forwardedQuery.get()).isEqualTo("from=beijing");
    }

    private static HttpServer startBackend() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                forwardedPath.set(exchange.getRequestURI().getPath());
                forwardedQuery.set(exchange.getRequestURI().getQuery());
                byte[] body = "{\"success\":true}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream responseBody = exchange.getResponseBody()) {
                    responseBody.write(body);
                }
            });
            server.setExecutor(Executors.newSingleThreadExecutor());
            server.start();
            return server;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to start gateway test backend", ex);
        }
    }
}


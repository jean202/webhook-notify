package com.jean202.webhooknotify.core.channel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jean202.webhooknotify.core.NotifyMessage;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SlackChannelTest {
    private final AtomicReference<CapturedRequest> capturedRequest = new AtomicReference<>();
    private final AtomicInteger responseStatus = new AtomicInteger(200);
    private final AtomicReference<String> responseBody = new AtomicReference<>("ok");

    private HttpServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/slack", this::handleExchange);
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendPostsJsonPayloadToWebhook() {
        SlackChannel channel = new SlackChannel(webhookUrl());

        channel.send(NotifyMessage.of("Deploy \"prod\"", "line1\\path\nline2"));

        CapturedRequest request = capturedRequest.get();
        assertNotNull(request);
        assertEquals("POST", request.method());
        assertEquals("application/json; charset=UTF-8", request.contentType());
        assertEquals("{\"text\":\"*Deploy \\\"prod\\\"*\\nline1\\\\path\\nline2\"}", request.body());
    }

    @Test
    void sendRejectsBlankText() {
        SlackChannel channel = new SlackChannel(webhookUrl());

        assertThrows(IllegalArgumentException.class, () -> channel.send(NotifyMessage.text("   ")));
    }

    @Test
    void sendThrowsWhenSlackReturnsNonSuccessStatus() {
        responseStatus.set(500);
        responseBody.set("failure");
        SlackChannel channel = new SlackChannel(webhookUrl());

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> channel.send(NotifyMessage.text("deploy failed"))
        );

        assertTrue(exception.getMessage().contains("500"));
        assertTrue(exception.getMessage().contains("failure"));
    }

    @Test
    void sendTimesOutWhenWebhookAcceptsButNeverResponds() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        server.createContext("/slack-stall", exchange -> {
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        SlackChannel channel = new SlackChannel(
            "http://localhost:%d/slack-stall".formatted(server.getAddress().getPort()),
            HttpClient.newHttpClient(),
            Duration.ofMillis(200)
        );

        try {
            long startedAt = System.nanoTime();
            IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> channel.send(NotifyMessage.text("stalled"))
            );
            long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;

            assertTrue(exception.getMessage().contains("Timed out"), exception.getMessage());
            assertInstanceOf(HttpTimeoutException.class, exception.getCause());
            assertTrue(elapsedMillis < 5_000, "send should give the thread back promptly, took " + elapsedMillis + "ms");
        } finally {
            release.countDown();
        }
    }

    @Test
    void constructorRejectsNonPositiveRequestTimeout() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new SlackChannel(webhookUrl(), HttpClient.newHttpClient(), Duration.ZERO)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new SlackChannel(webhookUrl(), HttpClient.newHttpClient(), Duration.ofSeconds(-1))
        );
    }

    private void handleExchange(HttpExchange exchange) throws IOException {
        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        capturedRequest.set(new CapturedRequest(
            exchange.getRequestMethod(),
            exchange.getRequestHeaders().getFirst("Content-Type"),
            requestBody
        ));

        byte[] responseBytes = responseBody.get().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(responseStatus.get(), responseBytes.length);
        exchange.getResponseBody().write(responseBytes);
        exchange.close();
    }

    private String webhookUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/slack";
    }

    private record CapturedRequest(String method, String contentType, String body) {
    }
}

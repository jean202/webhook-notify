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

class DiscordChannelTest {
    private final AtomicReference<CapturedRequest> capturedRequest = new AtomicReference<>();
    private final AtomicInteger responseStatus = new AtomicInteger(204);
    private final AtomicReference<String> responseBody = new AtomicReference<>("");

    private HttpServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/discord", this::handleExchange);
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendPostsContentPayloadForBodyOnly() {
        DiscordChannel channel = new DiscordChannel(webhookUrl());

        channel.send(NotifyMessage.text("deploy complete"));

        CapturedRequest request = capturedRequest.get();
        assertNotNull(request);
        assertEquals("POST", request.method());
        assertEquals("application/json; charset=UTF-8", request.contentType());
        assertEquals("{\"content\":\"deploy complete\"}", request.body());
    }

    @Test
    void sendPostsEmbedPayloadForTitledMessage() {
        DiscordChannel channel = new DiscordChannel(webhookUrl());

        channel.send(NotifyMessage.of("Deploy", "v2.1.0 released"));

        CapturedRequest request = capturedRequest.get();
        assertNotNull(request);
        assertEquals(
                "{\"embeds\":[{\"title\":\"Deploy\",\"description\":\"v2.1.0 released\"}]}",
                request.body()
        );
    }

    @Test
    void sendEscapesSpecialCharacters() {
        DiscordChannel channel = new DiscordChannel(webhookUrl());

        channel.send(NotifyMessage.text("line1\nline2 \"quoted\""));

        CapturedRequest request = capturedRequest.get();
        assertNotNull(request);
        assertEquals("{\"content\":\"line1\\nline2 \\\"quoted\\\"\"}", request.body());
    }

    @Test
    void sendRejectsBlankText() {
        DiscordChannel channel = new DiscordChannel(webhookUrl());

        assertThrows(IllegalArgumentException.class, () -> channel.send(NotifyMessage.text("   ")));
    }

    @Test
    void sendThrowsOnNonSuccessStatus() {
        responseStatus.set(400);
        responseBody.set("bad request");
        DiscordChannel channel = new DiscordChannel(webhookUrl());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> channel.send(NotifyMessage.text("test"))
        );

        assertTrue(exception.getMessage().contains("400"));
    }

    @Test
    void sendTimesOutWhenWebhookAcceptsButNeverResponds() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        server.createContext("/discord-stall", exchange -> {
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        DiscordChannel channel = new DiscordChannel(
                "http://localhost:%d/discord-stall".formatted(server.getAddress().getPort()),
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
                () -> new DiscordChannel(webhookUrl(), HttpClient.newHttpClient(), Duration.ZERO)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new DiscordChannel(webhookUrl(), HttpClient.newHttpClient(), Duration.ofSeconds(-1))
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
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/discord";
    }

    private record CapturedRequest(String method, String contentType, String body) {
    }
}

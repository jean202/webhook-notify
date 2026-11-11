package com.jean202.webhooknotify.core.channel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jean202.webhooknotify.core.NotifyChannel;
import com.jean202.webhooknotify.core.NotifyMessage;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

class ChannelDeliveryTest {
    @TestFactory
    Stream<DynamicTest> customTimeoutBoundsHeadersAndBody() {
        return Stream.of("slack", "discord").flatMap(name -> Stream.of(false, true).map(delayBody ->
            DynamicTest.dynamicTest(name + (delayBody ? " body" : " headers"), () -> {
                try (DelayedWebhook webhook = new DelayedWebhook(2, delayBody, 200)) {
                    NotifyChannel channel = channel(name, webhook.url(), Duration.ofMillis(500));

                    IllegalStateException failure = assertThrows(
                        IllegalStateException.class, () -> channel.send(NotifyMessage.text("test"))
                    );

                    assertInstanceOf(HttpTimeoutException.class, failure.getCause());
                    if (delayBody) {
                        assertEquals(0, webhook.bodyStarted.getCount(), "must reach the stalled response body");
                    }
                }
            })
        ));
    }

    @TestFactory
    Stream<DynamicTest> rejectsInvalidTimeouts() {
        return Stream.of("slack", "discord").flatMap(name -> Stream.of(
            Duration.ZERO, Duration.ofNanos(-1), Duration.ofSeconds(Long.MAX_VALUE)
        ).map(timeout -> DynamicTest.dynamicTest(name + " " + timeout, () ->
            assertThrows(IllegalArgumentException.class, () -> channel(name, "http://127.0.0.1", timeout))
        )));
    }

    @TestFactory
    Stream<DynamicTest> rejectsNullTimeout() {
        return Stream.of("slack", "discord").map(name -> DynamicTest.dynamicTest(name, () ->
            assertThrows(NullPointerException.class, () -> channel(name, "http://127.0.0.1", null))
        ));
    }

    @TestFactory
    Stream<DynamicTest> customTimeoutAllowsSuccessfulDelivery() {
        return Stream.of("slack", "discord").map(name -> DynamicTest.dynamicTest(name, () -> {
            try (DelayedWebhook webhook = new DelayedWebhook(0, false, 200)) {
                channel(name, webhook.url(), Duration.ofSeconds(2)).send(NotifyMessage.text("test"));
                assertEquals(1, webhook.requestCount.get());
            }
        }));
    }

    @TestFactory
    Stream<DynamicTest> httpErrorsAreReportedWithoutRetry() {
        return Stream.of("slack", "discord").flatMap(name -> Stream.of(429, 500).map(status ->
            DynamicTest.dynamicTest(name + " " + status, () -> {
                try (DelayedWebhook webhook = new DelayedWebhook(0, false, status)) {
                    NotifyChannel channel = channel(name, webhook.url(), Duration.ofSeconds(2));
                    IllegalStateException failure = assertThrows(
                        IllegalStateException.class, () -> channel.send(NotifyMessage.text("test"))
                    );
                    assertTrue(failure.getMessage().contains(Integer.toString(status)));
                    assertTrue(failure.getMessage().contains("ok"));
                    assertEquals(1, webhook.requestCount.get());
                }
            })
        ));
    }

    @TestFactory
    Stream<DynamicTest> closedConnectionPreservesIoCause() {
        return Stream.of("slack", "discord").map(name -> DynamicTest.dynamicTest(name, () -> {
            try (DelayedWebhook webhook = new DelayedWebhook(0, false, 0)) {
                NotifyChannel channel = channel(name, webhook.url(), Duration.ofSeconds(2));
                IllegalStateException failure = assertThrows(
                    IllegalStateException.class, () -> channel.send(NotifyMessage.text("test"))
                );
                assertInstanceOf(IOException.class, failure.getCause());
            }
        }));
    }

    @TestFactory
    Stream<DynamicTest> interruptedSendRestoresInterruptFlag() {
        return Stream.of("slack", "discord").map(name -> DynamicTest.dynamicTest(name, () -> {
            try (DelayedWebhook webhook = new DelayedWebhook(5, true, 200)) {
                NotifyChannel channel = channel(name, webhook.url(), Duration.ofSeconds(30));
                AtomicReference<Throwable> failure = new AtomicReference<>();
                AtomicBoolean interrupted = new AtomicBoolean();
                Thread sender = new Thread(() -> {
                    try {
                        channel.send(NotifyMessage.text("test"));
                    } catch (Throwable exception) {
                        failure.set(exception);
                        interrupted.set(Thread.currentThread().isInterrupted());
                    }
                });
                sender.setDaemon(true);
                sender.start();
                try {
                    assertTrue(webhook.bodyStarted.await(2, TimeUnit.SECONDS));
                    sender.interrupt();
                    sender.join(2_000);
                    assertFalse(sender.isAlive(), "interruption must unblock the sender");
                    assertNotNull(failure.get());
                    assertInstanceOf(IllegalStateException.class, failure.get());
                    assertInstanceOf(InterruptedException.class, failure.get().getCause());
                    assertTrue(interrupted.get());
                } finally {
                    sender.interrupt();
                    webhook.release.countDown();
                    sender.join(2_000);
                }
            }
        }));
    }

    private static NotifyChannel channel(String name, String url, Duration timeout) {
        HttpClient client = HttpClient.newHttpClient();
        return name.equals("slack")
            ? new SlackChannel(url, client, timeout) : new DiscordChannel(url, client, timeout);
    }

    @TestFactory
    Stream<DynamicTest> legacyConstructorsTimeOut() {
        return Stream.of("slack", "discord").map(name -> DynamicTest.dynamicTest(name, () -> {
            try (DelayedWebhook webhook = new DelayedWebhook()) {
                NotifyChannel channel = name.equals("slack")
                    ? new SlackChannel(webhook.url()) : new DiscordChannel(webhook.url());

                long started = System.nanoTime();
                IllegalStateException failure = assertThrows(
                    IllegalStateException.class, () -> channel.send(NotifyMessage.text("test"))
                );

                assertInstanceOf(HttpTimeoutException.class, failure.getCause());
                assertTrue(Duration.ofNanos(System.nanoTime() - started).compareTo(Duration.ofSeconds(12)) < 0);
            }
        }));
    }

    private static final class DelayedWebhook implements AutoCloseable {
        private final CountDownLatch release = new CountDownLatch(1);
        private final CountDownLatch bodyStarted = new CountDownLatch(1);
        private final AtomicInteger requestCount = new AtomicInteger();
        private final HttpServer server;

        private DelayedWebhook() throws IOException {
            this(12, false, 200);
        }

        private DelayedWebhook(long waitSeconds, boolean delayBody, int status) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/webhook", exchange -> {
                try {
                    exchange.getRequestBody().readAllBytes();
                    requestCount.incrementAndGet();
                    if (status == 0) {
                        return;
                    }
                    byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
                    if (delayBody) {
                        exchange.sendResponseHeaders(status, body.length);
                        exchange.getResponseBody().write(body, 0, 1);
                        exchange.getResponseBody().flush();
                        bodyStarted.countDown();
                        release.await(waitSeconds, TimeUnit.SECONDS);
                        exchange.getResponseBody().write(body, 1, 1);
                    } else {
                        release.await(waitSeconds, TimeUnit.SECONDS);
                        exchange.sendResponseHeaders(status, body.length);
                        exchange.getResponseBody().write(body);
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } finally {
                    exchange.close();
                }
            });
            server.start();
        }

        private String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/webhook";
        }

        @Override
        public void close() {
            release.countDown();
            server.stop(0);
        }
    }
}

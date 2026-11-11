package com.jean202.webhooknotify.core.channel;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class WebhookHttpSender {
    static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private WebhookHttpSender() {
    }

    static long timeoutNanos(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        try {
            return timeout.toNanos();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("timeout must be representable in nanoseconds", exception);
        }
    }

    static HttpResponse<String> send(HttpClient client, HttpRequest request, long timeoutNanos)
        throws IOException, InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException("Interrupted before sending webhook notification");
        }

        long started = System.nanoTime();
        CompletableFuture<HttpResponse<String>> response = client.sendAsync(
            request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        try {
            // On Java 17, HttpRequest.timeout stops at headers; wait for the complete body here.
            long remaining = timeoutNanos - (System.nanoTime() - started);
            if (remaining <= 0) {
                throw new TimeoutException();
            }
            return response.get(remaining, TimeUnit.NANOSECONDS);
        } catch (TimeoutException exception) {
            response.cancel(true);
            HttpTimeoutException timeout = new HttpTimeoutException("Webhook request timed out");
            timeout.initCause(exception);
            throw timeout;
        } catch (InterruptedException exception) {
            response.cancel(true);
            throw exception;
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IOException("Failed to send webhook notification", cause);
        }
    }
}

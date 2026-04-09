package com.jean202.webhooknotify.core.channel;

import com.jean202.webhooknotify.core.NotifyChannel;
import com.jean202.webhooknotify.core.NotifyMessage;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class DiscordChannel implements NotifyChannel {
    private final URI webhookUri;
    private final HttpClient httpClient;

    public DiscordChannel(String webhookUrl) {
        this(webhookUrl, HttpClient.newHttpClient());
    }

    public DiscordChannel(String webhookUrl, HttpClient httpClient) {
        this.webhookUri = URI.create(requireNonBlank(webhookUrl, "webhookUrl"));
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    }

    @Override
    public String name() {
        return "discord";
    }

    @Override
    public void send(NotifyMessage message) {
        Objects.requireNonNull(message, "message");

        HttpRequest request = HttpRequest.newBuilder(webhookUri)
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(toPayload(message), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while sending Discord notification", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to send Discord notification", exception);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(
                    "Discord webhook request failed with status " + response.statusCode() + ": " + response.body()
            );
        }
    }

    private static String toPayload(NotifyMessage message) {
        String title = message.title().trim();
        String body = message.body().trim();

        if (title.isEmpty() && body.isEmpty()) {
            throw new IllegalArgumentException("Discord notification text must not be blank");
        }

        if (title.isEmpty()) {
            return "{\"content\":\"" + escapeJson(body) + "\"}";
        }

        // Use embed for titled messages
        return "{\"embeds\":[{\"title\":\"" + escapeJson(title) +
                "\",\"description\":\"" + escapeJson(body) + "\"}]}";
    }

    private static String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}

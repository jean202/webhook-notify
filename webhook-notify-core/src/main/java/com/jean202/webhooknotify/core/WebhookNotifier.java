package com.jean202.webhooknotify.core;

import com.jean202.webhooknotify.core.channel.DiscordChannel;
import com.jean202.webhooknotify.core.channel.SlackChannel;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

public final class WebhookNotifier {
    private final List<NotifyChannel> channels;

    private WebhookNotifier(List<NotifyChannel> channels) {
        this.channels = List.copyOf(channels);
    }

    public static Builder builder() {
        return new Builder();
    }

    public void send(NotifyMessage message) {
        for (NotifyChannel channel : channels) {
            channel.send(message);
        }
    }

    public void sendTo(List<String> channelNames, NotifyMessage message) {
        Objects.requireNonNull(channelNames, "channelNames");
        Objects.requireNonNull(message, "message");

        Set<String> requestedChannelNames = normalizeChannelNames(channelNames);
        Set<String> availableChannelNames = channels.stream()
            .map(channel -> normalizeChannelName(channel.name(), "channel.name()"))
            .collect(Collectors.toCollection(LinkedHashSet::new));

        List<String> missingChannelNames = requestedChannelNames.stream()
            .filter(channelName -> !availableChannelNames.contains(channelName))
            .toList();
        if (!missingChannelNames.isEmpty()) {
            throw new IllegalArgumentException(
                "Unknown notify channel(s): " + String.join(", ", missingChannelNames)
            );
        }

        for (NotifyChannel channel : channels) {
            String normalizedChannelName = normalizeChannelName(channel.name(), "channel.name()");
            if (requestedChannelNames.contains(normalizedChannelName)) {
                channel.send(message);
            }
        }
    }

    public void send(String body) {
        send(NotifyMessage.text(body));
    }

    public void send(String title, String body) {
        send(NotifyMessage.of(title, body));
    }

    public void sendTo(List<String> channelNames, String body) {
        sendTo(channelNames, NotifyMessage.text(body));
    }

    public void sendTo(List<String> channelNames, String title, String body) {
        sendTo(channelNames, NotifyMessage.of(title, body));
    }

    public ConditionalSend when(boolean condition) {
        return new ConditionalSend(condition);
    }

    public ConditionalSend when(BooleanSupplier conditionSupplier) {
        Objects.requireNonNull(conditionSupplier, "conditionSupplier");
        return when(conditionSupplier.getAsBoolean());
    }

    public ConditionalSend when(NotifyCondition condition, Map<String, ?> variables) {
        Objects.requireNonNull(condition, "condition");
        return when(condition.matches(Objects.requireNonNull(variables, "variables")));
    }

    public ConditionalSend when(String conditionExpression, Map<String, ?> variables) {
        Objects.requireNonNull(conditionExpression, "conditionExpression");
        return when(NotifyCondition.evaluate(conditionExpression, Objects.requireNonNull(variables, "variables")));
    }

    public final class ConditionalSend {
        private final boolean matched;

        private ConditionalSend(boolean matched) {
            this.matched = matched;
        }

        public boolean matched() {
            return matched;
        }

        public boolean send(NotifyMessage message) {
            if (!matched) {
                return false;
            }
            WebhookNotifier.this.send(message);
            return true;
        }

        public boolean send(String body) {
            return send(NotifyMessage.text(body));
        }

        public boolean send(String title, String body) {
            return send(NotifyMessage.of(title, body));
        }
    }

    public static final class Builder {
        private final List<NotifyChannel> channels = new ArrayList<>();

        public Builder channel(NotifyChannel channel) {
            channels.add(Objects.requireNonNull(channel, "channel"));
            return this;
        }

        public Builder slack(String webhookUrl) {
            channels.add(new SlackChannel(webhookUrl));
            return this;
        }

        public Builder discord(String webhookUrl) {
            channels.add(new DiscordChannel(webhookUrl));
            return this;
        }

        public WebhookNotifier build() {
            if (channels.isEmpty()) {
                throw new IllegalStateException("At least one notify channel must be configured");
            }
            return new WebhookNotifier(channels);
        }
    }

    private static Set<String> normalizeChannelNames(List<String> channelNames) {
        if (channelNames.isEmpty()) {
            throw new IllegalArgumentException("channelNames must not be empty");
        }

        Set<String> normalizedChannelNames = new LinkedHashSet<>();
        for (String channelName : channelNames) {
            normalizedChannelNames.add(normalizeChannelName(channelName, "channelNames"));
        }
        return normalizedChannelNames;
    }

    private static String normalizeChannelName(String channelName, String fieldName) {
        Objects.requireNonNull(channelName, fieldName);

        String normalizedChannelName = channelName.trim().toLowerCase(Locale.ROOT);
        if (normalizedChannelName.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not contain blank channel names");
        }
        return normalizedChannelName;
    }
}

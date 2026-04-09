package com.jean202.webhooknotify.core;

import com.jean202.webhooknotify.core.channel.DiscordChannel;
import com.jean202.webhooknotify.core.channel.SlackChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;

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

    public void send(String body) {
        send(NotifyMessage.text(body));
    }

    public void send(String title, String body) {
        send(NotifyMessage.of(title, body));
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
}

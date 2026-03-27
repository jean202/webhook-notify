package com.jean202.webhooknotify.core;

import java.util.ArrayList;
import java.util.List;

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

    public static final class Builder {
        private final List<NotifyChannel> channels = new ArrayList<>();

        public Builder channel(NotifyChannel channel) {
            channels.add(channel);
            return this;
        }

        public WebhookNotifier build() {
            return new WebhookNotifier(channels);
        }
    }
}

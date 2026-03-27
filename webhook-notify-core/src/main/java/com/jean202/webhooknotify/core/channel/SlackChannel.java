package com.jean202.webhooknotify.core.channel;

import com.jean202.webhooknotify.core.NotifyChannel;
import com.jean202.webhooknotify.core.NotifyMessage;

public class SlackChannel implements NotifyChannel {
    @Override
    public String name() {
        return "slack";
    }

    @Override
    public void send(NotifyMessage message) {
        // Initial skeleton only. Real HTTP delivery will be added later.
    }
}

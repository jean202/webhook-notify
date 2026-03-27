package com.jean202.webhooknotify.test;

import com.jean202.webhooknotify.core.NotifyChannel;
import com.jean202.webhooknotify.core.NotifyMessage;
import java.util.ArrayList;
import java.util.List;

public class FakeChannel implements NotifyChannel {
    private final List<NotifyMessage> sentMessages = new ArrayList<>();

    @Override
    public String name() {
        return "fake";
    }

    @Override
    public void send(NotifyMessage message) {
        sentMessages.add(message);
    }

    public List<NotifyMessage> sentMessages() {
        return List.copyOf(sentMessages);
    }
}

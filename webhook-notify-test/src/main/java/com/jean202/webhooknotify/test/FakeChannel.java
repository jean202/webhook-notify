package com.jean202.webhooknotify.test;

import com.jean202.webhooknotify.core.NotifyChannel;
import com.jean202.webhooknotify.core.NotifyMessage;
import java.util.ArrayList;
import java.util.List;

public class FakeChannel implements NotifyChannel {
    private final List<NotifyMessage> sentMessages = new ArrayList<>();
    private final String name;

    public FakeChannel() {
        this("fake");
    }

    public FakeChannel(String name) {
        this.name = name;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void send(NotifyMessage message) {
        sentMessages.add(message);
    }

    public List<NotifyMessage> sentMessages() {
        return List.copyOf(sentMessages);
    }
}

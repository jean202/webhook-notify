package com.jean202.webhooknotify.core;

public interface NotifyChannel {
    String name();

    void send(NotifyMessage message);
}

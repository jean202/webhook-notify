package com.jean202.webhooknotify.core;

public record NotifyMessage(String title, String body) {
    public static NotifyMessage of(String title, String body) {
        return new NotifyMessage(title, body);
    }
}

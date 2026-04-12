package com.jean202.webhooknotify.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class WebhookNotifierTest {
    @Test
    void buildRequiresAtLeastOneChannel() {
        assertThrows(IllegalStateException.class, () -> WebhookNotifier.builder().build());
    }

    @Test
    void sendStringWrapsPlainTextMessage() {
        RecordingChannel channel = new RecordingChannel();
        WebhookNotifier notifier = WebhookNotifier.builder()
            .channel(channel)
            .build();

        notifier.send("deploy completed");

        assertEquals(List.of(NotifyMessage.text("deploy completed")), channel.messages());
    }

    @Test
    void sendTitleAndBodyWrapsStructuredMessage() {
        RecordingChannel channel = new RecordingChannel();
        WebhookNotifier notifier = WebhookNotifier.builder()
            .channel(channel)
            .build();

        notifier.send("Deploy", "v1.2.3");

        assertEquals(List.of(NotifyMessage.of("Deploy", "v1.2.3")), channel.messages());
    }

    @Test
    void conditionalSendSkipsWhenConditionIsFalse() {
        RecordingChannel channel = new RecordingChannel();
        WebhookNotifier notifier = WebhookNotifier.builder()
            .channel(channel)
            .build();

        boolean sent = notifier.when(false).send("deploy completed");

        assertFalse(sent);
        assertEquals(List.of(), channel.messages());
    }

    @Test
    void conditionalSendEvaluatesBooleanSupplier() {
        RecordingChannel channel = new RecordingChannel();
        WebhookNotifier notifier = WebhookNotifier.builder()
            .channel(channel)
            .build();
        AtomicBoolean invoked = new AtomicBoolean(false);

        boolean sent = notifier.when(() -> {
            invoked.set(true);
            return true;
        }).send("deploy completed");

        assertTrue(invoked.get());
        assertTrue(sent);
        assertEquals(List.of(NotifyMessage.text("deploy completed")), channel.messages());
    }

    @Test
    void conditionalSendSupportsStringConditionExpression() {
        RecordingChannel channel = new RecordingChannel();
        WebhookNotifier notifier = WebhookNotifier.builder()
            .channel(channel)
            .build();

        boolean sent = notifier.when(
            "result.changeRate.abs() > 5.0 && !result.achieved",
            Map.of("result", new PriceResult(new BigDecimal("-6.2"), false))
        ).send("급등 감지");

        assertTrue(sent);
        assertEquals(List.of(NotifyMessage.text("급등 감지")), channel.messages());
    }

    @Test
    void conditionalSendSupportsParsedCondition() {
        RecordingChannel channel = new RecordingChannel();
        WebhookNotifier notifier = WebhookNotifier.builder()
            .channel(channel)
            .build();
        NotifyCondition condition = NotifyCondition.parse("result.tierJustAchieved");

        boolean sent = notifier.when(
            condition,
            Map.of("result", new TierResult(false))
        ).send("티어 달성");

        assertFalse(sent);
        assertEquals(List.of(), channel.messages());
    }

    @Test
    void sendToDeliversOnlyRequestedChannels() {
        RecordingChannel slackPrimary = new RecordingChannel("slack");
        RecordingChannel discord = new RecordingChannel("discord");
        RecordingChannel slackSecondary = new RecordingChannel("slack");
        WebhookNotifier notifier = WebhookNotifier.builder()
            .channel(slackPrimary)
            .channel(discord)
            .channel(slackSecondary)
            .build();

        notifier.sendTo(List.of(" SLACK "), "deploy completed");

        assertEquals(List.of(NotifyMessage.text("deploy completed")), slackPrimary.messages());
        assertEquals(List.of(), discord.messages());
        assertEquals(List.of(NotifyMessage.text("deploy completed")), slackSecondary.messages());
    }

    @Test
    void sendToRejectsUnknownChannelsBeforeSending() {
        RecordingChannel slack = new RecordingChannel("slack");
        RecordingChannel discord = new RecordingChannel("discord");
        WebhookNotifier notifier = WebhookNotifier.builder()
            .channel(slack)
            .channel(discord)
            .build();

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> notifier.sendTo(List.of("slack", "telegram"), "deploy completed")
        );

        assertTrue(exception.getMessage().contains("telegram"));
        assertEquals(List.of(), slack.messages());
        assertEquals(List.of(), discord.messages());
    }

    private static final class RecordingChannel implements NotifyChannel {
        private final List<NotifyMessage> messages = new ArrayList<>();
        private final String name;

        private RecordingChannel() {
            this("recording");
        }

        private RecordingChannel(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public void send(NotifyMessage message) {
            messages.add(message);
        }

        List<NotifyMessage> messages() {
            return List.copyOf(messages);
        }
    }

    private record PriceResult(BigDecimal changeRate, boolean achieved) {
    }

    private record TierResult(boolean tierJustAchieved) {
    }
}

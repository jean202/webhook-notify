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

    private static final class RecordingChannel implements NotifyChannel {
        private final List<NotifyMessage> messages = new ArrayList<>();

        @Override
        public String name() {
            return "recording";
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

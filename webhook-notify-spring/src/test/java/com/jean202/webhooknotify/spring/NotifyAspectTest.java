package com.jean202.webhooknotify.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jean202.webhooknotify.core.WebhookNotifier;
import com.jean202.webhooknotify.test.FakeChannel;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

class NotifyAspectTest {

    private final FakeChannel slackChannel = new FakeChannel("slack");
    private final FakeChannel discordChannel = new FakeChannel("discord");
    private final WebhookNotifier notifier = WebhookNotifier.builder()
        .channel(slackChannel)
        .channel(discordChannel)
        .build();
    private final NotifyAspect aspect = new NotifyAspect(notifier);

    private <T> T proxy(T target) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(aspect);
        return factory.getProxy();
    }

    @Test
    void sendsNotificationAfterMethodExecution() {
        SampleService service = proxy(new SampleService());
        service.simple();

        assertEquals(1, slackChannel.sentMessages().size());
        assertEquals(1, discordChannel.sentMessages().size());
        assertEquals("hello", slackChannel.sentMessages().get(0).body());
        assertEquals("hello", discordChannel.sentMessages().get(0).body());
    }

    @Test
    void rendersTemplateWithReturnValue() {
        SampleService service = proxy(new SampleService());
        service.withTemplate();

        assertEquals(1, slackChannel.sentMessages().size());
        assertEquals(1, discordChannel.sentMessages().size());
        assertEquals("order 42 completed", slackChannel.sentMessages().get(0).body());
        assertEquals("order 42 completed", discordChannel.sentMessages().get(0).body());
    }

    @Test
    void skipsNotificationWhenConditionIsFalse() {
        SampleService service = proxy(new SampleService());
        service.conditionFalse();

        assertTrue(slackChannel.sentMessages().isEmpty());
        assertTrue(discordChannel.sentMessages().isEmpty());
    }

    @Test
    void sendsNotificationWhenConditionIsTrue() {
        SampleService service = proxy(new SampleService());
        service.conditionTrue();

        assertEquals(1, slackChannel.sentMessages().size());
        assertEquals(1, discordChannel.sentMessages().size());
    }

    @Test
    void sendsNotificationOnlyToSelectedChannel() {
        SampleService service = proxy(new SampleService());
        service.discordOnly();

        assertTrue(slackChannel.sentMessages().isEmpty());
        assertEquals(1, discordChannel.sentMessages().size());
        assertEquals("discord only", discordChannel.sentMessages().get(0).body());
    }

    @Test
    void sendsNotificationToCommaSeparatedChannels() {
        SampleService service = proxy(new SampleService());
        service.selectedChannels();

        assertEquals(1, slackChannel.sentMessages().size());
        assertEquals(1, discordChannel.sentMessages().size());
        assertEquals("selected channels", slackChannel.sentMessages().get(0).body());
        assertEquals("selected channels", discordChannel.sentMessages().get(0).body());
    }

    @Test
    void rejectsUnknownSelectedChannel() {
        SampleService service = proxy(new SampleService());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, service::unknownChannel);

        assertTrue(exception.getMessage().contains("telegram"));
        assertTrue(slackChannel.sentMessages().isEmpty());
        assertTrue(discordChannel.sentMessages().isEmpty());
    }

    public static class SampleService {
        @Notify
        public String simple() {
            return "hello";
        }

        @Notify(template = "order #{result.id} completed")
        public Order withTemplate() {
            return new Order(42);
        }

        @Notify(condition = "result.amount > 1000", template = "big order: #{result.amount}")
        public Order conditionFalse() {
            return new Order(500);
        }

        @Notify(condition = "result.amount > 100", template = "big order: #{result.amount}")
        public Order conditionTrue() {
            return new Order(500);
        }

        @Notify(channel = "discord", template = "discord only")
        public String discordOnly() {
            return "ignored";
        }

        @Notify(channel = " discord, slack ", template = "selected channels")
        public String selectedChannels() {
            return "ignored";
        }

        @Notify(channel = "telegram", template = "unknown")
        public String unknownChannel() {
            return "ignored";
        }
    }

    public static class Order {
        private final int id;
        private final int amount;

        Order(int id) {
            this(id, id);
        }

        Order(int id, int amount) {
            this.id = id;
            this.amount = amount;
        }

        public int getId() { return id; }
        public int getAmount() { return amount; }
    }
}

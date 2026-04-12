package com.jean202.webhooknotify.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jean202.webhooknotify.core.WebhookNotifier;
import com.jean202.webhooknotify.test.FakeChannel;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class NotifyAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(NotifyAutoConfiguration.class));

    @Test
    void skipsAutoConfiguredNotifierWhenNoWebhookUrlIsConfigured() {
        contextRunner.run(context -> {
            assertTrue(context.getBeansOfType(WebhookNotifier.class).isEmpty());
            assertTrue(context.getBeansOfType(NotifyAspect.class).isEmpty());
        });
    }

    @Test
    void createsNotifierAndAspectWhenSlackWebhookUrlIsConfigured() {
        contextRunner
            .withPropertyValues("webhook-notify.slack.webhook-url=https://hooks.slack.com/services/test")
            .run(context -> {
                assertEquals(1, context.getBeansOfType(WebhookNotifier.class).size());
                assertEquals(1, context.getBeansOfType(NotifyAspect.class).size());
            });
    }

    @Test
    void ignoresBlankWebhookUrlValues() {
        contextRunner
            .withPropertyValues("webhook-notify.slack.webhook-url=   ")
            .run(context -> {
                assertTrue(context.getBeansOfType(WebhookNotifier.class).isEmpty());
                assertTrue(context.getBeansOfType(NotifyAspect.class).isEmpty());
            });
    }

    @Test
    void backsOffToUserProvidedNotifierBean() {
        contextRunner
            .withUserConfiguration(UserNotifierConfiguration.class)
            .run(context -> {
                WebhookNotifier notifier = context.getBean(WebhookNotifier.class);

                assertSame(context.getBean("customNotifier"), notifier);
                assertEquals(1, context.getBeansOfType(NotifyAspect.class).size());
            });
    }

    @Configuration(proxyBeanMethods = false)
    static class UserNotifierConfiguration {
        @Bean
        WebhookNotifier customNotifier() {
            return WebhookNotifier.builder()
                .channel(new FakeChannel())
                .build();
        }
    }
}

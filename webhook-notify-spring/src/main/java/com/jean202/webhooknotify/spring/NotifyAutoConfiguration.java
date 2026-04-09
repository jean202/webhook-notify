package com.jean202.webhooknotify.spring;

import com.jean202.webhooknotify.core.WebhookNotifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy
@EnableConfigurationProperties(NotifyProperties.class)
@ConditionalOnProperty(prefix = "webhook-notify.slack", name = "webhook-url")
public class NotifyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public WebhookNotifier webhookNotifier(NotifyProperties properties) {
        return WebhookNotifier.builder()
                .slack(properties.getSlack().getWebhookUrl())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public NotifyAspect notifyAspect(WebhookNotifier notifier) {
        return new NotifyAspect(notifier);
    }
}

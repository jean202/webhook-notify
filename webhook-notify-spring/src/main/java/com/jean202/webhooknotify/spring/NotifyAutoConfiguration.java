package com.jean202.webhooknotify.spring;

import com.jean202.webhooknotify.core.WebhookNotifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy
@EnableConfigurationProperties(NotifyProperties.class)
public class NotifyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @Conditional(OnWebhookChannelConfiguredCondition.class)
    public WebhookNotifier webhookNotifier(NotifyProperties properties) {
        WebhookNotifier.Builder builder = WebhookNotifier.builder();

        String slackUrl = properties.getSlack().getWebhookUrl();
        if (slackUrl != null && !slackUrl.isBlank()) {
            builder.slack(slackUrl);
        }

        String discordUrl = properties.getDiscord().getWebhookUrl();
        if (discordUrl != null && !discordUrl.isBlank()) {
            builder.discord(discordUrl);
        }

        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(WebhookNotifier.class)
    public NotifyAspect notifyAspect(WebhookNotifier notifier) {
        return new NotifyAspect(notifier);
    }
}

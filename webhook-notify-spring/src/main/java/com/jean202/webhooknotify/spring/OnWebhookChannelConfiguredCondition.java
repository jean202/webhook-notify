package com.jean202.webhooknotify.spring;

import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

final class OnWebhookChannelConfiguredCondition extends SpringBootCondition {
    private static final String[] WEBHOOK_URL_PROPERTIES = {
        "webhook-notify.slack.webhook-url",
        "webhook-notify.discord.webhook-url"
    };

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        for (String propertyName : WEBHOOK_URL_PROPERTIES) {
            String propertyValue = context.getEnvironment().getProperty(propertyName);
            if (StringUtils.hasText(propertyValue)) {
                return ConditionOutcome.match(
                    ConditionMessage.forCondition("Webhook notifier")
                        .because("property '%s' is configured".formatted(propertyName))
                );
            }
        }

        return ConditionOutcome.noMatch(
            ConditionMessage.forCondition("Webhook notifier")
                .because("no webhook-notify webhook URL is configured")
        );
    }
}

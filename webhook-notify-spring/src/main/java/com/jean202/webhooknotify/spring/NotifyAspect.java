package com.jean202.webhooknotify.spring;

import com.jean202.webhooknotify.core.NotifyCondition;
import com.jean202.webhooknotify.core.NotifyMessage;
import com.jean202.webhooknotify.core.WebhookNotifier;
import java.util.Map;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public class NotifyAspect {
    private final WebhookNotifier notifier;

    public NotifyAspect(WebhookNotifier notifier) {
        this.notifier = notifier;
    }

    @Around("@annotation(notify)")
    public Object around(ProceedingJoinPoint joinPoint, Notify notify) throws Throwable {
        Object result = joinPoint.proceed();

        Map<String, Object> variables = Map.of("result", result == null ? "" : result);

        String condition = notify.condition();
        if (!condition.isEmpty()) {
            boolean matched = NotifyCondition.evaluate(condition, variables);
            if (!matched) {
                return result;
            }
        }

        String template = notify.template();
        NotifyMessage message;
        if (template.isEmpty()) {
            message = NotifyMessage.text(String.valueOf(result));
        } else {
            message = NotifyMessage.template(template).var("result", result).build();
        }

        notifier.send(message);
        return result;
    }
}

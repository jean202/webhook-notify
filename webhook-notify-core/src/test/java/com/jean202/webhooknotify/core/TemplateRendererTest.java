package com.jean202.webhooknotify.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TemplateRendererTest {
    private final TemplateRenderer renderer = new TemplateRenderer();

    @Test
    void renderReplacesSimpleAndNestedPlaceholders() {
        String rendered = renderer.render(
            "#{result.service} deployed to #{result.environment.name} by #{actor}",
            Map.of(
                "result",
                new DeployResult("billing-api", new Environment("prod")),
                "actor",
                "jean"
            )
        );

        assertEquals("billing-api deployed to prod by jean", rendered);
    }

    @Test
    void renderSupportsMapProperties() {
        String rendered = renderer.render(
            "status=#{payload.status}, count=#{payload.count}",
            Map.of("payload", Map.of("status", "ok", "count", 3))
        );

        assertEquals("status=ok, count=3", rendered);
    }

    @Test
    void renderSupportsZeroArgMethodCalls() {
        String rendered = renderer.render(
            "abs=#{result.changeRate.abs()}",
            Map.of("result", new PriceResult(new BigDecimal("-5.2")))
        );

        assertEquals("abs=5.2", rendered);
    }

    @Test
    void renderRejectsMissingVariable() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> renderer.render("deploy #{missing}", Map.of("name", "api"))
        );

        assertTrue(exception.getMessage().contains("missing"));
    }

    @Test
    void renderRejectsUnclosedPlaceholder() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> renderer.render("deploy #{result.version", Map.of("result", new Version("1.2.3")))
        );

        assertTrue(exception.getMessage().contains("Unclosed"));
    }

    @Test
    void renderRejectsNullResolvedValue() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> renderer.render("#{result.version}", Map.of("result", new Version(null)))
        );

        assertTrue(exception.getMessage().contains("resolved to null"));
    }

    @Test
    void notifyMessageTemplateBuilderRendersBodyAndTitle() {
        NotifyMessage message = NotifyMessage.template("#{name} 서버 #{status}")
            .title("배포 #{version}")
            .var("name", "API-01")
            .var("status", "정상")
            .var("version", "1.2.3")
            .build();

        assertEquals("배포 1.2.3", message.title());
        assertEquals("API-01 서버 정상", message.body());
    }

    private record DeployResult(String service, Environment environment) {
    }

    private record Environment(String name) {
    }

    private record Version(String version) {
    }

    private record PriceResult(BigDecimal changeRate) {
    }
}

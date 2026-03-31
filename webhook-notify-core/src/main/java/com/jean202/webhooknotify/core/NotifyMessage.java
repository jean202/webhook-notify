package com.jean202.webhooknotify.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record NotifyMessage(String title, String body) {
    public NotifyMessage {
        title = Objects.requireNonNull(title, "title");
        body = Objects.requireNonNull(body, "body");
    }

    public static NotifyMessage of(String title, String body) {
        return new NotifyMessage(title, body);
    }

    public static NotifyMessage text(String body) {
        return new NotifyMessage("", body);
    }

    public static TemplateBuilder template(String bodyTemplate) {
        return new TemplateBuilder(bodyTemplate);
    }

    public static final class TemplateBuilder {
        private static final TemplateRenderer TEMPLATE_RENDERER = new TemplateRenderer();

        private final String bodyTemplate;
        private final Map<String, Object> variables = new LinkedHashMap<>();
        private String titleTemplate = "";

        private TemplateBuilder(String bodyTemplate) {
            this.bodyTemplate = Objects.requireNonNull(bodyTemplate, "bodyTemplate");
        }

        public TemplateBuilder title(String titleTemplate) {
            this.titleTemplate = Objects.requireNonNull(titleTemplate, "titleTemplate");
            return this;
        }

        public TemplateBuilder var(String name, Object value) {
            Objects.requireNonNull(name, "name");
            if (name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
            variables.put(name, value);
            return this;
        }

        public NotifyMessage build() {
            String renderedTitle = render(titleTemplate);
            String renderedBody = render(bodyTemplate);
            return new NotifyMessage(renderedTitle, renderedBody);
        }

        private String render(String template) {
            if (template.isEmpty()) {
                return "";
            }
            return TEMPLATE_RENDERER.render(template, variables);
        }
    }
}

package com.jean202.webhooknotify.core;

import java.util.Map;
import java.util.Objects;

public final class TemplateRenderer {
    private static final ValueResolver VALUE_RESOLVER = new ValueResolver();

    public String render(String template, Map<String, ?> variables) {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(variables, "variables");

        StringBuilder rendered = new StringBuilder(template.length() + 16);
        int cursor = 0;

        while (cursor < template.length()) {
            int placeholderStart = template.indexOf("#{", cursor);
            if (placeholderStart < 0) {
                rendered.append(template, cursor, template.length());
                break;
            }

            rendered.append(template, cursor, placeholderStart);

            int placeholderEnd = template.indexOf('}', placeholderStart + 2);
            if (placeholderEnd < 0) {
                throw new IllegalArgumentException("Unclosed placeholder starting at index " + placeholderStart);
            }

            String expression = template.substring(placeholderStart + 2, placeholderEnd).trim();
            if (expression.isEmpty()) {
                throw new IllegalArgumentException("Empty placeholder starting at index " + placeholderStart);
            }

            Object value = VALUE_RESOLVER.resolve(expression, variables);
            if (value == null) {
                throw new IllegalArgumentException("Template variable '" + expression + "' resolved to null");
            }

            rendered.append(value);
            cursor = placeholderEnd + 1;
        }

        return rendered.toString();
    }
}

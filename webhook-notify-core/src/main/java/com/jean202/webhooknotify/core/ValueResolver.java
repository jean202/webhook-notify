package com.jean202.webhooknotify.core;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

final class ValueResolver {
    Object resolve(String expression, Map<String, ?> variables) {
        Objects.requireNonNull(expression, "expression");
        Objects.requireNonNull(variables, "variables");

        String[] segments = expression.split("\\.");
        validateSegments(expression, segments);

        String rootName = segments[0];
        if (rootName.endsWith("()")) {
            throw new IllegalArgumentException("Root variable must not be a method call in '" + expression + "'");
        }
        if (!variables.containsKey(rootName)) {
            throw new IllegalArgumentException("Missing variable '" + rootName + "'");
        }

        Object value = variables.get(rootName);
        for (int index = 1; index < segments.length; index++) {
            value = resolveSegment(value, segments[index], expression);
        }
        return value;
    }

    private Object resolveSegment(Object target, String segment, String expression) {
        if (segment.endsWith("()")) {
            return invokeMethod(target, segment.substring(0, segment.length() - 2), expression);
        }
        return resolveProperty(target, segment, expression);
    }

    private Object invokeMethod(Object target, String methodName, String expression) {
        if (target == null) {
            throw new IllegalArgumentException("Expression '" + expression + "' resolved through null");
        }
        if (methodName.isBlank()) {
            throw new IllegalArgumentException("Invalid method call in '" + expression + "'");
        }

        Method method;
        try {
            method = target.getClass().getMethod(methodName);
        } catch (NoSuchMethodException exception) {
            throw new IllegalArgumentException(
                "Method '" + methodName + "()' not found on " + target.getClass().getName() + " for '" + expression + "'",
                exception
            );
        }

        if (method.getParameterCount() != 0 || method.getReturnType() == Void.TYPE) {
            throw new IllegalArgumentException(
                "Method '" + methodName + "()' is not a zero-arg value method on " + target.getClass().getName()
            );
        }

        try {
            return method.invoke(target);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalArgumentException(
                "Failed to invoke method '" + methodName + "()' on " + target.getClass().getName(),
                exception
            );
        }
    }

    private Object resolveProperty(Object target, String propertyName, String expression) {
        if (target == null) {
            throw new IllegalArgumentException("Expression '" + expression + "' resolved through null");
        }

        if (target instanceof Map<?, ?> map) {
            if (!map.containsKey(propertyName)) {
                throw new IllegalArgumentException("Property '" + propertyName + "' not found in '" + expression + "'");
            }
            return map.get(propertyName);
        }

        Method accessor = findAccessor(target.getClass(), propertyName);
        if (accessor != null) {
            try {
                return accessor.invoke(target);
            } catch (IllegalAccessException | InvocationTargetException exception) {
                throw new IllegalArgumentException(
                    "Failed to read property '" + propertyName + "' from " + target.getClass().getName(),
                    exception
                );
            }
        }

        Field field = findField(target.getClass(), propertyName);
        if (field != null) {
            try {
                return field.get(target);
            } catch (IllegalAccessException exception) {
                throw new IllegalArgumentException(
                    "Failed to read field '" + propertyName + "' from " + target.getClass().getName(),
                    exception
                );
            }
        }

        throw new IllegalArgumentException(
            "Property '" + propertyName + "' not found on " + target.getClass().getName() + " for '" + expression + "'"
        );
    }

    private Method findAccessor(Class<?> type, String propertyName) {
        for (String methodName : new String[]{propertyName, getterName("get", propertyName), getterName("is", propertyName)}) {
            try {
                Method method = type.getMethod(methodName);
                if (method.getParameterCount() == 0 && method.getReturnType() != Void.TYPE) {
                    return method;
                }
            } catch (NoSuchMethodException ignored) {
                // Try the next accessor pattern.
            }
        }
        return null;
    }

    private Field findField(Class<?> type, String propertyName) {
        try {
            return type.getField(propertyName);
        } catch (NoSuchFieldException ignored) {
            return null;
        }
    }

    private void validateSegments(String expression, String[] segments) {
        for (String segment : segments) {
            if (segment.isBlank()) {
                throw new IllegalArgumentException("Invalid path '" + expression + "'");
            }
        }
    }

    private String getterName(String prefix, String propertyName) {
        return prefix + propertyName.substring(0, 1).toUpperCase(Locale.ROOT) + propertyName.substring(1);
    }
}

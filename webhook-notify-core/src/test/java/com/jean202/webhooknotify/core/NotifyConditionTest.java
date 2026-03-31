package com.jean202.webhooknotify.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NotifyConditionTest {
    @Test
    void matchesNumericComparison() {
        boolean matched = NotifyCondition.evaluate(
            "result.changeRate > 5.0",
            Map.of("result", new AnalysisResult(new BigDecimal("5.1"), false, false, 0, false, "SUCCESS", null))
        );

        assertTrue(matched);
    }

    @Test
    void matchesBooleanPropertyCondition() {
        NotifyCondition condition = NotifyCondition.parse("result.tierJustAchieved");

        assertTrue(condition.matches(Map.of("result", new AnalysisResult(BigDecimal.ZERO, false, true, 0, false, "SUCCESS", null))));
        assertFalse(condition.matches(Map.of("result", new AnalysisResult(BigDecimal.ZERO, false, false, 0, false, "SUCCESS", null))));
    }

    @Test
    void matchesLogicalOperatorsAndParentheses() {
        NotifyCondition condition = NotifyCondition.parse("(!result.achieved && result.daysRemaining <= 3) || result.forceNotify");

        assertTrue(condition.matches(Map.of("result", new AnalysisResult(BigDecimal.ZERO, false, false, 2, false, "SUCCESS", null))));
        assertTrue(condition.matches(Map.of("result", new AnalysisResult(BigDecimal.ZERO, true, false, 5, true, "SUCCESS", null))));
        assertFalse(condition.matches(Map.of("result", new AnalysisResult(BigDecimal.ZERO, true, false, 5, false, "SUCCESS", null))));
    }

    @Test
    void matchesMethodInvocationAndNegativeLiteral() {
        boolean matched = NotifyCondition.evaluate(
            "result.changeRate.abs() > -5.0",
            Map.of("result", new AnalysisResult(new BigDecimal("-6.2"), false, false, 0, false, "SUCCESS", null))
        );

        assertTrue(matched);
    }

    @Test
    void matchesStringAndNullEquality() {
        NotifyCondition condition = NotifyCondition.parse("result.status == \"SUCCESS\" && result.errorMessage == null");

        assertTrue(condition.matches(Map.of("result", new AnalysisResult(BigDecimal.ZERO, false, false, 0, false, "SUCCESS", null))));
        assertFalse(condition.matches(Map.of("result", new AnalysisResult(BigDecimal.ZERO, false, false, 0, false, "FAILED", "boom"))));
    }

    @Test
    void rejectsMissingVariable() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> NotifyCondition.evaluate("result.changeRate > 5.0", Map.of())
        );

        assertTrue(exception.getMessage().contains("Missing variable"));
    }

    @Test
    void rejectsNonBooleanTopLevelResult() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> NotifyCondition.evaluate("result.changeRate", Map.of(
                "result",
                new AnalysisResult(BigDecimal.ONE, false, false, 0, false, "SUCCESS", null)
            ))
        );

        assertTrue(exception.getMessage().contains("did not evaluate to boolean"));
    }

    @Test
    void rejectsInvalidSyntax() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> NotifyCondition.parse("result.changeRate >")
        );

        assertTrue(exception.getMessage().contains("Unexpected token"));
    }

    private record AnalysisResult(
        BigDecimal changeRate,
        boolean achieved,
        boolean tierJustAchieved,
        int daysRemaining,
        boolean forceNotify,
        String status,
        String errorMessage
    ) {
    }
}

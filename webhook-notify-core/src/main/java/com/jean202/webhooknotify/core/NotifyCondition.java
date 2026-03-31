package com.jean202.webhooknotify.core;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

public final class NotifyCondition {
    private static final ValueResolver VALUE_RESOLVER = new ValueResolver();

    private final String expression;
    private final Node root;

    private NotifyCondition(String expression, Node root) {
        this.expression = expression;
        this.root = root;
    }

    public static NotifyCondition parse(String expression) {
        Objects.requireNonNull(expression, "expression");
        if (expression.isBlank()) {
            throw new IllegalArgumentException("expression must not be blank");
        }

        Parser parser = new Parser(expression);
        Node root = parser.parseExpression();
        parser.expect(TokenType.EOF);
        return new NotifyCondition(expression, root);
    }

    public static boolean evaluate(String expression, Map<String, ?> variables) {
        return parse(expression).matches(variables);
    }

    public boolean matches(Map<String, ?> variables) {
        Objects.requireNonNull(variables, "variables");
        Object value = root.evaluate(variables);
        if (!(value instanceof Boolean bool)) {
            throw new IllegalArgumentException(
                "Condition '" + expression + "' did not evaluate to boolean: " + describeType(value)
            );
        }
        return bool;
    }

    public String expression() {
        return expression;
    }

    private static boolean equalsValue(Object left, Object right) {
        if (left == null || right == null) {
            return left == right;
        }
        if (left instanceof Number && right instanceof Number) {
            return toBigDecimal(left).compareTo(toBigDecimal(right)) == 0;
        }
        return Objects.equals(left, right);
    }

    private static int compareValues(Object left, Object right, String operator) {
        if (left == null || right == null) {
            throw new IllegalArgumentException("Cannot apply '" + operator + "' to null values");
        }
        if (left instanceof Number && right instanceof Number) {
            return toBigDecimal(left).compareTo(toBigDecimal(right));
        }
        if (left instanceof Comparable<?> comparable && left.getClass().isInstance(right)) {
            @SuppressWarnings("unchecked")
            Comparable<Object> typedComparable = (Comparable<Object>) comparable;
            return typedComparable.compareTo(right);
        }
        throw new IllegalArgumentException(
            "Cannot compare values of type " + left.getClass().getName() + " and " + right.getClass().getName()
        );
    }

    private static boolean requireBoolean(Object value, String operator) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        throw new IllegalArgumentException("Operator '" + operator + "' requires boolean operands");
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("Expected numeric value but got " + describeType(value));
        }
        return new BigDecimal(number.toString());
    }

    private static String describeType(Object value) {
        return value == null ? "null" : value.getClass().getName();
    }

    private interface Node {
        Object evaluate(Map<String, ?> variables);
    }

    private record LiteralNode(Object value) implements Node {
        @Override
        public Object evaluate(Map<String, ?> variables) {
            return value;
        }
    }

    private record PathNode(String path) implements Node {
        @Override
        public Object evaluate(Map<String, ?> variables) {
            return VALUE_RESOLVER.resolve(path, variables);
        }
    }

    private record UnaryNode(TokenType operator, Node operand) implements Node {
        @Override
        public Object evaluate(Map<String, ?> variables) {
            Object value = operand.evaluate(variables);
            return switch (operator) {
                case NOT -> !requireBoolean(value, "!");
                case MINUS -> toBigDecimal(value).negate();
                default -> throw new IllegalStateException("Unsupported unary operator: " + operator);
            };
        }
    }

    private record BinaryNode(Node left, TokenType operator, Node right) implements Node {
        @Override
        public Object evaluate(Map<String, ?> variables) {
            return switch (operator) {
                case OR -> {
                    boolean leftValue = requireBoolean(left.evaluate(variables), "||");
                    yield leftValue || requireBoolean(right.evaluate(variables), "||");
                }
                case AND -> {
                    boolean leftValue = requireBoolean(left.evaluate(variables), "&&");
                    yield leftValue && requireBoolean(right.evaluate(variables), "&&");
                }
                case EQ -> equalsValue(left.evaluate(variables), right.evaluate(variables));
                case NE -> !equalsValue(left.evaluate(variables), right.evaluate(variables));
                case GT -> compareValues(left.evaluate(variables), right.evaluate(variables), ">") > 0;
                case GTE -> compareValues(left.evaluate(variables), right.evaluate(variables), ">=") >= 0;
                case LT -> compareValues(left.evaluate(variables), right.evaluate(variables), "<") < 0;
                case LTE -> compareValues(left.evaluate(variables), right.evaluate(variables), "<=") <= 0;
                default -> throw new IllegalStateException("Unsupported binary operator: " + operator);
            };
        }
    }

    private enum TokenType {
        PATH,
        NUMBER,
        STRING,
        BOOLEAN,
        NULL,
        LPAREN,
        RPAREN,
        NOT,
        MINUS,
        AND,
        OR,
        EQ,
        NE,
        GT,
        GTE,
        LT,
        LTE,
        EOF
    }

    private record Token(TokenType type, String text, int position) {
    }

    private static final class Parser {
        private final Tokenizer tokenizer;
        private Token current;

        private Parser(String source) {
            this.tokenizer = new Tokenizer(source);
            this.current = tokenizer.next();
        }

        private Node parseExpression() {
            return parseOr();
        }

        private Node parseOr() {
            Node node = parseAnd();
            while (match(TokenType.OR)) {
                node = new BinaryNode(node, TokenType.OR, parseAnd());
            }
            return node;
        }

        private Node parseAnd() {
            Node node = parseEquality();
            while (match(TokenType.AND)) {
                node = new BinaryNode(node, TokenType.AND, parseEquality());
            }
            return node;
        }

        private Node parseEquality() {
            Node node = parseComparison();
            while (current.type == TokenType.EQ || current.type == TokenType.NE) {
                TokenType operator = current.type;
                advance();
                node = new BinaryNode(node, operator, parseComparison());
            }
            return node;
        }

        private Node parseComparison() {
            Node node = parseUnary();
            while (current.type == TokenType.GT
                || current.type == TokenType.GTE
                || current.type == TokenType.LT
                || current.type == TokenType.LTE) {
                TokenType operator = current.type;
                advance();
                node = new BinaryNode(node, operator, parseUnary());
            }
            return node;
        }

        private Node parseUnary() {
            if (match(TokenType.NOT)) {
                return new UnaryNode(TokenType.NOT, parseUnary());
            }
            if (match(TokenType.MINUS)) {
                return new UnaryNode(TokenType.MINUS, parseUnary());
            }
            return parsePrimary();
        }

        private Node parsePrimary() {
            Token token = current;
            return switch (token.type) {
                case LPAREN -> {
                    advance();
                    Node nested = parseExpression();
                    expect(TokenType.RPAREN);
                    yield nested;
                }
                case PATH -> {
                    advance();
                    yield new PathNode(token.text);
                }
                case NUMBER -> {
                    advance();
                    yield new LiteralNode(new BigDecimal(token.text));
                }
                case STRING -> {
                    advance();
                    yield new LiteralNode(token.text);
                }
                case BOOLEAN -> {
                    advance();
                    yield new LiteralNode(Boolean.parseBoolean(token.text));
                }
                case NULL -> {
                    advance();
                    yield new LiteralNode(null);
                }
                default -> throw error("Unexpected token '" + token.text + "'");
            };
        }

        private boolean match(TokenType expected) {
            if (current.type != expected) {
                return false;
            }
            advance();
            return true;
        }

        private void expect(TokenType expected) {
            if (current.type != expected) {
                throw error("Expected " + expected + " but found '" + current.text + "'");
            }
            advance();
        }

        private void advance() {
            current = tokenizer.next();
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at index " + current.position);
        }
    }

    private static final class Tokenizer {
        private final String source;
        private int index;

        private Tokenizer(String source) {
            this.source = source;
        }

        private Token next() {
            skipWhitespace();
            if (index >= source.length()) {
                return new Token(TokenType.EOF, "", index);
            }

            int start = index;
            char current = source.charAt(index);
            return switch (current) {
                case '(' -> single(TokenType.LPAREN, start);
                case ')' -> single(TokenType.RPAREN, start);
                case '-' -> single(TokenType.MINUS, start);
                case '!' -> match('=', TokenType.NE, TokenType.NOT, start);
                case '=' -> require('=', TokenType.EQ, start);
                case '>' -> match('=', TokenType.GTE, TokenType.GT, start);
                case '<' -> match('=', TokenType.LTE, TokenType.LT, start);
                case '&' -> require('&', TokenType.AND, start);
                case '|' -> require('|', TokenType.OR, start);
                case '\'', '"' -> stringToken(current, start);
                default -> {
                    if (Character.isDigit(current)) {
                        yield numberToken(start);
                    }
                    if (isIdentifierStart(current)) {
                        yield identifierToken(start);
                    }
                    throw new IllegalArgumentException("Unexpected character '" + current + "' at index " + start);
                }
            };
        }

        private Token single(TokenType type, int start) {
            index++;
            return new Token(type, source.substring(start, index), start);
        }

        private Token match(char expected, TokenType whenMatched, TokenType whenSingle, int start) {
            index++;
            if (index < source.length() && source.charAt(index) == expected) {
                index++;
                return new Token(whenMatched, source.substring(start, index), start);
            }
            return new Token(whenSingle, source.substring(start, index), start);
        }

        private Token require(char expected, TokenType type, int start) {
            index++;
            if (index >= source.length() || source.charAt(index) != expected) {
                throw new IllegalArgumentException("Expected '" + expected + "' after '" + source.charAt(start) + "' at index " + start);
            }
            index++;
            return new Token(type, source.substring(start, index), start);
        }

        private Token stringToken(char quote, int start) {
            index++;
            StringBuilder value = new StringBuilder();

            while (index < source.length()) {
                char current = source.charAt(index++);
                if (current == quote) {
                    return new Token(TokenType.STRING, value.toString(), start);
                }
                if (current == '\\') {
                    if (index >= source.length()) {
                        throw new IllegalArgumentException("Unterminated escape sequence at index " + start);
                    }
                    value.append(switch (source.charAt(index++)) {
                        case '\\' -> '\\';
                        case '\'' -> '\'';
                        case '"' -> '"';
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        default -> throw new IllegalArgumentException("Unsupported escape sequence at index " + (index - 2));
                    });
                    continue;
                }
                value.append(current);
            }

            throw new IllegalArgumentException("Unterminated string literal at index " + start);
        }

        private Token numberToken(int start) {
            while (index < source.length() && Character.isDigit(source.charAt(index))) {
                index++;
            }
            if (index < source.length() && source.charAt(index) == '.') {
                index++;
                while (index < source.length() && Character.isDigit(source.charAt(index))) {
                    index++;
                }
            }
            return new Token(TokenType.NUMBER, source.substring(start, index), start);
        }

        private Token identifierToken(int start) {
            readSegment();
            while (index < source.length() && source.charAt(index) == '.') {
                index++;
                if (index >= source.length() || !isIdentifierStart(source.charAt(index))) {
                    throw new IllegalArgumentException("Invalid path segment at index " + index);
                }
                readSegment();
            }

            String text = source.substring(start, index);
            if ("true".equals(text) || "false".equals(text)) {
                return new Token(TokenType.BOOLEAN, text, start);
            }
            if ("null".equals(text)) {
                return new Token(TokenType.NULL, text, start);
            }
            return new Token(TokenType.PATH, text, start);
        }

        private void readSegment() {
            index++;
            while (index < source.length() && isIdentifierPart(source.charAt(index))) {
                index++;
            }
            if (index + 1 < source.length() && source.charAt(index) == '(' && source.charAt(index + 1) == ')') {
                index += 2;
            }
        }

        private void skipWhitespace() {
            while (index < source.length() && Character.isWhitespace(source.charAt(index))) {
                index++;
            }
        }

        private boolean isIdentifierStart(char value) {
            return Character.isLetter(value) || value == '_';
        }

        private boolean isIdentifierPart(char value) {
            return Character.isLetterOrDigit(value) || value == '_';
        }
    }
}

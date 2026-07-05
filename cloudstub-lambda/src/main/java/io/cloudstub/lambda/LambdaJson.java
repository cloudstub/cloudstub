package io.cloudstub.lambda;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON parser producing a JDK object tree ({@link Map}, {@link List}, {@link String},
 * {@link Boolean}, {@link Long}/{@link Double}, or {@code null}). Lambda request bodies carry
 * nested structures ({@code Code}, {@code Environment}, {@code Tags}) and numeric fields ({@code
 * Timeout}, {@code MemorySize}) whose types must be preserved on the way back out, which the scalar
 * {@link io.cloudstub.core.spi.StubRequest#jsonField} cannot do. Dependency-free (JDK only): the
 * SPI exposes no JSON type and core's jackson is shaded out of reach of modules.
 *
 * <p>Objects preserve insertion order. The parser is lenient at the entry point: a malformed or
 * empty body yields an empty object from {@link #parseObject}, never an exception out of a handler.
 */
final class LambdaJson {

    private final String s;
    private int i;

    private LambdaJson(String s) {
        this.s = s;
    }

    /**
     * Parses {@code body} as a JSON object. Returns an empty, mutable map if the body is empty, not
     * valid JSON, or not a JSON object.
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> parseObject(String body) {
        if (body == null || body.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            LambdaJson p = new LambdaJson(body);
            Object value = p.parseValue();
            p.skipWhitespace();
            if (value instanceof Map && p.i >= p.s.length()) {
                return (Map<String, Object>) value;
            }
        } catch (RuntimeException e) {
            // Fall through to the empty map — a bad body must not throw out of a handler.
        }
        return new LinkedHashMap<>();
    }

    private Object parseValue() {
        skipWhitespace();
        char c = peek();
        return switch (c) {
            case '{' -> parseObjectNode();
            case '[' -> parseArray();
            case '"' -> parseString();
            case 't', 'f' -> parseBoolean();
            case 'n' -> parseNull();
            default -> parseNumber();
        };
    }

    private Map<String, Object> parseObjectNode() {
        Map<String, Object> object = new LinkedHashMap<>();
        expect('{');
        skipWhitespace();
        if (peek() == '}') {
            i++;
            return object;
        }
        while (true) {
            skipWhitespace();
            String key = parseString();
            skipWhitespace();
            expect(':');
            object.put(key, parseValue());
            skipWhitespace();
            char c = next();
            if (c == '}') {
                return object;
            }
            if (c != ',') {
                throw new IllegalStateException("expected ',' or '}'");
            }
        }
    }

    private List<Object> parseArray() {
        List<Object> array = new ArrayList<>();
        expect('[');
        skipWhitespace();
        if (peek() == ']') {
            i++;
            return array;
        }
        while (true) {
            array.add(parseValue());
            skipWhitespace();
            char c = next();
            if (c == ']') {
                return array;
            }
            if (c != ',') {
                throw new IllegalStateException("expected ',' or ']'");
            }
        }
    }

    private String parseString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = next();
            if (c == '"') {
                return sb.toString();
            }
            if (c == '\\') {
                char esc = next();
                switch (esc) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        String hex = s.substring(i, i + 4);
                        i += 4;
                        sb.append((char) Integer.parseInt(hex, 16));
                    }
                    default -> throw new IllegalStateException("bad escape: \\" + esc);
                }
            } else {
                sb.append(c);
            }
        }
    }

    private Boolean parseBoolean() {
        if (s.startsWith("true", i)) {
            i += 4;
            return Boolean.TRUE;
        }
        if (s.startsWith("false", i)) {
            i += 5;
            return Boolean.FALSE;
        }
        throw new IllegalStateException("expected boolean");
    }

    private Object parseNull() {
        if (s.startsWith("null", i)) {
            i += 4;
            return null;
        }
        throw new IllegalStateException("expected null");
    }

    private Object parseNumber() {
        int start = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
            i++;
        }
        String token = s.substring(start, i);
        if (token.isEmpty()) {
            throw new IllegalStateException("unexpected character: " + peek());
        }
        if (token.indexOf('.') < 0 && token.indexOf('e') < 0 && token.indexOf('E') < 0) {
            try {
                return Long.parseLong(token);
            } catch (NumberFormatException e) {
                // Fall through to double for values outside the long range.
            }
        }
        return Double.parseDouble(token);
    }

    private void skipWhitespace() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
    }

    private char peek() {
        if (i >= s.length()) {
            throw new IllegalStateException("unexpected end of input");
        }
        return s.charAt(i);
    }

    private char next() {
        return s.charAt(i++);
    }

    private void expect(char c) {
        if (next() != c) {
            throw new IllegalStateException("expected '" + c + "'");
        }
    }
}

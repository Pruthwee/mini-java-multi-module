package com.minijava.ui.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A tiny, dependency-free JSON parser and string serializer.
 *
 * <p>This keeps MiniJava-UI self-contained: the UI-transformation demo builds and
 * runs with nothing but the JDK. It supports the JSON subset needed to describe a
 * UI config (objects, arrays, strings, numbers, booleans, null) and is deliberately
 * small rather than exhaustive.</p>
 *
 * <p>Parsed values map to: {@link Map} (object, insertion-ordered),
 * {@link List} (array), {@link String}, {@link Double} (number),
 * {@link Boolean}, or {@code null}.</p>
 */
public final class MiniJson {

    private final String src;
    private int pos;

    private MiniJson(String src) {
        this.src = src;
    }

    /** Parse a JSON document into Java objects. */
    public static Object parse(String json) {
        if (json == null) {
            throw new JsonException("input is null");
        }
        MiniJson p = new MiniJson(json);
        p.skipWhitespace();
        Object value = p.readValue();
        p.skipWhitespace();
        if (p.pos != p.src.length()) {
            throw new JsonException("trailing characters at position " + p.pos);
        }
        return value;
    }

    /** Convenience: parse and cast to a JSON object. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String json) {
        Object value = parse(json);
        if (!(value instanceof Map)) {
            throw new JsonException("expected a JSON object at the top level");
        }
        return (Map<String, Object>) value;
    }

    private Object readValue() {
        char c = peek();
        switch (c) {
            case '{': return readObject();
            case '[': return readArray();
            case '"': return readString();
            case 't': case 'f': return readBoolean();
            case 'n': return readNull();
            default:
                if (c == '-' || (c >= '0' && c <= '9')) {
                    return readNumber();
                }
                throw new JsonException("unexpected character '" + c + "' at position " + pos);
        }
    }

    private Map<String, Object> readObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        expect('{');
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipWhitespace();
            String key = readString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            map.put(key, readValue());
            skipWhitespace();
            char c = next();
            if (c == '}') {
                return map;
            }
            if (c != ',') {
                throw new JsonException("expected ',' or '}' at position " + (pos - 1));
            }
        }
    }

    private List<Object> readArray() {
        List<Object> list = new ArrayList<>();
        expect('[');
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (true) {
            skipWhitespace();
            list.add(readValue());
            skipWhitespace();
            char c = next();
            if (c == ']') {
                return list;
            }
            if (c != ',') {
                throw new JsonException("expected ',' or ']' at position " + (pos - 1));
            }
        }
    }

    private String readString() {
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
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        String hex = src.substring(pos, pos + 4);
                        pos += 4;
                        sb.append((char) Integer.parseInt(hex, 16));
                        break;
                    default:
                        throw new JsonException("invalid escape '\\" + esc + "'");
                }
            } else {
                sb.append(c);
            }
        }
    }

    private Double readNumber() {
        int start = pos;
        if (peek() == '-') {
            pos++;
        }
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if ((c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                pos++;
            } else {
                break;
            }
        }
        return Double.parseDouble(src.substring(start, pos));
    }

    private Boolean readBoolean() {
        if (src.startsWith("true", pos)) {
            pos += 4;
            return Boolean.TRUE;
        }
        if (src.startsWith("false", pos)) {
            pos += 5;
            return Boolean.FALSE;
        }
        throw new JsonException("invalid literal at position " + pos);
    }

    private Object readNull() {
        if (src.startsWith("null", pos)) {
            pos += 4;
            return null;
        }
        throw new JsonException("invalid literal at position " + pos);
    }

    private void skipWhitespace() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
            pos++;
        }
    }

    private char peek() {
        if (pos >= src.length()) {
            throw new JsonException("unexpected end of input");
        }
        return src.charAt(pos);
    }

    private char next() {
        if (pos >= src.length()) {
            throw new JsonException("unexpected end of input");
        }
        return src.charAt(pos++);
    }

    private void expect(char c) {
        char actual = next();
        if (actual != c) {
            throw new JsonException("expected '" + c + "' but found '" + actual + "' at position " + (pos - 1));
        }
    }

    /** Escape a raw string for safe inclusion inside a JSON string literal. */
    public static String escape(String raw) {
        StringBuilder sb = new StringBuilder(raw.length() + 8);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    /** Thrown when JSON input cannot be parsed. */
    public static final class JsonException extends RuntimeException {
        public JsonException(String message) {
            super(message);
        }
    }
}

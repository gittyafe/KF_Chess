package org.example.view;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A small, dependency-free recursive-descent JSON parser.
 *
 * <p>This replaces the previous approach in {@code ConfigParser}, which
 * stripped all quotes and spaces from the whole file and then located values
 * with {@code indexOf("key")} / {@code indexOf(",")} / {@code indexOf("}")}.
 * That approach silently breaks on: a key name that is a substring of another
 * key, a string value containing a comma or brace, nested objects/arrays,
 * or numbers using scientific notation - none of which raise an error, they
 * just produce a wrong value or a wrong default. A real (if minimal) parser
 * removes that whole class of bug and fails loudly on malformed input
 * instead of guessing.</p>
 *
 * <p>Only the subset needed for animation config files is supported: a JSON
 * object at the top level with string/number/boolean/null values. Nested
 * objects and arrays are parsed (so parsing doesn't derail) but returned as
 * opaque {@link Map} / {@link java.util.List} instances rather than flattened.</p>
 */
final class MiniJson {
    private final String src;
    private int pos;

    private MiniJson(String src) {
        this.src = src;
    }

    static Map<String, Object> parseObject(String json) {
        MiniJson parser = new MiniJson(json);
        parser.skipWhitespace();
        Object value = parser.parseValue();
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("Expected a JSON object at the top level");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) value;
        return result;
    }

    private Object parseValue() {
        skipWhitespace();
        if (pos >= src.length()) {
            throw new IllegalArgumentException("Unexpected end of JSON input");
        }
        char c = src.charAt(pos);
        return switch (c) {
            case '{' -> parseObjectValue();
            case '[' -> parseArrayValue();
            case '"' -> parseString();
            case 't', 'f' -> parseBoolean();
            case 'n' -> parseNull();
            default -> parseNumber();
        };
    }

    private Map<String, Object> parseObjectValue() {
        Map<String, Object> map = new LinkedHashMap<>();
        expect('{');
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipWhitespace();
            String key = parseString();
            skipWhitespace();
            expect(':');
            Object value = parseValue();
            map.put(key, value);
            skipWhitespace();
            char next = peek();
            if (next == ',') {
                pos++;
            } else if (next == '}') {
                pos++;
                break;
            } else {
                throw new IllegalArgumentException("Expected ',' or '}' at position " + pos);
            }
        }
        return map;
    }

    private java.util.List<Object> parseArrayValue() {
        java.util.List<Object> list = new java.util.ArrayList<>();
        expect('[');
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (true) {
            list.add(parseValue());
            skipWhitespace();
            char next = peek();
            if (next == ',') {
                pos++;
            } else if (next == ']') {
                pos++;
                break;
            } else {
                throw new IllegalArgumentException("Expected ',' or ']' at position " + pos);
            }
        }
        return list;
    }

    private String parseString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= src.length()) {
                throw new IllegalArgumentException("Unterminated string starting near position " + pos);
            }
            char c = src.charAt(pos++);
            if (c == '"') break;
            if (c == '\\') {
                char esc = src.charAt(pos++);
                sb.append(switch (esc) {
                    case '"' -> '"';
                    case '\\' -> '\\';
                    case '/' -> '/';
                    case 'n' -> '\n';
                    case 't' -> '\t';
                    case 'r' -> '\r';
                    case 'b' -> '\b';
                    case 'f' -> '\f';
                    case 'u' -> parseUnicodeEscape();
                    default -> throw new IllegalArgumentException("Invalid escape '\\" + esc + "'");
                });
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private char parseUnicodeEscape() {
        String hex = src.substring(pos, pos + 4);
        pos += 4;
        return (char) Integer.parseInt(hex, 16);
    }

    private Double parseNumber() {
        int start = pos;
        while (pos < src.length() && "-+0123456789.eE".indexOf(src.charAt(pos)) >= 0) {
            pos++;
        }
        String num = src.substring(start, pos);
        if (num.isEmpty()) {
            throw new IllegalArgumentException("Expected a number at position " + start);
        }
        return Double.parseDouble(num);
    }

    private Boolean parseBoolean() {
        if (src.startsWith("true", pos)) { pos += 4; return Boolean.TRUE; }
        if (src.startsWith("false", pos)) { pos += 5; return Boolean.FALSE; }
        throw new IllegalArgumentException("Invalid literal at position " + pos);
    }

    private Object parseNull() {
        if (src.startsWith("null", pos)) { pos += 4; return null; }
        throw new IllegalArgumentException("Invalid literal at position " + pos);
    }

    private void expect(char c) {
        if (pos >= src.length() || src.charAt(pos) != c) {
            throw new IllegalArgumentException("Expected '" + c + "' at position " + pos);
        }
        pos++;
    }

    private char peek() {
        if (pos >= src.length()) {
            throw new IllegalArgumentException("Unexpected end of JSON input");
        }
        return src.charAt(pos);
    }

    private void skipWhitespace() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
            pos++;
        }
    }
}

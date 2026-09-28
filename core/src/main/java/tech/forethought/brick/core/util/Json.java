package tech.forethought.brick.core.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON codec covering exactly the shapes of the data protocol:
 * objects, arrays, strings, numbers, booleans, null. Not a general-purpose
 * JSON library. Static and thread-safe.
 */
public final class Json {

    private Json() {
    }

    /** Serializes maps, lists, strings, numbers, booleans, and null. */
    public static String write(Object value) {
        var out = new StringBuilder();
        writeValue(value, out);
        return out.toString();
    }

    /** Parses JSON text into maps, lists, strings, Long/Double, booleans, null. */
    public static Object read(String text) {
        var parser = new Parser(text);
        var value = parser.readValue();
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw parser.error("trailing content");
        }
        return value;
    }

    private static void writeValue(Object value, StringBuilder out) {
        switch (value) {
            case null -> out.append("null");
            case String s -> writeString(s, out);
            case Number n -> out.append(n);
            case Boolean b -> out.append(b);
            case Map<?, ?> map -> writeObject(map, out);
            case List<?> list -> writeArray(list, out);
            default -> throw new IllegalArgumentException("unsupported JSON value: " + value.getClass());
        }
    }

    private static void writeObject(Map<?, ?> map, StringBuilder out) {
        out.append('{');
        var first = true;
        for (var entry : map.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            writeString(String.valueOf(entry.getKey()), out);
            out.append(':');
            writeValue(entry.getValue(), out);
        }
        out.append('}');
    }

    private static void writeArray(List<?> list, StringBuilder out) {
        out.append('[');
        var first = true;
        for (var item : list) {
            if (!first) {
                out.append(',');
            }
            first = false;
            writeValue(item, out);
        }
        out.append(']');
    }

    private static void writeString(String s, StringBuilder out) {
        out.append('"');
        for (var i = 0; i < s.length(); i++) {
            var c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    private static final class Parser {

        private final String text;
        private int pos;

        Parser(String text) {
            this.text = text;
        }

        boolean atEnd() {
            return pos >= text.length();
        }

        void skipWhitespace() {
            while (!atEnd() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }

        IllegalArgumentException error(String detail) {
            return new IllegalArgumentException("invalid JSON at position " + pos + ": " + detail);
        }

        Object readValue() {
            skipWhitespace();
            if (atEnd()) {
                throw error("unexpected end of input");
            }
            var c = text.charAt(pos);
            return switch (c) {
                case '{' -> readObject();
                case '[' -> readArray();
                case '"' -> readString();
                case 't' -> readLiteral("true", Boolean.TRUE);
                case 'f' -> readLiteral("false", Boolean.FALSE);
                case 'n' -> readLiteral("null", null);
                default -> {
                    if (c == '-' || Character.isDigit(c)) {
                        yield readNumber();
                    }
                    throw error("unexpected character '" + c + "'");
                }
            };
        }

        private Map<String, Object> readObject() {
            var map = new LinkedHashMap<String, Object>();
            pos++;
            skipWhitespace();
            if (!atEnd() && text.charAt(pos) == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                if (atEnd() || text.charAt(pos) != '"') {
                    throw error("expected object key");
                }
                var key = readString();
                skipWhitespace();
                if (atEnd() || text.charAt(pos) != ':') {
                    throw error("expected ':'");
                }
                pos++;
                map.put(key, readValue());
                skipWhitespace();
                if (atEnd()) {
                    throw error("unterminated object");
                }
                var c = text.charAt(pos++);
                if (c == '}') {
                    return map;
                }
                if (c != ',') {
                    throw error("expected ',' or '}'");
                }
            }
        }

        private List<Object> readArray() {
            var list = new ArrayList<Object>();
            pos++;
            skipWhitespace();
            if (!atEnd() && text.charAt(pos) == ']') {
                pos++;
                return list;
            }
            while (true) {
                list.add(readValue());
                skipWhitespace();
                if (atEnd()) {
                    throw error("unterminated array");
                }
                var c = text.charAt(pos++);
                if (c == ']') {
                    return list;
                }
                if (c != ',') {
                    throw error("expected ',' or ']'");
                }
            }
        }

        private String readString() {
            var out = new StringBuilder();
            pos++;
            while (true) {
                if (atEnd()) {
                    throw error("unterminated string");
                }
                var c = text.charAt(pos++);
                if (c == '"') {
                    return out.toString();
                }
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                if (atEnd()) {
                    throw error("unterminated escape");
                }
                var escape = text.charAt(pos++);
                switch (escape) {
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case '/' -> out.append('/');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> out.append(readUnicodeEscape());
                    default -> throw error("unsupported escape '\\" + escape + "'");
                }
            }
        }

        private char readUnicodeEscape() {
            if (pos + 4 > text.length()) {
                throw error("truncated unicode escape");
            }
            var hex = text.substring(pos, pos + 4);
            pos += 4;
            try {
                return (char) Integer.parseInt(hex, 16);
            } catch (NumberFormatException e) {
                throw error("invalid unicode escape '\\u" + hex + "'");
            }
        }

        private Object readLiteral(String literal, Object value) {
            if (!text.startsWith(literal, pos)) {
                throw error("expected '" + literal + "'");
            }
            pos += literal.length();
            return value;
        }

        private Number readNumber() {
            var start = pos;
            if (!atEnd() && text.charAt(pos) == '-') {
                pos++;
            }
            while (!atEnd() && Character.isDigit(text.charAt(pos))) {
                pos++;
            }
            var floating = false;
            if (!atEnd() && text.charAt(pos) == '.') {
                floating = true;
                pos++;
                while (!atEnd() && Character.isDigit(text.charAt(pos))) {
                    pos++;
                }
            }
            if (!atEnd() && (text.charAt(pos) == 'e' || text.charAt(pos) == 'E')) {
                floating = true;
                pos++;
                if (!atEnd() && (text.charAt(pos) == '+' || text.charAt(pos) == '-')) {
                    pos++;
                }
                while (!atEnd() && Character.isDigit(text.charAt(pos))) {
                    pos++;
                }
            }
            var number = text.substring(start, pos);
            if (number.isEmpty() || "-".equals(number)) {
                throw error("malformed number");
            }
            try {
                // no ternary: mixed wrapper operands would unbox and promote to double
                if (floating) {
                    return Double.valueOf(number);
                }
                return Long.valueOf(number);
            } catch (NumberFormatException e) {
                throw error("malformed number '" + number + "'");
            }
        }
    }
}

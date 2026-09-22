/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.launcher.daemon.management.internal;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A minimal JSON reader and writer, sufficient for the daemon index format.
 *
 * <p>The index is a published cross-version contract, so the code that reads and writes it carries no
 * third party dependency that could pin the library to a particular Gradle version or classpath.
 *
 * <p>The parser produces {@link Map}, {@link List}, {@link String}, {@link Long}, {@link Double},
 * {@link Boolean} and null, and accepts any valid JSON document. Unknown members survive parsing, which
 * is what lets an older reader accept an entry written by a newer daemon.
 */
public final class Json {

    private Json() {
    }

    public static String writeObject(Map<String, ?> members) {
        StringBuilder result = new StringBuilder();
        writeValue(result, members);
        return result.toString();
    }

    private static void writeValue(StringBuilder out, @Nullable Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof Map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                writeString(out, String.valueOf(entry.getKey()));
                out.append(':');
                writeValue(out, entry.getValue());
            }
            out.append('}');
        } else if (value instanceof Iterable) {
            out.append('[');
            boolean first = true;
            for (Object element : (Iterable<?>) value) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                writeValue(out, element);
            }
            out.append(']');
        } else if (value instanceof Boolean || value instanceof Integer || value instanceof Long) {
            out.append(value);
        } else {
            writeString(out, String.valueOf(value));
        }
    }

    private static void writeString(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        out.append('"');
    }

    /**
     * Parses a JSON document that is expected to hold an object at its root.
     *
     * @throws IllegalArgumentException when the text is not a JSON object
     */
    public static Map<String, Object> parseObject(String text) {
        Parser parser = new Parser(text);
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw new IllegalArgumentException("Trailing content after the JSON value at index " + parser.pos);
        }
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("Expected a JSON object but found " + (value == null ? "null" : value.getClass().getSimpleName()));
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) value;
        return result;
    }

    private static final class Parser {
        private final String text;
        private int pos;

        private Parser(String text) {
            this.text = text;
        }

        private boolean atEnd() {
            return pos >= text.length();
        }

        private void skipWhitespace() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }

        private char peek() {
            if (atEnd()) {
                throw new IllegalArgumentException("Unexpected end of JSON input");
            }
            return text.charAt(pos);
        }

        private void expect(char expected) {
            if (peek() != expected) {
                throw new IllegalArgumentException("Expected '" + expected + "' at index " + pos + " but found '" + peek() + "'");
            }
            pos++;
        }

        private @Nullable Object readValue() {
            skipWhitespace();
            char c = peek();
            switch (c) {
                case '{':
                    return readObject();
                case '[':
                    return readArray();
                case '"':
                    return readString();
                case 't':
                    readLiteral("true");
                    return true;
                case 'f':
                    readLiteral("false");
                    return false;
                case 'n':
                    readLiteral("null");
                    return null;
                default:
                    return readNumber();
            }
        }

        private Map<String, Object> readObject() {
            expect('{');
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                return result;
            }
            while (true) {
                skipWhitespace();
                String name = readString();
                skipWhitespace();
                expect(':');
                result.put(name, readValue());
                skipWhitespace();
                char next = peek();
                pos++;
                if (next == '}') {
                    return result;
                }
                if (next != ',') {
                    throw new IllegalArgumentException("Expected ',' or '}' at index " + (pos - 1));
                }
            }
        }

        private List<Object> readArray() {
            expect('[');
            List<Object> result = new ArrayList<Object>();
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return result;
            }
            while (true) {
                result.add(readValue());
                skipWhitespace();
                char next = peek();
                pos++;
                if (next == ']') {
                    return result;
                }
                if (next != ',') {
                    throw new IllegalArgumentException("Expected ',' or ']' at index " + (pos - 1));
                }
            }
        }

        private String readString() {
            expect('"');
            StringBuilder result = new StringBuilder();
            while (true) {
                char c = peek();
                pos++;
                if (c == '"') {
                    return result.toString();
                }
                if (c != '\\') {
                    result.append(c);
                    continue;
                }
                char escape = peek();
                pos++;
                switch (escape) {
                    case '"':
                        result.append('"');
                        break;
                    case '\\':
                        result.append('\\');
                        break;
                    case '/':
                        result.append('/');
                        break;
                    case 'b':
                        result.append('\b');
                        break;
                    case 'f':
                        result.append('\f');
                        break;
                    case 'n':
                        result.append('\n');
                        break;
                    case 'r':
                        result.append('\r');
                        break;
                    case 't':
                        result.append('\t');
                        break;
                    case 'u':
                        if (pos + 4 > text.length()) {
                            throw new IllegalArgumentException("Truncated unicode escape at index " + pos);
                        }
                        result.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                        pos += 4;
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown escape '\\" + escape + "' at index " + (pos - 1));
                }
            }
        }

        private void readLiteral(String literal) {
            if (!text.startsWith(literal, pos)) {
                throw new IllegalArgumentException("Unexpected token at index " + pos);
            }
            pos += literal.length();
        }

        private Object readNumber() {
            int start = pos;
            if (peek() == '-') {
                pos++;
            }
            boolean floating = false;
            while (!atEnd()) {
                char c = text.charAt(pos);
                if (c >= '0' && c <= '9') {
                    pos++;
                } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                    floating = true;
                    pos++;
                } else {
                    break;
                }
            }
            String number = text.substring(start, pos);
            if (number.isEmpty() || "-".equals(number)) {
                throw new IllegalArgumentException("Expected a number at index " + start);
            }
            if (floating) {
                return Double.valueOf(number);
            }
            return Long.valueOf(number);
        }
    }
}

/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.build.docs.dsl.javadoc;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Converts the main description of a javadoc comment into a stream of tokens.
 */
class Lexer {
    private static final Pattern END_TAG_NAME = Pattern.compile("\\s|}");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern HTML_ELEMENT = Pattern.compile("<\\\\?.+?>");
    private static final Pattern HTML_ENCODED_CHAR = Pattern.compile("&#\\d+;");
    private static final Pattern HTML_ENTITY = Pattern.compile("&.+?;");
    private static final Pattern TAG = Pattern.compile("\\{@.+?\\}");
    private static final Map<String, String> ENTITIES = new HashMap<String, String>();

    static {
        ENTITIES.put("amp", "&");
        ENTITIES.put("lt", "<");
        ENTITIES.put("gt", ">");
    }

    enum Token {
        Start, Text, End
    }

    final Scanner scanner;
    Token token;
    String value;
    String inlineTag;

    Lexer(Scanner scanner) {
        this.scanner = scanner;
    }

    public void pushText(String rawCommentText) {
        scanner.pushText(rawCommentText);
    }

    boolean next() {
        if (scanner.isEmpty()) {
            token = null;
            value = null;
            return false;
        }

        scanner.mark();
        while (!scanner.isEmpty()) {
            if (inlineTag == null && scanner.lookingAt(HTML_ELEMENT)) {
                break;
            }
            if (inlineTag == null && scanner.lookingAt(HTML_ENCODED_CHAR)) {
                break;
            }
            if (inlineTag == null && scanner.lookingAt(HTML_ENTITY)) {
                break;
            }
            if (scanner.lookingAt(TAG)) {
                break;
            }
            if (inlineTag != null && scanner.lookingAt('}')) {
                break;
            }
            scanner.next();
        }

        String region = scanner.region();
        if (region.length() > 0) {
            token = Token.Text;
            value = region;
            return true;
        }

        if (inlineTag == null && scanner.lookingAt(HTML_ELEMENT)) {
            scanner.next();
            token = Token.Start;
            if (scanner.lookingAt('/')) {
                token = Token.End;
                scanner.next();
            }
            scanner.mark();
            scanner.find('>');
            value = scanner.region().toLowerCase();
            scanner.next();
            return true;
        }

        if (inlineTag == null && scanner.lookingAt(HTML_ENCODED_CHAR)) {
            scanner.next(2);
            scanner.mark();
            scanner.find(';');
            token = Token.Text;
            value = new String(new char[]{(char) Integer.parseInt(scanner.region())});
            scanner.next();
            return true;
        }

        if (inlineTag == null && scanner.lookingAt(HTML_ENTITY)) {
            scanner.next();
            scanner.mark();
            scanner.find(';');
            token = Token.Text;
            value = ENTITIES.get(scanner.region().toLowerCase());
            scanner.next();
            return true;
        }

        if (scanner.lookingAt(TAG)) {
            scanner.next(2);
            scanner.mark();
            scanner.find(END_TAG_NAME);
            token = Token.Start;
            value = scanner.region();
            inlineTag = value;
            scanner.skip(WHITESPACE);
            return true;
        }

        if (inlineTag != null && scanner.lookingAt('}')) {
            token = Token.End;
            value = inlineTag;
            inlineTag = null;
            scanner.next();
            return true;
        }

        throw new IllegalStateException();
    }
}
